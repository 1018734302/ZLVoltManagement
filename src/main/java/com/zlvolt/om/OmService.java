package com.zlvolt.om;

import com.zlvolt.common.BizException;
import com.zlvolt.common.CodeGen;
import com.zlvolt.common.IdGen;
import com.zlvolt.common.MapUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OmService {

    private final JdbcTemplate jdbc;
    private final IdGen idGen;
    private final CodeGen codeGen;

    public OmService(JdbcTemplate jdbc, IdGen idGen, CodeGen codeGen) {
        this.jdbc = jdbc;
        this.idGen = idGen;
        this.codeGen = codeGen;
    }

    public List<Map<String, Object>> stations() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT s.id, s.code, s.name, s.project_id, p.name AS project_name,
                       s.customer_id, c.name AS customer_name, s.capacity_mw,
                       s.grid_connection_date, s.address, s.status,
                       (SELECT COUNT(*) FROM om_device d WHERE d.station_id = s.id) AS device_count,
                       (SELECT COUNT(*) FROM om_alarm a WHERE a.station_id = s.id AND a.status = 'OPEN') AS open_alarm_count
                FROM om_station s
                LEFT JOIN pm_project p ON p.id = s.project_id
                LEFT JOIN crm_customer c ON c.id = s.customer_id
                ORDER BY s.id
                """));
    }

    public List<Map<String, Object>> devices() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT d.id, d.code, d.station_id, s.name AS station_name,
                       d.name, d.type, d.model, d.manufacturer,
                       d.install_date, d.status, d.last_online_at
                FROM om_device d
                LEFT JOIN om_station s ON s.id = d.station_id
                ORDER BY d.station_id, d.id
                """));
    }

    public List<Map<String, Object>> alarms() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT a.id, a.code, a.station_id, s.name AS station_name,
                       a.device_id, d.code AS device_code, d.name AS device_name,
                       a.alarm_type, a.level, a.content, a.alarm_time,
                       a.status, a.work_order_id
                FROM om_alarm a
                LEFT JOIN om_station s ON s.id = a.station_id
                LEFT JOIN om_device d ON d.id = a.device_id
                ORDER BY a.id DESC
                """));
    }

    public List<Map<String, Object>> workOrders() {
        return MapUtils.camel(jdbc.queryForList("""
                SELECT w.id, w.code, w.station_id, s.name AS station_name,
                       w.device_id, d.code AS device_code,
                       w.alarm_id, w.type, w.priority, w.title, w.description,
                       w.assignee_id, u.real_name AS assignee_name,
                       w.plan_start, w.plan_end, w.actual_end, w.status, w.cost_amount
                FROM om_work_order w
                LEFT JOIN om_station s ON s.id = w.station_id
                LEFT JOIN om_device d ON d.id = w.device_id
                LEFT JOIN sys_user u ON u.id = w.assignee_id
                ORDER BY w.id DESC
                """));
    }

    public Map<String, Object> ackAlarm(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM om_alarm WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new BizException(40401, "告警不存在");
        }
        jdbc.update("UPDATE om_alarm SET status = 'ACKED' WHERE id = ? AND status = 'OPEN'", id);
        return alarm(id);
    }

    public Map<String, Object> createWorkOrder(Map<String, Object> body) {
        long alarmId = MapUtils.toLong(body.get("alarmId"), 0L);
        long assigneeId = MapUtils.toLong(body.get("assigneeId"), 9L);
        return createWorkOrderForAlarm(alarmId, assigneeId);
    }

    public Map<String, Object> createWorkOrderForAlarm(long alarmId, long assigneeId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM om_alarm WHERE id = ?", alarmId);
        if (rows.isEmpty()) {
            throw new BizException(40401, "告警不存在");
        }
        Map<String, Object> alarm = MapUtils.camel(rows.get(0));
        if (alarm.get("workOrderId") != null) {
            return workOrder(MapUtils.toLong(alarm.get("workOrderId"), 0L));
        }
        long id = idGen.next("om_work_order");
        String code = codeGen.next("WO");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp planEnd = new Timestamp(now.getTime() + 4 * 3600_000L);
        jdbc.update("""
                        INSERT INTO om_work_order
                        (id, code, station_id, device_id, alarm_id, type, priority, title,
                         description, assignee_id, plan_start, plan_end, status, cost_amount)
                        VALUES (?, ?, ?, ?, ?, 'REPAIR', ?, ?, ?, ?, ?, ?, 'ASSIGNED', 0)
                        """,
                id, code, alarm.get("stationId"), alarm.get("deviceId"), alarmId,
                alarm.get("level"), alarm.get("content"),
                "由告警自动生成：" + alarm.get("code"), assigneeId, now, planEnd);
        jdbc.update("UPDATE om_alarm SET work_order_id = ? WHERE id = ?", id, alarmId);
        return workOrder(id);
    }

    public Map<String, Object> assign(long id, Map<String, Object> body) {
        workOrder(id);
        long assigneeId = MapUtils.toLong(body.get("assigneeId"), 9L);
        jdbc.update("UPDATE om_work_order SET assignee_id = ?, status = 'ASSIGNED' WHERE id = ?", assigneeId, id);
        return workOrder(id);
    }

    public Map<String, Object> complete(long id, Map<String, Object> body) {
        Map<String, Object> order = workOrder(id);
        BigDecimal cost = MapUtils.toDecimal(body.get("costAmount"), new BigDecimal("2800"));
        String description = MapUtils.text(body, "description");
        if (description == null || description.isBlank()) {
            description = "现场处理完成，设备恢复运行";
        }
        jdbc.update("""
                        UPDATE om_work_order
                        SET status = 'COMPLETED', actual_end = ?, description = ?, cost_amount = ?
                        WHERE id = ?
                        """,
                new Timestamp(System.currentTimeMillis()), description, cost, id);
        Object alarmId = order.get("alarmId");
        if (alarmId != null) {
            jdbc.update("UPDATE om_alarm SET status = 'CLEARED', cleared_at = ? WHERE id = ?",
                    new Timestamp(System.currentTimeMillis()), alarmId);
        }
        return workOrder(id);
    }

    public Map<String, Object> close(long id) {
        workOrder(id);
        jdbc.update("UPDATE om_work_order SET status = 'CLOSED' WHERE id = ?", id);
        return workOrder(id);
    }

    public Map<String, Object> alarm(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id, a.code, a.station_id, s.name AS station_name,
                       a.device_id, d.code AS device_code, d.name AS device_name,
                       a.alarm_type, a.level, a.content, a.alarm_time,
                       a.status, a.work_order_id
                FROM om_alarm a
                LEFT JOIN om_station s ON s.id = a.station_id
                LEFT JOIN om_device d ON d.id = a.device_id
                WHERE a.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BizException(40401, "告警不存在");
        }
        return MapUtils.camel(rows.get(0));
    }

    public Map<String, Object> workOrder(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT w.id, w.code, w.station_id, s.name AS station_name,
                       w.device_id, d.code AS device_code,
                       w.alarm_id, w.type, w.priority, w.title, w.description,
                       w.assignee_id, u.real_name AS assignee_name,
                       w.plan_start, w.plan_end, w.actual_end, w.status, w.cost_amount
                FROM om_work_order w
                LEFT JOIN om_station s ON s.id = w.station_id
                LEFT JOIN om_device d ON d.id = w.device_id
                LEFT JOIN sys_user u ON u.id = w.assignee_id
                WHERE w.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BizException(40401, "工单不存在");
        }
        return MapUtils.camel(rows.get(0));
    }
}
