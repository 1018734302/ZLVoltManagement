package com.zlvolt.srm;

import com.zlvolt.common.BizException;
import com.zlvolt.common.CodeGen;
import com.zlvolt.common.IdGen;
import com.zlvolt.common.MapUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SrmService {

    private final JdbcTemplate jdbc;
    private final IdGen idGen;
    private final CodeGen codeGen;

    public SrmService(JdbcTemplate jdbc, IdGen idGen, CodeGen codeGen) {
        this.jdbc = jdbc;
        this.idGen = idGen;
        this.codeGen = codeGen;
    }

    public List<Map<String, Object>> suppliers() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT id, code, name, type, level, contact_name, contact_phone,
                       bank_name, bank_account, status
                FROM srm_supplier ORDER BY id
                """));
    }

    public List<Map<String, Object>> materials() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT id, code, name, category, spec, unit, tax_rate, safety_stock, status
                FROM srm_material ORDER BY id
                """));
    }

    public List<Map<String, Object>> purchaseOrders() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT po.id, po.code, po.project_id, p.name AS project_name,
                       po.supplier_id, s.name AS supplier_name,
                       po.order_date, po.delivery_date, po.total_amount, po.tax_amount,
                       po.status,
                       (SELECT COUNT(*) FROM srm_po_line l WHERE l.po_id = po.id) AS line_count
                FROM srm_purchase_order po
                LEFT JOIN pm_project p ON p.id = po.project_id
                LEFT JOIN srm_supplier s ON s.id = po.supplier_id
                ORDER BY po.id DESC
                """));
    }

    public Map<String, Object> purchaseOrder(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT po.id, po.code, po.project_id, p.name AS project_name,
                       po.supplier_id, s.name AS supplier_name,
                       po.order_date, po.delivery_date, po.total_amount, po.tax_amount,
                       po.status, po.payment_terms
                FROM srm_purchase_order po
                LEFT JOIN pm_project p ON p.id = po.project_id
                LEFT JOIN srm_supplier s ON s.id = po.supplier_id
                WHERE po.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BizException(40401, "采购订单不存在");
        }
        Map<String, Object> po = MapUtils.camel(rows.get(0));
        po.put("lines", MapUtils.camel(jdbc.queryForList("""
                SELECT l.id, l.po_id, l.material_id, m.name AS material_name,
                       l.qty, l.received_qty, l.unit_price, l.tax_rate, l.amount, l.delivery_date
                FROM srm_po_line l
                LEFT JOIN srm_material m ON m.id = l.material_id
                WHERE l.po_id = ?
                ORDER BY l.id
                """, id)));
        return po;
    }

    public Map<String, Object> createPurchaseOrder(Map<String, Object> body) {
        long projectId = MapUtils.toLong(body.get("projectId"), 0L);
        long supplierId = MapUtils.toLong(body.get("supplierId"), 0L);
        if (projectId == 0L || supplierId == 0L) {
            throw new BizException("请选择项目和供应商");
        }
        Object linesObj = body.get("lines");
        if (!(linesObj instanceof List<?> rawLines) || rawLines.isEmpty()) {
            throw new BizException("采购订单至少需要一行物料");
        }
        long id = idGen.next("srm_purchase_order");
        String code = codeGen.next("PO");
        LocalDate deliveryDate = parseDate(MapUtils.text(body, "deliveryDate"), LocalDate.now().plusDays(30));
        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Object raw : rawLines) {
            if (!(raw instanceof Map<?, ?> rawMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> line = (Map<String, Object>) rawMap;
            long materialId = MapUtils.toLong(line.get("materialId"), 0L);
            BigDecimal qty = MapUtils.toDecimal(line.get("qty"), BigDecimal.ZERO);
            BigDecimal price = MapUtils.toDecimal(line.get("unitPrice"), BigDecimal.ZERO);
            if (materialId == 0L || qty.signum() <= 0 || price.signum() <= 0) {
                throw new BizException("采购订单行物料、数量、单价必须大于 0");
            }
            List<Map<String, Object>> materials = jdbc.queryForList(
                    "SELECT name FROM srm_material WHERE id = ? AND status = 'ACTIVE'", materialId);
            if (materials.isEmpty()) {
                throw new BizException("物料不存在或已停用: " + materialId);
            }
            Map<String, Object> material = MapUtils.camel(materials.get(0));
            BigDecimal amount = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
            total = total.add(amount);
            Map<String, Object> lineData = new LinkedHashMap<>();
            lineData.put("materialId", materialId);
            lineData.put("materialName", material.get("name"));
            lineData.put("qty", qty);
            lineData.put("unitPrice", price);
            lineData.put("amount", amount);
            lines.add(lineData);
        }
        if (lines.isEmpty()) {
            throw new BizException("采购订单没有有效明细");
        }
        BigDecimal taxAmount = total.subtract(total.divide(new BigDecimal("1.13"), 2, RoundingMode.HALF_UP));
        jdbc.update("""
                        INSERT INTO srm_purchase_order
                        (id, code, project_id, supplier_id, order_date, delivery_date,
                         total_amount, tax_amount, currency, payment_terms, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'CNY', '30%预付款，65%到货30天，5%质保金', 'DRAFT')
                        """,
                id, code, projectId, supplierId, Date.valueOf(LocalDate.now()),
                Date.valueOf(deliveryDate), total, taxAmount);
        for (Map<String, Object> line : lines) {
            jdbc.update("""
                            INSERT INTO srm_po_line
                            (id, po_id, material_id, qty, received_qty, unit_price, tax_rate, amount, delivery_date)
                            VALUES (?, ?, ?, ?, 0, ?, 0.1300, ?, ?)
                            """,
                    idGen.next("srm_po_line"), id, line.get("materialId"), line.get("qty"),
                    line.get("unitPrice"), line.get("amount"), Date.valueOf(deliveryDate));
        }
        return purchaseOrder(id);
    }

    public Map<String, Object> approve(long id) {
        Map<String, Object> po = purchaseOrder(id);
        if (!"DRAFT".equals(po.get("status")) && !"APPROVING".equals(po.get("status"))) {
            throw new BizException("当前采购订单状态不允许审批: " + po.get("status"));
        }
        jdbc.update("UPDATE srm_purchase_order SET status = 'APPROVED' WHERE id = ?", id);
        return purchaseOrder(id);
    }

    public Map<String, Object> deliver(long id) {
        Map<String, Object> po = purchaseOrder(id);
        if (!"APPROVED".equals(po.get("status")) && !"PARTIAL_RECEIVED".equals(po.get("status"))) {
            throw new BizException("只有已审批订单可以发货");
        }
        long deliveryId = idGen.next("srm_delivery");
        String code = codeGen.next("DL");
        jdbc.update("""
                        INSERT INTO srm_delivery (id, code, po_id, supplier_id, delivery_date, logistics_no, status)
                        VALUES (?, ?, ?, ?, ?, ?, 'SHIPPED')
                        """,
                deliveryId, code, id, po.get("supplierId"), Date.valueOf(LocalDate.now()),
                "SF" + System.currentTimeMillis());
        return MapUtils.map("id", deliveryId, "code", code, "poId", id, "status", "SHIPPED");
    }

    private LocalDate parseDate(String value, LocalDate defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return LocalDate.parse(value);
    }
}
