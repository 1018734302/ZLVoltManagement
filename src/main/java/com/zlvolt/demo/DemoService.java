package com.zlvolt.demo;

import com.zlvolt.common.MapUtils;
import com.zlvolt.crm.CrmService;
import com.zlvolt.fin.FinService;
import com.zlvolt.fund.FundService;
import com.zlvolt.inv.InvService;
import com.zlvolt.iot.IotService;
import com.zlvolt.om.OmService;
import com.zlvolt.pm.PmService;
import com.zlvolt.srm.SrmService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class DemoService {

    private final CrmService crmService;
    private final PmService pmService;
    private final SrmService srmService;
    private final InvService invService;
    private final FinService finService;
    private final FundService fundService;
    private final IotService iotService;
    private final OmService omService;
    private final JdbcTemplate jdbc;

    public DemoService(CrmService crmService, PmService pmService, SrmService srmService,
                       InvService invService, FinService finService, FundService fundService,
                       IotService iotService, OmService omService, JdbcTemplate jdbc) {
        this.crmService = crmService;
        this.pmService = pmService;
        this.srmService = srmService;
        this.invService = invService;
        this.finService = finService;
        this.fundService = fundService;
        this.iotService = iotService;
        this.omService = omService;
        this.jdbc = jdbc;
    }

    public Map<String, Object> run() {
        List<Map<String, Object>> steps = new ArrayList<>();
        long stamp = System.currentTimeMillis();
        try {
            Map<String, Object> customer = step(steps, "1. CRM 创建客户", () ->
                    crmService.createCustomer(MapUtils.map(
                            "name", "演示客户-" + stamp,
                            "type", "ROOFTOP_OWNER",
                            "region", "江苏苏州",
                            "contactName", "演示联系人",
                            "contactPhone", "13900000000")));
            long customerId = MapUtils.toLong(customer.get("id"), 0L);

            Map<String, Object> lead = step(steps, "2. CRM 登记线索", () ->
                    crmService.createLead(MapUtils.map(
                            "name", "演示 1MW 屋顶光伏线索-" + stamp,
                            "customerId", customerId,
                            "source", "一键演示",
                            "estimatedCapacityMw", 1.0,
                            "estimatedAmount", 4800000)));
            long leadId = MapUtils.toLong(lead.get("id"), 0L);

            Map<String, Object> opportunity = step(steps, "3. CRM 线索转商机", () ->
                    crmService.convertLead(leadId));
            long opportunityId = MapUtils.toLong(opportunity.get("id"), 0L);

            Map<String, Object> contract = step(steps, "4. CRM 签订销售合同", () ->
                    crmService.createContract(MapUtils.map(
                            "customerId", customerId,
                            "opportunityId", opportunityId,
                            "name", "演示 1MW 屋顶光伏 EPC 合同-" + stamp,
                            "amount", 4800000)));
            long contractId = MapUtils.toLong(contract.get("id"), 0L);

            Map<String, Object> project = step(steps, "5. PM 合同立项", () ->
                    pmService.createProjectFromContract(contractId));
            long projectId = MapUtils.toLong(project.get("id"), 0L);
            long wbsId = jdbc.queryForObject(
                    "SELECT MIN(id) FROM pm_wbs WHERE project_id = ?", Long.class, projectId);

            Map<String, Object> po = step(steps, "6. SRM 创建采购订单", () ->
                    srmService.createPurchaseOrder(MapUtils.map(
                            "projectId", projectId,
                            "supplierId", 1L,
                            "deliveryDate", LocalDate.now().plusDays(15).toString(),
                            "lines", List.of(
                                    MapUtils.map("materialId", 1L, "qty", 1000, "unitPrice", 850),
                                    MapUtils.map("materialId", 5L, "qty", 100, "unitPrice", 3200)))));
            long poId = MapUtils.toLong(po.get("id"), 0L);

            step(steps, "7. OA 采购订单审批", () -> srmService.approve(poId));
            Map<String, Object> receipt = step(steps, "8. INV 到货验收入库", () ->
                    invService.receivePo(poId, 2L));
            long receiptId = MapUtils.toLong(receipt.get("id"), 0L);

            Map<String, Object> reconciliation = step(steps, "9. FIN 生成供应商对账单", () ->
                    finService.generateReconciliation(MapUtils.map(
                            "supplierId", 1L,
                            "projectId", projectId,
                            "period", YearMonth.now().toString())));
            long reconciliationId = MapUtils.toLong(reconciliation.get("id"), 0L);

            step(steps, "10. FIN 对账确认", () -> finService.confirmReconciliation(reconciliationId));
            Map<String, Object> invoice = step(steps, "11. FIN 发票登记", () ->
                    finService.createInvoice(MapUtils.map(
                            "reconciliationId", reconciliationId,
                            "invoiceNo", "DEMO" + stamp,
                            "amount", reconciliation.get("totalAmount"))));
            long invoiceId = MapUtils.toLong(invoice.get("id"), 0L);

            Map<String, Object> match = step(steps, "12. FIN 三单匹配生成应付", () ->
                    finService.matchInvoice(invoiceId));
            @SuppressWarnings("unchecked")
            Map<String, Object> payable = (Map<String, Object>) match.get("payable");
            long payableId = MapUtils.toLong(payable.get("id"), 0L);
            BigDecimal payableAmount = MapUtils.decimal(payable, "availableAmount");

            Map<String, Object> paymentRequest = step(steps, "13. FUND 创建付款申请", () ->
                    fundService.createPaymentRequest(MapUtils.map(
                            "payableId", payableId,
                            "amount", payableAmount,
                            "paymentType", "PROGRESS",
                            "accountId", 1L)));
            long paymentRequestId = MapUtils.toLong(paymentRequest.get("id"), 0L);

            step(steps, "14. OA 付款审批", () -> fundService.approve(paymentRequestId));
            Map<String, Object> payment = step(steps, "15. FUND 执行付款", () ->
                    fundService.pay(paymentRequestId));

            Map<String, Object> issue = step(steps, "16. INV 项目领料出库", () ->
                    invService.issueMaterials(MapUtils.map(
                            "projectId", projectId,
                            "wbsId", wbsId,
                            "warehouseId", 2L,
                            "lines", List.of(
                                    MapUtils.map("materialId", 1L, "qty", 900),
                                    MapUtils.map("materialId", 5L, "qty", 90)))));

            step(steps, "17. PM 归集人工成本", () ->
                    pmService.addCost(projectId, MapUtils.map(
                            "costType", "LABOR", "amount", 200000, "wbsId", wbsId, "remark", "演示人工成本")));
            step(steps, "18. PM 归集分包成本", () ->
                    pmService.addCost(projectId, MapUtils.map(
                            "costType", "SUBCONTRACT", "amount", 100000, "wbsId", wbsId, "remark", "演示分包成本")));

            Map<String, Object> grid = step(steps, "19. PM 并网登记", () ->
                    pmService.gridConnection(projectId, MapUtils.map("gridDate", LocalDate.now().toString())));
            @SuppressWarnings("unchecked")
            Map<String, Object> station = (Map<String, Object>) grid.get("station");
            long stationId = MapUtils.toLong(station.get("id"), 0L);

            Map<String, Object> iotResult = step(steps, "20. IoT 设备故障上报", () ->
                    iotService.simulate("fault", stationId));
            @SuppressWarnings("unchecked")
            Map<String, Object> iotInner = (Map<String, Object>) iotResult.get("result");
            @SuppressWarnings("unchecked")
            Map<String, Object> workOrder = (Map<String, Object>) iotInner.get("workOrder");
            if (workOrder != null) {
                long workOrderId = MapUtils.toLong(workOrder.get("id"), 0L);
                step(steps, "21. O&M 工单处理完成", () ->
                        omService.complete(workOrderId, MapUtils.map(
                                "costAmount", 2800,
                                "description", "一键演示：更换风扇并复测温度，设备恢复运行")));
            } else {
                steps.add(stepItem("21. O&M 工单处理完成", "SKIP", "未生成工单"));
            }

            step(steps, "22. PM 项目结算", () -> pmService.settle(projectId));

            Map<String, Object> cost = pmService.costSummary(projectId);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("customerCode", customer.get("code"));
            summary.put("contractCode", contract.get("code"));
            summary.put("contractAmount", contract.get("amount"));
            summary.put("projectId", projectId);
            summary.put("projectCode", project.get("code"));
            summary.put("projectName", project.get("name"));
            summary.put("poCode", po.get("code"));
            summary.put("poAmount", po.get("totalAmount"));
            summary.put("receiptCode", receipt.get("code"));
            summary.put("receiptId", receiptId);
            summary.put("reconciliationCode", reconciliation.get("code"));
            summary.put("invoiceCode", invoice.get("code"));
            summary.put("payableCode", payable.get("code"));
            summary.put("payableAmount", payable.get("amount"));
            @SuppressWarnings("unchecked")
            Map<String, Object> paidPayable = (Map<String, Object>) payment.get("payable");
            summary.put("paidAmount", MapUtils.decimal(paidPayable, "paidAmount"));
            summary.put("paymentCode", payment.get("paymentCode"));
            summary.put("issueCode", issue.get("code"));
            summary.put("projectCost", cost.get("totalCost"));
            summary.put("grossMargin", cost.get("grossMargin"));
            summary.put("stationCode", station.get("code"));
            summary.put("alarmCount", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM om_alarm WHERE station_id = ?", Integer.class, stationId));
            summary.put("workOrderCount", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM om_work_order WHERE station_id = ?", Integer.class, stationId));
            return MapUtils.map("success", true, "message", "全业务链演示完成", "steps", steps, "summary", summary);
        } catch (DemoAbortException e) {
            return MapUtils.map("success", false, "message", e.getMessage(), "steps", steps);
        } catch (Exception e) {
            steps.add(stepItem("异常", "FAIL", e.getMessage()));
            return MapUtils.map("success", false, "message", e.getMessage(), "steps", steps);
        }
    }

    private <T> T step(List<Map<String, Object>> steps, String name, Supplier<T> action) {
        try {
            T result = action.get();
            steps.add(stepItem(name, "OK", detail(result)));
            return result;
        } catch (Exception e) {
            steps.add(stepItem(name, "FAIL", e.getMessage()));
            throw new DemoAbortException(e.getMessage());
        }
    }

    private Map<String, Object> stepItem(String name, String status, String detail) {
        return MapUtils.map("name", name, "status", status, "detail", detail);
    }

    private String detail(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object station = map.get("station");
            if (station instanceof Map<?, ?> stationMap) {
                return "电站 " + stationMap.get("code");
            }
            Object payload = map.get("payload");
            if (payload != null) {
                return "遥测已入库，告警/工单已生成";
            }
            Object projectCode = map.get("projectCode");
            if (projectCode != null) {
                return projectCode + "，成本 " + map.get("totalCost");
            }
            Object code = map.get("code");
            if (code != null) {
                return String.valueOf(code);
            }
            Object paymentCode = map.get("paymentCode");
            if (paymentCode != null) {
                return String.valueOf(paymentCode);
            }
            Object id = map.get("id");
            if (id != null) {
                return "ID " + id;
            }
            Object matchResult = map.get("matchResult");
            if (matchResult != null) {
                return String.valueOf(matchResult);
            }
        }
        return value == null ? "" : String.valueOf(value);
    }

    private static class DemoAbortException extends RuntimeException {
        DemoAbortException(String message) {
            super(message);
        }
    }
}
