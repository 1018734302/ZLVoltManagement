package com.zlvolt.fin;

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
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FinService {

    private final JdbcTemplate jdbc;
    private final IdGen idGen;
    private final CodeGen codeGen;

    public FinService(JdbcTemplate jdbc, IdGen idGen, CodeGen codeGen) {
        this.jdbc = jdbc;
        this.idGen = idGen;
        this.codeGen = codeGen;
    }

    public List<Map<String, Object>> reconciliations() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT r.id, r.code, r.supplier_id, s.name AS supplier_name,
                       r.project_id, p.name AS project_name, r.period,
                       r.total_qty, r.total_amount, r.tax_amount, r.diff_amount,
                       r.status, r.finance_confirm_at
                FROM fin_reconciliation r
                LEFT JOIN srm_supplier s ON s.id = r.supplier_id
                LEFT JOIN pm_project p ON p.id = r.project_id
                ORDER BY r.id DESC
                """));
    }

    public Map<String, Object> reconciliation(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.id, r.code, r.supplier_id, s.name AS supplier_name,
                       r.project_id, p.name AS project_name, r.period,
                       r.start_date, r.end_date, r.total_qty, r.total_amount,
                       r.tax_amount, r.diff_amount, r.status, r.finance_confirm_at
                FROM fin_reconciliation r
                LEFT JOIN srm_supplier s ON s.id = r.supplier_id
                LEFT JOIN pm_project p ON p.id = r.project_id
                WHERE r.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BizException(40401, "对账单不存在");
        }
        Map<String, Object> rec = MapUtils.camel(rows.get(0));
        rec.put("lines", MapUtils.camel(jdbc.queryForList("""
                SELECT rl.id, rl.reconciliation_id, rl.po_id, po.code AS po_code,
                       rl.po_line_id, rl.receipt_id, r.code AS receipt_code,
                       rl.material_id, m.name AS material_name,
                       rl.qty, rl.unit_price, rl.amount, rl.diff_qty, rl.diff_amount
                FROM fin_reconciliation_line rl
                LEFT JOIN srm_purchase_order po ON po.id = rl.po_id
                LEFT JOIN inv_receipt r ON r.id = rl.receipt_id
                LEFT JOIN srm_material m ON m.id = rl.material_id
                WHERE rl.reconciliation_id = ?
                ORDER BY rl.id
                """, id)));
        return rec;
    }

    public Map<String, Object> generateReconciliation(Map<String, Object> body) {
        long supplierId = MapUtils.toLong(body.get("supplierId"), 0L);
        long projectId = MapUtils.toLong(body.get("projectId"), 0L);
        if (supplierId == 0L || projectId == 0L) {
            throw new BizException("请选择供应商和项目");
        }
        String period = MapUtils.text(body, "period");
        if (period == null || period.isBlank()) {
            period = YearMonth.now().toString();
        }
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT id FROM fin_reconciliation
                WHERE supplier_id = ? AND project_id = ? AND period = ?
                """, supplierId, projectId, period);
        if (!existing.isEmpty()) {
            return reconciliation(MapUtils.toLong(existing.get(0).get("id"), 0L));
        }
        YearMonth ym = YearMonth.parse(period);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT r.id AS receipt_id, r.code AS receipt_code, r.receipt_date,
                       po.id AS po_id, po.code AS po_code, l.id AS po_line_id,
                       l.material_id, m.name AS material_name,
                       l.qualified_qty AS qty, l.unit_price, l.amount
                FROM inv_receipt r
                JOIN inv_receipt_line l ON l.receipt_id = r.id
                JOIN srm_purchase_order po ON po.id = r.po_id
                JOIN srm_material m ON m.id = l.material_id
                WHERE po.supplier_id = ? AND r.project_id = ? AND r.status = 'STORED'
                  AND r.receipt_date >= ? AND r.receipt_date < ?
                ORDER BY r.id, l.id
                """, supplierId, projectId, Date.valueOf(start), Date.valueOf(end));
        if (lines.isEmpty()) {
            throw new BizException("该供应商/项目在 " + period + " 没有可对账的收货单");
        }
        long id = idGen.next("fin_reconciliation");
        String code = "REC-" + period.replace("-", "") + "-" + String.format("%04d", id);
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalQty = BigDecimal.ZERO;
        for (Map<String, Object> line : lines) {
            Map<String, Object> camel = MapUtils.camel(line);
            totalAmount = totalAmount.add(MapUtils.decimal(camel, "amount"));
            totalQty = totalQty.add(MapUtils.decimal(camel, "qty"));
        }
        BigDecimal tax = totalAmount.subtract(totalAmount.divide(new BigDecimal("1.13"), 2, RoundingMode.HALF_UP));
        jdbc.update("""
                        INSERT INTO fin_reconciliation
                        (id, code, supplier_id, project_id, period, start_date, end_date,
                         total_qty, total_amount, tax_amount, diff_amount, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'DRAFT')
                        """,
                id, code, supplierId, projectId, period, Date.valueOf(start), Date.valueOf(end),
                totalQty, totalAmount, tax);
        for (Map<String, Object> line : lines) {
            Map<String, Object> camel = MapUtils.camel(line);
            jdbc.update("""
                            INSERT INTO fin_reconciliation_line
                            (id, reconciliation_id, po_id, po_line_id, receipt_id, material_id,
                             qty, unit_price, amount, diff_qty, diff_amount)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)
                            """,
                    idGen.next("fin_reconciliation_line"), id,
                    MapUtils.toLong(camel.get("poId"), 0L), MapUtils.toLong(camel.get("poLineId"), 0L),
                    MapUtils.toLong(camel.get("receiptId"), 0L), MapUtils.toLong(camel.get("materialId"), 0L),
                    MapUtils.decimal(camel, "qty"), MapUtils.decimal(camel, "unitPrice"),
                    MapUtils.decimal(camel, "amount"));
        }
        return reconciliation(id);
    }

    public Map<String, Object> confirmReconciliation(long id) {
        Map<String, Object> rec = reconciliation(id);
        String status = MapUtils.str(rec, "status");
        if ("FINANCE_CONFIRMED".equals(status) || "CLOSED".equals(status) || "INVOICED".equals(status)) {
            return rec;
        }
        Timestamp now = new Timestamp(System.currentTimeMillis());
        jdbc.update("""
                        UPDATE fin_reconciliation
                        SET status = 'FINANCE_CONFIRMED',
                            supplier_confirm_at = COALESCE(supplier_confirm_at, ?),
                            buyer_confirm_at = COALESCE(buyer_confirm_at, ?),
                            finance_confirm_at = ?
                        WHERE id = ?
                        """,
                now, now, now, id);
        return reconciliation(id);
    }

    public List<Map<String, Object>> invoices() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT i.id, i.code, i.invoice_no, i.invoice_type,
                       i.supplier_id, s.name AS supplier_name,
                       i.reconciliation_id, r.code AS reconciliation_code,
                       i.invoice_date, i.amount, i.tax_amount, i.status, i.match_result
                FROM fin_invoice i
                LEFT JOIN srm_supplier s ON s.id = i.supplier_id
                LEFT JOIN fin_reconciliation r ON r.id = i.reconciliation_id
                ORDER BY i.id DESC
                """));
    }

    public Map<String, Object> invoice(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT i.id, i.code, i.invoice_no, i.invoice_type,
                       i.supplier_id, s.name AS supplier_name,
                       i.reconciliation_id, r.code AS reconciliation_code,
                       i.invoice_date, i.amount, i.tax_amount, i.status, i.match_result
                FROM fin_invoice i
                LEFT JOIN srm_supplier s ON s.id = i.supplier_id
                LEFT JOIN fin_reconciliation r ON r.id = i.reconciliation_id
                WHERE i.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BizException(40401, "发票不存在");
        }
        return MapUtils.camel(rows.get(0));
    }

    public Map<String, Object> createInvoice(Map<String, Object> body) {
        long reconciliationId = MapUtils.toLong(body.get("reconciliationId"), 0L);
        if (reconciliationId == 0L) {
            throw new BizException("请选择对账单");
        }
        Map<String, Object> rec = reconciliation(reconciliationId);
        String invoiceNo = MapUtils.text(body, "invoiceNo");
        if (invoiceNo == null || invoiceNo.isBlank()) {
            invoiceNo = "FP" + System.currentTimeMillis();
        }
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM fin_invoice WHERE invoice_no = ?", Integer.class, invoiceNo);
        if (exists != null && exists > 0) {
            throw new BizException("发票号码已登记: " + invoiceNo);
        }
        BigDecimal amount = MapUtils.toDecimal(body.get("amount"), MapUtils.decimal(rec, "totalAmount"));
        BigDecimal tax = MapUtils.toDecimal(body.get("taxAmount"),
                amount.subtract(amount.divide(new BigDecimal("1.13"), 2, RoundingMode.HALF_UP)));
        long id = idGen.next("fin_invoice");
        String code = codeGen.next("INV");
        jdbc.update("""
                        INSERT INTO fin_invoice
                        (id, code, invoice_no, invoice_type, supplier_id, reconciliation_id,
                         invoice_date, amount, tax_amount, tax_rate, status)
                        VALUES (?, ?, ?, 'VAT_SPECIAL', ?, ?, ?, ?, ?, 0.1300, 'PENDING')
                        """,
                id, code, invoiceNo, MapUtils.toLong(rec.get("supplierId"), 0L),
                reconciliationId, Date.valueOf(LocalDate.now()), amount, tax);
        return invoice(id);
    }

    public Map<String, Object> matchInvoice(long id) {
        Map<String, Object> invoice = invoice(id);
        long reconciliationId = MapUtils.toLong(invoice.get("reconciliationId"), 0L);
        Map<String, Object> rec = reconciliation(reconciliationId);
        BigDecimal recAmount = MapUtils.decimal(rec, "totalAmount");
        BigDecimal invoiceAmount = MapUtils.decimal(invoice, "amount");
        BigDecimal diff = invoiceAmount.subtract(recAmount).abs();
        BigDecimal tolerance = recAmount.multiply(new BigDecimal("0.005"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("invoice", invoice);
        result.put("reconciliation", rec);
        result.put("diffAmount", diff);
        result.put("tolerance", tolerance);
        if (diff.compareTo(tolerance) > 0) {
            jdbc.update("UPDATE fin_invoice SET status = 'MISMATCH', match_result = 'MISMATCH' WHERE id = ?", id);
            result.put("matchResult", "MISMATCH");
            result.put("message", "发票金额与对账金额差异超容差，已转异常处理");
            return result;
        }
        jdbc.update("UPDATE fin_invoice SET status = 'MATCHED', match_result = 'MATCHED' WHERE id = ?", id);
        Map<String, Object> payable = createPayable(rec);
        result.put("invoice", invoice(id));
        result.put("payable", payable);
        result.put("matchResult", "MATCHED");
        result.put("message", "三单匹配通过，已生成应付账款");
        return result;
    }

    public List<Map<String, Object>> payables() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT p.id, p.code, p.supplier_id, s.name AS supplier_name,
                       p.project_id, pr.name AS project_name, p.source_type, p.source_id,
                       p.amount, p.paid_amount, p.prepaid_offset, p.retention_amount,
                       p.available_amount, p.due_date, p.status
                FROM fin_payable p
                LEFT JOIN srm_supplier s ON s.id = p.supplier_id
                LEFT JOIN pm_project pr ON pr.id = p.project_id
                ORDER BY p.id DESC
                """));
    }

    public Map<String, Object> payable(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.id, p.code, p.supplier_id, s.name AS supplier_name,
                       p.project_id, pr.name AS project_name, p.source_type, p.source_id,
                       p.amount, p.paid_amount, p.prepaid_offset, p.retention_amount,
                       p.available_amount, p.due_date, p.status
                FROM fin_payable p
                LEFT JOIN srm_supplier s ON s.id = p.supplier_id
                LEFT JOIN pm_project pr ON pr.id = p.project_id
                WHERE p.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BizException(40401, "应付账款不存在");
        }
        return MapUtils.camel(rows.get(0));
    }

    private Map<String, Object> createPayable(Map<String, Object> rec) {
        long recId = MapUtils.toLong(rec.get("id"), 0L);
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT id FROM fin_payable
                WHERE source_type = 'RECONCILIATION' AND source_id = ?
                """, recId);
        if (!existing.isEmpty()) {
            return payable(MapUtils.toLong(existing.get(0).get("id"), 0L));
        }
        BigDecimal amount = MapUtils.decimal(rec, "totalAmount");
        BigDecimal prepaid = amount.multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal retention = amount.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal available = amount.subtract(prepaid).subtract(retention);
        long id = idGen.next("fin_payable");
        String code = codeGen.next("AP");
        jdbc.update("""
                        INSERT INTO fin_payable
                        (id, code, supplier_id, project_id, source_type, source_id,
                         amount, paid_amount, prepaid_offset, retention_amount,
                         available_amount, due_date, status)
                        VALUES (?, ?, ?, ?, 'RECONCILIATION', ?, ?, 0, ?, ?, ?, ?, 'OPEN')
                        """,
                id, code, MapUtils.toLong(rec.get("supplierId"), 0L),
                MapUtils.toLong(rec.get("projectId"), 0L), recId,
                amount, prepaid, retention, available,
                Date.valueOf(LocalDate.now().plusDays(30)));
        return payable(id);
    }
}
