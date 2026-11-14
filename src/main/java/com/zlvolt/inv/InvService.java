package com.zlvolt.inv;

import com.zlvolt.common.BizException;
import com.zlvolt.common.CodeGen;
import com.zlvolt.common.IdGen;
import com.zlvolt.common.MapUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InvService {

    private final JdbcTemplate jdbc;
    private final IdGen idGen;
    private final CodeGen codeGen;

    public InvService(JdbcTemplate jdbc, IdGen idGen, CodeGen codeGen) {
        this.jdbc = jdbc;
        this.idGen = idGen;
        this.codeGen = codeGen;
    }

    public List<Map<String, Object>> warehouses() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT w.id, w.code, w.name, w.project_id, p.name AS project_name,
                       w.location, w.manager_id, u.real_name AS manager_name, w.status
                FROM inv_warehouse w
                LEFT JOIN pm_project p ON p.id = w.project_id
                LEFT JOIN sys_user u ON u.id = w.manager_id
                ORDER BY w.id
                """));
    }

    public List<Map<String, Object>> stocks() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT s.id, s.warehouse_id, w.name AS warehouse_name,
                       s.material_id, m.code AS material_code, m.name AS material_name, m.unit,
                       s.qty, s.locked_qty, s.avg_cost, s.amount
                FROM inv_stock s
                LEFT JOIN inv_warehouse w ON w.id = s.warehouse_id
                LEFT JOIN srm_material m ON m.id = s.material_id
                ORDER BY s.warehouse_id, s.material_id
                """));
    }

    public List<Map<String, Object>> receipts() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT r.id, r.code, r.po_id, po.code AS po_code,
                       r.warehouse_id, w.name AS warehouse_name,
                       r.project_id, p.name AS project_name,
                       r.receipt_date, r.status, r.total_amount
                FROM inv_receipt r
                LEFT JOIN srm_purchase_order po ON po.id = r.po_id
                LEFT JOIN inv_warehouse w ON w.id = r.warehouse_id
                LEFT JOIN pm_project p ON p.id = r.project_id
                ORDER BY r.id DESC
                """));
    }

    public Map<String, Object> receipt(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.id, r.code, r.po_id, po.code AS po_code,
                       r.warehouse_id, w.name AS warehouse_name,
                       r.project_id, p.name AS project_name,
                       r.receipt_date, r.status, r.total_amount
                FROM inv_receipt r
                LEFT JOIN srm_purchase_order po ON po.id = r.po_id
                LEFT JOIN inv_warehouse w ON w.id = r.warehouse_id
                LEFT JOIN pm_project p ON p.id = r.project_id
                WHERE r.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BizException(40401, "收货单不存在");
        }
        Map<String, Object> receipt = MapUtils.camel(rows.get(0));
        receipt.put("lines", MapUtils.camel(jdbc.queryForList("""
                SELECT rl.id, rl.receipt_id, rl.po_line_id, rl.material_id,
                       m.name AS material_name, rl.qty, rl.qualified_qty,
                       rl.unit_price, rl.amount, rl.batch_no
                FROM inv_receipt_line rl
                LEFT JOIN srm_material m ON m.id = rl.material_id
                WHERE rl.receipt_id = ?
                ORDER BY rl.id
                """, id)));
        return receipt;
    }

    public List<Map<String, Object>> movements() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT mo.id, mo.warehouse_id, w.name AS warehouse_name,
                       mo.material_id, m.name AS material_name,
                       mo.biz_type, mo.biz_id, mo.biz_code, mo.qty_delta,
                       mo.unit_cost, mo.amount, mo.occurred_at
                FROM inv_stock_movement mo
                LEFT JOIN inv_warehouse w ON w.id = mo.warehouse_id
                LEFT JOIN srm_material m ON m.id = mo.material_id
                ORDER BY mo.id DESC
                """));
    }

    public Map<String, Object> receivePo(long poId, Long warehouseId) {
        List<Map<String, Object>> pos = jdbc.queryForList(
                "SELECT * FROM srm_purchase_order WHERE id = ?", poId);
        if (pos.isEmpty()) {
            throw new BizException(40401, "采购订单不存在");
        }
        Map<String, Object> po = MapUtils.camel(pos.get(0));
        String status = MapUtils.str(po, "status");
        if (!"APPROVED".equals(status) && !"PARTIAL_RECEIVED".equals(status)) {
            throw new BizException("只有已审批采购订单可以收货，当前状态: " + status);
        }
        long wid = warehouseId == null ? 2L : warehouseId;
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT l.id, l.material_id, m.name AS material_name,
                       l.qty - l.received_qty AS remaining_qty, l.unit_price
                FROM srm_po_line l
                LEFT JOIN srm_material m ON m.id = l.material_id
                WHERE l.po_id = ? AND l.qty > l.received_qty
                ORDER BY l.id
                """, poId);
        if (lines.isEmpty()) {
            throw new BizException("该采购订单已全部收货");
        }
        long receiptId = idGen.next("inv_receipt");
        String code = codeGen.next("GRN");
        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> receiptLines = new ArrayList<>();
        for (Map<String, Object> line : lines) {
            BigDecimal qty = MapUtils.decimal(MapUtils.camel(line), "remainingQty");
            BigDecimal price = MapUtils.decimal(MapUtils.camel(line), "unitPrice");
            BigDecimal amount = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
            total = total.add(amount);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("poLineId", MapUtils.toLong(line.get("id"), 0L));
            data.put("materialId", MapUtils.toLong(line.get("material_id"), 0L));
            data.put("materialName", line.get("material_name"));
            data.put("qty", qty);
            data.put("unitPrice", price);
            data.put("amount", amount);
            receiptLines.add(data);
        }
        jdbc.update("""
                        INSERT INTO inv_receipt
                        (id, code, po_id, delivery_id, warehouse_id, project_id, receipt_date,
                         inspector_id, status, total_amount, remark)
                        VALUES (?, ?, ?, NULL, ?, ?, ?, 6, 'STORED', ?, '采购收货入库')
                        """,
                receiptId, code, poId, wid, po.get("projectId"), Date.valueOf(LocalDate.now()), total);
        for (Map<String, Object> line : receiptLines) {
            jdbc.update("""
                            INSERT INTO inv_receipt_line
                            (id, receipt_id, po_line_id, material_id, qty, qualified_qty,
                             unit_price, amount, batch_no)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    idGen.next("inv_receipt_line"), receiptId, line.get("poLineId"),
                    line.get("materialId"), line.get("qty"), line.get("qty"),
                    line.get("unitPrice"), line.get("amount"),
                    "B" + System.currentTimeMillis());
            jdbc.update("UPDATE srm_po_line SET received_qty = received_qty + ? WHERE id = ?",
                    line.get("qty"), line.get("poLineId"));
            increaseStock(wid, MapUtils.toLong(line.get("materialId"), 0L),
                    MapUtils.decimal(line, "qty"), MapUtils.decimal(line, "unitPrice"),
                    MapUtils.decimal(line, "amount"));
            insertMovement(wid, MapUtils.toLong(line.get("materialId"), 0L), "RECEIPT",
                    receiptId, code, MapUtils.decimal(line, "qty"),
                    MapUtils.decimal(line, "unitPrice"), MapUtils.decimal(line, "amount"), "采购入库");
        }
        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM srm_po_line WHERE po_id = ? AND received_qty < qty", Integer.class, poId);
        jdbc.update("UPDATE srm_purchase_order SET status = ? WHERE id = ?",
                remaining != null && remaining == 0 ? "RECEIVED" : "PARTIAL_RECEIVED", poId);
        return receipt(receiptId);
    }

    public Map<String, Object> issueMaterials(Map<String, Object> body) {
        long projectId = MapUtils.toLong(body.get("projectId"), 0L);
        long wbsId = MapUtils.toLong(body.get("wbsId"), 1L);
        long warehouseId = MapUtils.toLong(body.get("warehouseId"), 2L);
        Integer wbsValid = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pm_wbs WHERE id = ? AND project_id = ?", Integer.class, wbsId, projectId);
        if (wbsValid == null || wbsValid == 0) {
            Long minWbs = jdbc.queryForObject(
                    "SELECT MIN(id) FROM pm_wbs WHERE project_id = ?", Long.class, projectId);
            if (minWbs != null) {
                wbsId = minWbs;
            }
        }
        Object linesObj = body.get("lines");
        if (!(linesObj instanceof List<?> rawLines) || rawLines.isEmpty()) {
            throw new BizException("出库单至少需要一行物料");
        }
        long issueId = idGen.next("inv_issue");
        String code = codeGen.next("ISSUE");
        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> issueLines = new ArrayList<>();
        for (Object raw : rawLines) {
            if (!(raw instanceof Map<?, ?> rawMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> line = (Map<String, Object>) rawMap;
            long materialId = MapUtils.toLong(line.get("materialId"), 0L);
            BigDecimal qty = MapUtils.toDecimal(line.get("qty"), BigDecimal.ZERO);
            if (materialId == 0L || qty.signum() <= 0) {
                throw new BizException("出库物料和数量必须大于 0");
            }
            List<Map<String, Object>> stocks = jdbc.queryForList(
                    "SELECT * FROM inv_stock WHERE warehouse_id = ? AND material_id = ?", warehouseId, materialId);
            if (stocks.isEmpty()) {
                throw new BizException("仓库无该物料库存: " + materialId);
            }
            Map<String, Object> stock = MapUtils.camel(stocks.get(0));
            BigDecimal available = MapUtils.decimal(stock, "qty").subtract(MapUtils.decimal(stock, "lockedQty"));
            if (available.compareTo(qty) < 0) {
                throw new BizException("库存不足，可用 " + available + "，申请 " + qty);
            }
            BigDecimal unitCost = MapUtils.decimal(stock, "avgCost");
            BigDecimal amount = qty.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
            total = total.add(amount);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("materialId", materialId);
            data.put("qty", qty);
            data.put("unitCost", unitCost);
            data.put("amount", amount);
            issueLines.add(data);
        }
        jdbc.update("""
                        INSERT INTO inv_issue
                        (id, code, project_id, wbs_id, warehouse_id, issue_date, type, status, total_amount, remark)
                        VALUES (?, ?, ?, ?, ?, ?, 'PROJECT', 'CONFIRMED', ?, '项目领料')
                        """,
                issueId, code, projectId, wbsId, warehouseId, Date.valueOf(LocalDate.now()), total);
        for (Map<String, Object> line : issueLines) {
            long materialId = MapUtils.toLong(line.get("materialId"), 0L);
            BigDecimal qty = MapUtils.decimal(line, "qty");
            BigDecimal unitCost = MapUtils.decimal(line, "unitCost");
            BigDecimal amount = MapUtils.decimal(line, "amount");
            jdbc.update("""
                            INSERT INTO inv_issue_line
                            (id, issue_id, material_id, qty, unit_cost, amount, batch_no)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    idGen.next("inv_issue_line"), issueId, materialId, qty, unitCost, amount,
                    "B" + System.currentTimeMillis());
            decreaseStock(warehouseId, materialId, qty, amount);
            insertMovement(warehouseId, materialId, "ISSUE", issueId, code,
                    qty.negate(), unitCost, amount, "项目领料");
        }
        jdbc.update("""
                        INSERT INTO pm_cost_item
                        (id, project_id, wbs_id, cost_type, source_type, source_id, amount, occurred_date, remark)
                        VALUES (?, ?, ?, 'MATERIAL', 'ISSUE', ?, ?, ?, ?)
                        """,
                idGen.next("pm_cost_item"), projectId, wbsId, issueId, total,
                Date.valueOf(LocalDate.now()), "项目领料成本 " + code);
        return issue(issueId);
    }

    public Map<String, Object> issue(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT i.id, i.code, i.project_id, p.name AS project_name, i.wbs_id,
                       i.warehouse_id, w.name AS warehouse_name, i.issue_date,
                       i.type, i.status, i.total_amount
                FROM inv_issue i
                LEFT JOIN pm_project p ON p.id = i.project_id
                LEFT JOIN inv_warehouse w ON w.id = i.warehouse_id
                WHERE i.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BizException(40401, "出库单不存在");
        }
        Map<String, Object> issue = MapUtils.camel(rows.get(0));
        issue.put("lines", MapUtils.camel(jdbc.queryForList("""
                SELECT il.id, il.issue_id, il.material_id, m.name AS material_name,
                       il.qty, il.unit_cost, il.amount, il.batch_no
                FROM inv_issue_line il
                LEFT JOIN srm_material m ON m.id = il.material_id
                WHERE il.issue_id = ?
                ORDER BY il.id
                """, id)));
        return issue;
    }

    private void increaseStock(long warehouseId, long materialId, BigDecimal qty,
                               BigDecimal unitPrice, BigDecimal amount) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM inv_stock WHERE warehouse_id = ? AND material_id = ?", warehouseId, materialId);
        if (rows.isEmpty()) {
            jdbc.update("""
                            INSERT INTO inv_stock
                            (id, warehouse_id, material_id, qty, locked_qty, avg_cost, amount)
                            VALUES (?, ?, ?, ?, 0, ?, ?)
                            """,
                    idGen.next("inv_stock"), warehouseId, materialId, qty, unitPrice, amount);
            return;
        }
        Map<String, Object> stock = MapUtils.camel(rows.get(0));
        BigDecimal oldQty = MapUtils.decimal(stock, "qty");
        BigDecimal oldAmount = MapUtils.decimal(stock, "amount");
        BigDecimal newQty = oldQty.add(qty);
        BigDecimal newAmount = oldAmount.add(amount);
        BigDecimal avg = newQty.signum() == 0 ? BigDecimal.ZERO
                : newAmount.divide(newQty, 4, RoundingMode.HALF_UP);
        jdbc.update("UPDATE inv_stock SET qty = ?, avg_cost = ?, amount = ? WHERE id = ?",
                newQty, avg, newAmount, stock.get("id"));
    }

    private void decreaseStock(long warehouseId, long materialId, BigDecimal qty, BigDecimal amount) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM inv_stock WHERE warehouse_id = ? AND material_id = ?", warehouseId, materialId);
        if (rows.isEmpty()) {
            throw new BizException("库存不存在");
        }
        Map<String, Object> stock = MapUtils.camel(rows.get(0));
        BigDecimal newQty = MapUtils.decimal(stock, "qty").subtract(qty);
        BigDecimal newAmount = MapUtils.decimal(stock, "amount").subtract(amount);
        if (newQty.signum() < 0) {
            throw new BizException("库存不足");
        }
        if (newAmount.signum() < 0) {
            newAmount = BigDecimal.ZERO;
        }
        jdbc.update("UPDATE inv_stock SET qty = ?, amount = ? WHERE id = ?",
                newQty, newAmount, stock.get("id"));
    }

    private void insertMovement(long warehouseId, long materialId, String bizType, long bizId,
                                String bizCode, BigDecimal qtyDelta, BigDecimal unitCost,
                                BigDecimal amount, String remark) {
        jdbc.update("""
                        INSERT INTO inv_stock_movement
                        (id, warehouse_id, material_id, biz_type, biz_id, biz_code,
                         qty_delta, unit_cost, amount, occurred_at, remark)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                idGen.next("inv_stock_movement"), warehouseId, materialId, bizType, bizId,
                bizCode, qtyDelta, unitCost, amount, new Timestamp(System.currentTimeMillis()), remark);
    }
}
