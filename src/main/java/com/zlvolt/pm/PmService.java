package com.zlvolt.pm;

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

@Service
public class PmService {

    private final JdbcTemplate jdbc;
    private final IdGen idGen;
    private final CodeGen codeGen;

    public PmService(JdbcTemplate jdbc, IdGen idGen, CodeGen codeGen) {
        this.jdbc = jdbc;
        this.idGen = idGen;
        this.codeGen = codeGen;
    }

    public List<Map<String, Object>> projects() {
        String sql = """
                SELECT p.id, p.code, p.name, p.contract_id, ct.code AS contract_code,
                       ct.amount AS contract_amount, p.customer_id, c.name AS customer_name,
                       p.type, p.capacity_mw, p.location, p.manager_id, u.real_name AS manager_name,
                       p.start_date, p.planned_grid_date, p.actual_grid_date,
                       p.budget_amount, p.status,
                       (SELECT COUNT(*) FROM pm_milestone m WHERE m.project_id = p.id) AS ms_total,
                       (SELECT COUNT(*) FROM pm_milestone m WHERE m.project_id = p.id AND m.status = 'DONE') AS ms_done,
                       (SELECT COALESCE(SUM(ci.amount), 0) FROM pm_cost_item ci WHERE ci.project_id = p.id) AS cost_total
                FROM pm_project p
                LEFT JOIN crm_contract ct ON ct.id = p.contract_id
                LEFT JOIN crm_customer c ON c.id = p.customer_id
                LEFT JOIN sys_user u ON u.id = p.manager_id
                ORDER BY p.id DESC
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        return rows.stream().map(this::enrichProject).toList();
    }

    public Map<String, Object> project(long id) {
        Map<String, Object> project = enrichProject(projectRow(id));
        project.put("wbs", MapUtils.camel(jdbc.queryForList(
                "SELECT id, project_id, code, name, budget_amount, owner_id, status FROM pm_wbs WHERE project_id = ? ORDER BY code", id)));
        project.put("milestones", MapUtils.camel(jdbc.queryForList(
                "SELECT id, project_id, name, plan_date, actual_date, status, sort FROM pm_milestone WHERE project_id = ? ORDER BY sort", id)));
        project.put("changeOrders", MapUtils.camel(jdbc.queryForList(
                "SELECT id, code, project_id, wbs_id, type, amount_delta, schedule_delta_days, reason, status FROM pm_change_order WHERE project_id = ? ORDER BY id DESC", id)));
        project.put("costItems", MapUtils.camel(jdbc.queryForList(
                "SELECT id, project_id, wbs_id, cost_type, source_type, source_id, amount, occurred_date, remark FROM pm_cost_item WHERE project_id = ? ORDER BY occurred_date DESC, id DESC", id)));
        project.put("costSummary", costSummary(id));
        return project;
    }

    public Map<String, Object> createProjectFromContract(long contractId) {
        List<Map<String, Object>> contracts = jdbc.queryForList("SELECT * FROM crm_contract WHERE id = ?", contractId);
        if (contracts.isEmpty()) {
            throw new BizException(40401, "合同不存在");
        }
        List<Map<String, Object>> exists = jdbc.queryForList("SELECT id FROM pm_project WHERE contract_id = ?", contractId);
        if (!exists.isEmpty()) {
            return project(MapUtils.toLong(exists.get(0).get("id"), 0L));
        }
        Map<String, Object> contract = MapUtils.camel(contracts.get(0));
        long id = idGen.next("pm_project");
        String code = "PRJ-" + LocalDate.now().getYear() + "-" + String.format("%04d", id);
        BigDecimal amount = MapUtils.decimal(contract, "amount");
        BigDecimal budget = amount.multiply(new BigDecimal("0.75")).setScale(2, RoundingMode.HALF_UP);
        String name = contract.get("name") + " 项目";
        long customerId = MapUtils.toLong(contract.get("customerId"), 0L);
        String customerName = "";
        List<Map<String, Object>> customers = jdbc.queryForList("SELECT name FROM crm_customer WHERE id = ?", customerId);
        if (!customers.isEmpty()) {
            customerName = String.valueOf(customers.get(0).get("name"));
        }
        String location = customerName.isBlank() ? "项目所在地" : customerName + " 所在地";
        jdbc.update("""
                        INSERT INTO pm_project
                        (id, code, name, contract_id, customer_id, type, capacity_mw, location,
                         manager_id, start_date, planned_grid_date, budget_amount, actual_cost, status)
                        VALUES (?, ?, ?, ?, ?, 'ROOFTOP', 1.0000, ?, 4, ?, ?, ?, 0, 'IN_PROGRESS')
                        """,
                id, code, name, contractId, customerId, location,
                Date.valueOf(LocalDate.now()), Date.valueOf(LocalDate.now().plusDays(90)), budget);

        String[] wbsCodes = {"1.1", "1.2", "1.3", "1.4", "1.5"};
        String[] wbsNames = {"设备采购", "土建工程", "安装工程", "电气调试", "并网及其他"};
        BigDecimal[] ratios = {
                new BigDecimal("0.60"), new BigDecimal("0.10"), new BigDecimal("0.15"),
                new BigDecimal("0.10"), new BigDecimal("0.05")
        };
        for (int i = 0; i < wbsCodes.length; i++) {
            jdbc.update("""
                            INSERT INTO pm_wbs (id, project_id, parent_id, code, name, type, budget_amount, owner_id, status)
                            VALUES (?, ?, 0, ?, ?, 'COST', ?, 4, 'ACTIVE')
                            """,
                    idGen.next("pm_wbs"), id, wbsCodes[i], wbsNames[i],
                    budget.multiply(ratios[i]).setScale(2, RoundingMode.HALF_UP));
        }

        String[] milestones = {"开工", "基础完成", "组件安装完成", "电气安装完成", "并网", "竣工验收"};
        int[] days = {0, 30, 60, 75, 90, 120};
        for (int i = 0; i < milestones.length; i++) {
            String status = i == 0 ? "DONE" : "PENDING";
            jdbc.update("""
                            INSERT INTO pm_milestone (id, project_id, name, plan_date, actual_date, status, sort)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    idGen.next("pm_milestone"), id, milestones[i],
                    Date.valueOf(LocalDate.now().plusDays(days[i])),
                    i == 0 ? Date.valueOf(LocalDate.now()) : null, status, i + 1);
        }
        return project(id);
    }

    public Map<String, Object> updateProgress(long projectId, int percent) {
        List<Map<String, Object>> milestones = jdbc.queryForList(
                "SELECT id FROM pm_milestone WHERE project_id = ? ORDER BY sort", projectId);
        if (milestones.isEmpty()) {
            throw new BizException("项目没有里程碑，无法更新进度");
        }
        int safe = Math.max(0, Math.min(100, percent));
        int done = (int) Math.round(safe * milestones.size() / 100.0);
        for (int i = 0; i < milestones.size(); i++) {
            long milestoneId = MapUtils.toLong(milestones.get(i).get("id"), 0L);
            if (i < done) {
                jdbc.update("UPDATE pm_milestone SET status = 'DONE', actual_date = COALESCE(actual_date, ?) WHERE id = ?",
                        Date.valueOf(LocalDate.now()), milestoneId);
            } else {
                jdbc.update("UPDATE pm_milestone SET status = 'PENDING', actual_date = NULL WHERE id = ?", milestoneId);
            }
        }
        return project(projectId);
    }

    public Map<String, Object> createChangeOrder(long projectId, Map<String, Object> body) {
        Map<String, Object> project = projectRow(projectId);
        long id = idGen.next("pm_change_order");
        String code = codeGen.next("CO");
        String type = MapUtils.text(body, "type");
        if (type == null) {
            type = "DESIGN";
        }
        BigDecimal amount = MapUtils.toDecimal(body.get("amountDelta"), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        int days = (int) MapUtils.toLong(body.get("scheduleDeltaDays"), 0L);
        String reason = MapUtils.text(body, "reason");
        jdbc.update("""
                        INSERT INTO pm_change_order
                        (id, code, project_id, wbs_id, type, amount_delta, schedule_delta_days, reason, status)
                        VALUES (?, ?, ?, 5, ?, ?, ?, ?, 'APPROVED')
                        """,
                id, code, projectId, type, amount, days, reason);
        if (amount.signum() != 0) {
            long costId = idGen.next("pm_cost_item");
            jdbc.update("""
                            INSERT INTO pm_cost_item
                            (id, project_id, wbs_id, cost_type, source_type, source_id, amount, occurred_date, remark)
                            VALUES (?, ?, 5, 'OTHER', 'CHANGE_ORDER', ?, ?, ?, ?)
                            """,
                    costId, projectId, id, amount, Date.valueOf(LocalDate.now()), "变更签证：" + reason);
            BigDecimal budget = MapUtils.decimal(project, "budget_amount").add(amount);
            jdbc.update("UPDATE pm_project SET budget_amount = ? WHERE id = ?", budget, projectId);
        }
        return project(projectId);
    }

    public Map<String, Object> addCost(long projectId, Map<String, Object> body) {
        projectRow(projectId);
        String costType = MapUtils.text(body, "costType");
        if (costType == null) {
            costType = "OTHER";
        }
        BigDecimal amount = MapUtils.toDecimal(body.get("amount"), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        long wbsId = MapUtils.toLong(body.get("wbsId"), 5L);
        Integer wbsValid = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pm_wbs WHERE id = ? AND project_id = ?", Integer.class, wbsId, projectId);
        if (wbsValid == null || wbsValid == 0) {
            Long minWbs = jdbc.queryForObject(
                    "SELECT MIN(id) FROM pm_wbs WHERE project_id = ?", Long.class, projectId);
            if (minWbs != null) {
                wbsId = minWbs;
            }
        }
        String remark = MapUtils.text(body, "remark");
        jdbc.update("""
                        INSERT INTO pm_cost_item
                        (id, project_id, wbs_id, cost_type, source_type, source_id, amount, occurred_date, remark)
                        VALUES (?, ?, ?, ?, 'MANUAL', NULL, ?, ?, ?)
                        """,
                idGen.next("pm_cost_item"), projectId, wbsId, costType, amount,
                Date.valueOf(LocalDate.now()), remark == null ? "手工成本" : remark);
        return costSummary(projectId);
    }

    public Map<String, Object> gridConnection(long projectId, Map<String, Object> body) {
        Map<String, Object> project = projectRow(projectId);
        LocalDate gridDate = LocalDate.now();
        String dateText = MapUtils.text(body, "gridDate");
        if (dateText != null && !dateText.isBlank()) {
            gridDate = LocalDate.parse(dateText);
        }
        jdbc.update("UPDATE pm_project SET actual_grid_date = ?, status = 'GRID_CONNECTED' WHERE id = ?",
                Date.valueOf(gridDate), projectId);
        jdbc.update("""
                        UPDATE pm_milestone SET status = 'DONE', actual_date = COALESCE(actual_date, ?)
                        WHERE project_id = ? AND name = '并网'
                        """, Date.valueOf(gridDate), projectId);

        List<Map<String, Object>> stations = jdbc.queryForList("SELECT * FROM om_station WHERE project_id = ?", projectId);
        if (!stations.isEmpty()) {
            Map<String, Object> station = MapUtils.camel(stations.get(0));
            List<Map<String, Object>> devices = MapUtils.camel(jdbc.queryForList(
                    "SELECT * FROM om_device WHERE station_id = ? ORDER BY id", station.get("id")));
            station.put("devices", devices);
            return MapUtils.map("station", station, "devices", devices);
        }
        long stationId = idGen.next("om_station");
        String stationCode = "ST-" + LocalDate.now().getYear() + "-" + String.format("%04d", stationId);
        String stationName = project.get("name") + "电站";
        BigDecimal capacity = MapUtils.decimal(project, "capacity_mw");
        jdbc.update("""
                        INSERT INTO om_station
                        (id, code, name, project_id, customer_id, capacity_mw, grid_connection_date,
                         address, manager_id, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 9, 'ACTIVE')
                        """,
                stationId, stationCode, stationName, projectId,
                MapUtils.toLong(project.get("customer_id"), 0L), capacity,
                Date.valueOf(gridDate), MapUtils.str(project, "location"));

        String[][] deviceDefs = {
                {"INV", "逆变器", "INVERTER", "HS-110K", "苏州恒晟"},
                {"INV", "逆变器", "INVERTER", "HS-110K", "苏州恒晟"},
                {"METER", "并网电表", "METER", "DTSD1352", "常州瑞光"},
                {"CMB", "汇流箱", "COMBINER_BOX", "CMB-16", "常州瑞光"},
                {"WS", "气象站", "WEATHER_STATION", "WS-3", "南京智维"}
        };
        for (int i = 0; i < deviceDefs.length; i++) {
            String code = deviceDefs[i][0] + "-" + String.format("%04d", stationId * 100 + i + 1);
            jdbc.update("""
                            INSERT INTO om_device
                            (id, code, station_id, name, type, model, manufacturer, install_date, status, last_online_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ONLINE', ?)
                            """,
                    idGen.next("om_device"), code, stationId, deviceDefs[i][1],
                    deviceDefs[i][2], deviceDefs[i][3], deviceDefs[i][4],
                    Date.valueOf(gridDate), new Timestamp(System.currentTimeMillis()));
        }
        return MapUtils.map("station", station(stationId),
                "devices", MapUtils.camel(jdbc.queryForList("SELECT * FROM om_device WHERE station_id = ? ORDER BY id", stationId)));
    }

    public Map<String, Object> settle(long projectId) {
        projectRow(projectId);
        jdbc.update("UPDATE pm_project SET status = 'SETTLED' WHERE id = ?", projectId);
        return project(projectId);
    }

    public Map<String, Object> costSummary(long projectId) {
        Map<String, Object> project = projectRow(projectId);
        List<Map<String, Object>> items = MapUtils.camel(jdbc.queryForList("""
                SELECT cost_type, COALESCE(SUM(amount), 0) AS amount
                FROM pm_cost_item WHERE project_id = ?
                GROUP BY cost_type ORDER BY cost_type
                """, projectId));
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> item : items) {
            total = total.add(MapUtils.decimal(item, "amount"));
        }
        BigDecimal budget = MapUtils.decimal(project, "budget_amount");
        BigDecimal contractAmount = MapUtils.decimal(project, "contract_amount");
        BigDecimal gross = contractAmount.subtract(total);
        BigDecimal grossRate = contractAmount.signum() == 0 ? BigDecimal.ZERO
                : gross.multiply(new BigDecimal("100")).divide(contractAmount, 2, RoundingMode.HALF_UP);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("projectCode", project.get("code"));
        result.put("projectName", project.get("name"));
        result.put("budgetAmount", budget);
        result.put("contractAmount", contractAmount);
        result.put("totalCost", total);
        result.put("grossMargin", gross);
        result.put("grossRate", grossRate);
        result.put("items", items);
        return result;
    }

    private Map<String, Object> projectRow(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.id, p.code, p.name, p.contract_id, ct.code AS contract_code,
                       ct.amount AS contract_amount, p.customer_id, c.name AS customer_name,
                       p.type, p.capacity_mw, p.location, p.manager_id, u.real_name AS manager_name,
                       p.start_date, p.planned_grid_date, p.actual_grid_date,
                       p.budget_amount, p.status,
                       (SELECT COUNT(*) FROM pm_milestone m WHERE m.project_id = p.id) AS ms_total,
                       (SELECT COUNT(*) FROM pm_milestone m WHERE m.project_id = p.id AND m.status = 'DONE') AS ms_done,
                       (SELECT COALESCE(SUM(ci.amount), 0) FROM pm_cost_item ci WHERE ci.project_id = p.id) AS cost_total
                FROM pm_project p
                LEFT JOIN crm_contract ct ON ct.id = p.contract_id
                LEFT JOIN crm_customer c ON c.id = p.customer_id
                LEFT JOIN sys_user u ON u.id = p.manager_id
                WHERE p.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BizException(40401, "项目不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> enrichProject(Map<String, Object> row) {
        Map<String, Object> project = MapUtils.camel(row);
        long total = MapUtils.toLong(project.get("msTotal"), 0L);
        long done = MapUtils.toLong(project.get("msDone"), 0L);
        project.put("progress", total == 0 ? 0 : Math.round(done * 100.0 / total));
        BigDecimal cost = MapUtils.decimal(project, "costTotal");
        BigDecimal contract = MapUtils.decimal(project, "contractAmount");
        project.put("grossMargin", contract.subtract(cost));
        return project;
    }

    private Map<String, Object> station(long stationId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM om_station WHERE id = ?", stationId);
        return rows.isEmpty() ? Map.of() : MapUtils.camel(rows.get(0));
    }
}
