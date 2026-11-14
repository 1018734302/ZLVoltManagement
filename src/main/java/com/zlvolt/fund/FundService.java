package com.zlvolt.fund;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FundService {

    private final JdbcTemplate jdbc;
    private final IdGen idGen;
    private final CodeGen codeGen;

    public FundService(JdbcTemplate jdbc, IdGen idGen, CodeGen codeGen) {
        this.jdbc = jdbc;
        this.idGen = idGen;
        this.codeGen = codeGen;
    }

    public List<Map<String, Object>> accounts() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT id, code, name, bank_name, account_no, balance, status
                FROM fund_account ORDER BY id
                """));
    }

    public List<Map<String, Object>> paymentRequests() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT q.id, q.code, q.payable_id, p.code AS payable_code,
                       q.supplier_id, s.name AS supplier_name,
                       q.project_id, pr.name AS project_name,
                       q.amount, q.payment_type, q.account_id, a.name AS account_name,
                       q.apply_date, q.status
                FROM fund_payment_request q
                LEFT JOIN fin_payable p ON p.id = q.payable_id
                LEFT JOIN srm_supplier s ON s.id = q.supplier_id
                LEFT JOIN pm_project pr ON pr.id = q.project_id
                LEFT JOIN fund_account a ON a.id = q.account_id
                ORDER BY q.id DESC
                """));
    }

    public Map<String, Object> paymentRequest(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT q.id, q.code, q.payable_id, p.code AS payable_code,
                       q.supplier_id, s.name AS supplier_name,
                       q.project_id, pr.name AS project_name,
                       q.amount, q.payment_type, q.account_id, a.name AS account_name,
                       q.apply_date, q.status
                FROM fund_payment_request q
                LEFT JOIN fin_payable p ON p.id = q.payable_id
                LEFT JOIN srm_supplier s ON s.id = q.supplier_id
                LEFT JOIN pm_project pr ON pr.id = q.project_id
                LEFT JOIN fund_account a ON a.id = q.account_id
                WHERE q.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BizException(40401, "付款申请不存在");
        }
        return MapUtils.camel(rows.get(0));
    }

    public Map<String, Object> createPaymentRequest(Map<String, Object> body) {
        long payableId = MapUtils.toLong(body.get("payableId"), 0L);
        if (payableId == 0L) {
            throw new BizException("请选择应付账款");
        }
        List<Map<String, Object>> payables = jdbc.queryForList("SELECT * FROM fin_payable WHERE id = ?", payableId);
        if (payables.isEmpty()) {
            throw new BizException(40401, "应付账款不存在");
        }
        Map<String, Object> payable = MapUtils.camel(payables.get(0));
        String status = MapUtils.str(payable, "status");
        if ("PAID".equals(status) || "CLOSED".equals(status)) {
            throw new BizException("该应付账款已结清");
        }
        BigDecimal available = MapUtils.decimal(payable, "availableAmount");
        BigDecimal amount = MapUtils.toDecimal(body.get("amount"), available).setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || amount.compareTo(available) > 0) {
            throw new BizException("付款金额超过应付可用余额，可用 " + available);
        }
        long accountId = MapUtils.toLong(body.get("accountId"), 1L);
        List<Map<String, Object>> accounts = jdbc.queryForList("SELECT * FROM fund_account WHERE id = ?", accountId);
        if (accounts.isEmpty()) {
            throw new BizException("付款账户不存在");
        }
        long id = idGen.next("fund_payment_request");
        String code = codeGen.next("PAY");
        String paymentType = MapUtils.text(body, "paymentType");
        if (paymentType == null) {
            paymentType = "PROGRESS";
        }
        jdbc.update("""
                        INSERT INTO fund_payment_request
                        (id, code, payable_id, supplier_id, project_id, amount, payment_type,
                         account_id, apply_date, status, request_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'APPROVING', ?)
                        """,
                id, code, payableId, MapUtils.toLong(payable.get("supplierId"), 0L),
                MapUtils.toLong(payable.get("projectId"), 0L), amount, paymentType,
                accountId, Date.valueOf(LocalDate.now()), UUID.randomUUID().toString());
        return paymentRequest(id);
    }

    public Map<String, Object> approve(long id) {
        Map<String, Object> request = paymentRequest(id);
        if (!"APPROVING".equals(request.get("status")) && !"DRAFT".equals(request.get("status"))) {
            throw new BizException("当前付款申请状态不允许审批: " + request.get("status"));
        }
        jdbc.update("UPDATE fund_payment_request SET status = 'APPROVED' WHERE id = ?", id);
        return paymentRequest(id);
    }

    public Map<String, Object> pay(long id) {
        Map<String, Object> request = paymentRequest(id);
        if (!"APPROVED".equals(request.get("status"))) {
            throw new BizException("付款申请未审批通过，不能付款");
        }
        BigDecimal amount = MapUtils.decimal(request, "amount");
        long payableId = MapUtils.toLong(request.get("payableId"), 0L);
        long accountId = MapUtils.toLong(request.get("accountId"), 0L);
        List<Map<String, Object>> payables = jdbc.queryForList("SELECT * FROM fin_payable WHERE id = ?", payableId);
        List<Map<String, Object>> accounts = jdbc.queryForList("SELECT * FROM fund_account WHERE id = ?", accountId);
        if (payables.isEmpty() || accounts.isEmpty()) {
            throw new BizException("应付或付款账户不存在");
        }
        Map<String, Object> payable = MapUtils.camel(payables.get(0));
        Map<String, Object> account = MapUtils.camel(accounts.get(0));
        if (amount.compareTo(MapUtils.decimal(payable, "availableAmount")) > 0) {
            throw new BizException("付款金额超过应付可用余额");
        }
        if (amount.compareTo(MapUtils.decimal(account, "balance")) > 0) {
            throw new BizException("付款账户余额不足");
        }
        long paymentId = idGen.next("fund_payment_record");
        String paymentCode = codeGen.next("PMT");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        jdbc.update("""
                        INSERT INTO fund_payment_record
                        (id, code, payment_request_id, account_id, amount, payment_date,
                         method, bank_serial_no, status)
                        VALUES (?, ?, ?, ?, ?, ?, 'TRANSFER', ?, 'SUCCESS')
                        """,
                paymentId, paymentCode, id, accountId, amount, now,
                "BK" + System.currentTimeMillis());
        BigDecimal paid = MapUtils.decimal(payable, "paidAmount").add(amount);
        BigDecimal available = MapUtils.decimal(payable, "availableAmount").subtract(amount);
        String payableStatus = available.signum() == 0 ? "PAID" : "PARTIAL";
        jdbc.update("UPDATE fin_payable SET paid_amount = ?, available_amount = ?, status = ? WHERE id = ?",
                paid, available, payableStatus, payableId);
        jdbc.update("UPDATE fund_payment_request SET status = 'PAID' WHERE id = ?", id);
        jdbc.update("UPDATE fund_account SET balance = balance - ? WHERE id = ?", amount, accountId);
        jdbc.update("""
                        INSERT INTO fin_payment_allocation (id, payment_id, payable_id, amount)
                        VALUES (?, ?, ?, ?)
                        """,
                idGen.next("fin_payment_allocation"), paymentId, payableId, amount);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paymentId", paymentId);
        result.put("paymentCode", paymentCode);
        result.put("request", paymentRequest(id));
        result.put("payable", payable(payableId));
        return result;
    }

    public List<Map<String, Object>> payments() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT p.id, p.code, p.payment_request_id, q.code AS request_code,
                       p.account_id, a.name AS account_name, p.amount, p.payment_date,
                       p.method, p.bank_serial_no, p.status
                FROM fund_payment_record p
                LEFT JOIN fund_payment_request q ON q.id = p.payment_request_id
                LEFT JOIN fund_account a ON a.id = p.account_id
                ORDER BY p.id DESC
                """));
    }

    private Map<String, Object> payable(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.id, p.code, p.supplier_id, s.name AS supplier_name,
                       p.project_id, pr.name AS project_name, p.amount, p.paid_amount,
                       p.prepaid_offset, p.retention_amount, p.available_amount,
                       p.due_date, p.status
                FROM fin_payable p
                LEFT JOIN srm_supplier s ON s.id = p.supplier_id
                LEFT JOIN pm_project pr ON pr.id = p.project_id
                WHERE p.id = ?
                """, id);
        return rows.isEmpty() ? Map.of() : MapUtils.camel(rows.get(0));
    }
}
