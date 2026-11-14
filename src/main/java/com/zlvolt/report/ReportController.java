package com.zlvolt.report;

import com.zlvolt.common.ApiResponse;
import com.zlvolt.common.MapUtils;
import com.zlvolt.pm.PmService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final JdbcTemplate jdbc;
    private final PmService pmService;

    public ReportController(JdbcTemplate jdbc, PmService pmService) {
        this.jdbc = jdbc;
        this.pmService = pmService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contractAmount", sum("SELECT COALESCE(SUM(amount), 0) FROM crm_contract WHERE status = 'EFFECTIVE'"));
        result.put("projectCount", count("SELECT COUNT(*) FROM pm_project"));
        result.put("poAmount", sum("SELECT COALESCE(SUM(total_amount), 0) FROM srm_purchase_order"));
        result.put("payableOpen", sum("SELECT COALESCE(SUM(available_amount), 0) FROM fin_payable WHERE status IN ('OPEN', 'PARTIAL')"));
        result.put("paidAmount", sum("SELECT COALESCE(SUM(amount), 0) FROM fund_payment_record"));
        result.put("alarmOpen", count("SELECT COUNT(*) FROM om_alarm WHERE status = 'OPEN'"));
        result.put("workOrderOpen", count("SELECT COUNT(*) FROM om_work_order WHERE status IN ('CREATED', 'ASSIGNED', 'PROCESSING')"));
        result.put("projectStatus", MapUtils.camel(jdbc.queryForList(
                "SELECT status, COUNT(*) AS cnt FROM pm_project GROUP BY status ORDER BY status")));
        result.put("recentProjects", MapUtils.camel(jdbc.queryForList("""
                SELECT p.id, p.code, p.name, c.name AS customer_name, p.status,
                       p.budget_amount, p.capacity_mw,
                       (SELECT COALESCE(SUM(ci.amount), 0) FROM pm_cost_item ci WHERE ci.project_id = p.id) AS cost_total
                FROM pm_project p
                LEFT JOIN crm_customer c ON c.id = p.customer_id
                ORDER BY p.id DESC LIMIT 5
                """)));
        result.put("recentPurchaseOrders", MapUtils.camel(jdbc.queryForList("""
                SELECT po.id, po.code, s.name AS supplier_name, p.name AS project_name,
                       po.total_amount, po.status, po.order_date
                FROM srm_purchase_order po
                LEFT JOIN srm_supplier s ON s.id = po.supplier_id
                LEFT JOIN pm_project p ON p.id = po.project_id
                ORDER BY po.id DESC LIMIT 5
                """)));
        result.put("costByType", MapUtils.camel(jdbc.queryForList("""
                SELECT cost_type, COALESCE(SUM(amount), 0) AS amount
                FROM pm_cost_item GROUP BY cost_type ORDER BY cost_type
                """)));
        result.put("chain", MapUtils.map(
                "L2C 合同", count("SELECT COUNT(*) FROM crm_contract"),
                "P2P 收货", count("SELECT COUNT(*) FROM inv_receipt"),
                "财务对账", count("SELECT COUNT(*) FROM fin_reconciliation"),
                "资金付款", count("SELECT COUNT(*) FROM fund_payment_record"),
                "运维告警", count("SELECT COUNT(*) FROM om_alarm")));
        return ApiResponse.ok(result);
    }

    @GetMapping("/project-cost")
    public ApiResponse<Map<String, Object>> projectCost(@RequestParam long projectId) {
        return ApiResponse.ok(pmService.costSummary(projectId));
    }

    private BigDecimal sum(String sql) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private Integer count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
