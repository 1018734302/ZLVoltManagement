package com.zlvolt.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlvolt.common.BizException;
import com.zlvolt.common.CodeGen;
import com.zlvolt.common.IdGen;
import com.zlvolt.common.MapUtils;
import com.zlvolt.om.OmService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IotService {

    private final JdbcTemplate jdbc;
    private final IdGen idGen;
    private final CodeGen codeGen;
    private final OmService omService;
    private final ObjectMapper objectMapper;

    @Value("${zl.mqtt.enabled:true}")
    private boolean mqttEnabled;

    @Value("${zl.mqtt.port:1883}")
    private int mqttPort;

    public IotService(JdbcTemplate jdbc, IdGen idGen, CodeGen codeGen,
                      OmService omService, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.idGen = idGen;
        this.codeGen = codeGen;
        this.omService = omService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> ingest(Map<String, Object> payload) {
        String stationCode = MapUtils.text(payload, "stationCode");
        String deviceCode = MapUtils.text(payload, "deviceCode");
        Object metricsObj = payload.get("metrics");
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = metricsObj instanceof Map<?, ?> map
                ? (Map<String, Object>) map : payload;
        long tsMillis = MapUtils.toLong(payload.get("ts"), System.currentTimeMillis());
        Timestamp ts = new Timestamp(tsMillis);

        List<Map<String, Object>> devices = deviceByCode(deviceCode);
        Map<String, Object> device = devices.isEmpty() ? null : MapUtils.camel(devices.get(0));
        if (stationCode == null && device != null) {
            stationCode = MapUtils.str(device, "stationCode");
        }
        String deviceType = MapUtils.text(payload, "deviceType");
        if (deviceType == null && device != null) {
            deviceType = MapUtils.str(device, "type");
        }
        String status = MapUtils.text(metrics, "status");
        String msgId = MapUtils.text(payload, "msgId");
        if (msgId == null || msgId.isBlank()) {
            msgId = UUID.randomUUID().toString();
        }
        jdbc.update("""
                        INSERT INTO iot_telemetry
                        (msg_id, station_code, device_code, device_type, ts,
                         active_power, dc_voltage, dc_current, ac_voltage, ac_current,
                         frequency, temperature, energy_today, energy_total, status, fault_code, quality)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                msgId, stationCode, deviceCode, deviceType, ts,
                decimal(metrics, "activePower"), decimal(metrics, "dcVoltage"),
                decimal(metrics, "dcCurrent"), decimal(metrics, "acVoltage"),
                decimal(metrics, "acCurrent"), decimal(metrics, "frequency"),
                decimal(metrics, "temperature"), decimal(metrics, "energyToday"),
                decimal(metrics, "energyTotal"), status,
                MapUtils.text(metrics, "faultCode"),
                MapUtils.toLong(payload.get("quality"), 1L));
        Long telemetryId = jdbc.queryForObject("SELECT MAX(id) FROM iot_telemetry", Long.class);

        if (device != null) {
            String deviceStatus = "FAULT".equals(status) ? "FAULT" : "ONLINE";
            jdbc.update("UPDATE om_device SET status = ?, last_online_at = ? WHERE id = ?",
                    deviceStatus, ts, device.get("id"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("telemetryId", telemetryId);
        result.put("stationCode", stationCode);
        result.put("deviceCode", deviceCode);
        result.put("status", status);
        if (device == null) {
            result.put("message", "设备未登记，遥测已保存，未触发告警");
            return result;
        }
        BigDecimal temperature = decimal(metrics, "temperature");
        Map<String, Object> alarmResult = null;
        if ("FAULT".equals(status)) {
            alarmResult = raiseAlarm(device, "DEVICE_FAULT", "CRITICAL",
                    device.get("name") + " 故障停机");
        } else if (temperature.compareTo(new BigDecimal("85")) > 0) {
            alarmResult = raiseAlarm(device, "TEMPERATURE_HIGH", "MAJOR",
                    device.get("name") + " 温度过高：" + temperature.setScale(2, java.math.RoundingMode.HALF_UP) + "℃");
        }
        if (alarmResult != null) {
            result.put("alarm", alarmResult.get("alarm"));
            result.put("workOrder", alarmResult.get("workOrder"));
        }
        return result;
    }

    public Map<String, Object> simulate(String mode) {
        return simulate(mode, null);
    }

    public Map<String, Object> simulate(String mode, Long stationId) {
        String sql = """
                SELECT d.id, d.code, d.name, d.type, d.station_id, s.code AS station_code
                FROM om_device d
                JOIN om_station s ON s.id = d.station_id
                WHERE d.type = 'INVERTER'
                """ + (stationId == null ? "" : " AND d.station_id = ? ") + """
                ORDER BY d.id
                LIMIT 1
                """;
        List<Map<String, Object>> devices = stationId == null
                ? jdbc.queryForList(sql)
                : jdbc.queryForList(sql, stationId);
        if (devices.isEmpty()) {
            throw new BizException("没有可模拟的逆变器设备");
        }
        Map<String, Object> device = MapUtils.camel(devices.get(0));
        boolean fault = "fault".equalsIgnoreCase(mode) || "CRITICAL".equalsIgnoreCase(mode);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("activePower", fault ? 0 : 98.6);
        metrics.put("dcVoltage", fault ? 0 : 720.5);
        metrics.put("dcCurrent", fault ? 0 : 138.2);
        metrics.put("acVoltage", fault ? 0 : 400.2);
        metrics.put("acCurrent", fault ? 0 : 142.1);
        metrics.put("frequency", fault ? 0 : 50.01);
        metrics.put("temperature", fault ? 90.0 : 46.8);
        metrics.put("energyToday", 512.3);
        metrics.put("energyTotal", 1287640.5);
        metrics.put("status", fault ? "FAULT" : "RUNNING");
        metrics.put("faultCode", fault ? "E-TEMP-001" : null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msgId", UUID.randomUUID().toString());
        payload.put("stationCode", device.get("stationCode"));
        payload.put("deviceCode", device.get("code"));
        payload.put("deviceType", device.get("type"));
        payload.put("ts", System.currentTimeMillis());
        payload.put("metrics", metrics);
        payload.put("quality", 1);
        return MapUtils.map("payload", payload, "result", ingest(payload));
    }

    public List<Map<String, Object>> telemetry(String stationCode, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String sql = stationCode == null || stationCode.isBlank()
                ? "SELECT * FROM iot_telemetry ORDER BY id DESC LIMIT ?"
                : "SELECT * FROM iot_telemetry WHERE station_code = ? ORDER BY id DESC LIMIT ?";
        List<Map<String, Object>> rows = stationCode == null || stationCode.isBlank()
                ? jdbc.queryForList(sql, safeLimit)
                : jdbc.queryForList(sql, stationCode, safeLimit);
        return MapUtils.camel(rows);
    }

    public Map<String, Object> status() {
        Integer devices = jdbc.queryForObject("SELECT COUNT(*) FROM om_device", Integer.class);
        Integer telemetry = jdbc.queryForObject("SELECT COUNT(*) FROM iot_telemetry", Integer.class);
        Integer openAlarms = jdbc.queryForObject(
                "SELECT COUNT(*) FROM om_alarm WHERE status = 'OPEN'", Integer.class);
        return MapUtils.map(
                "mqttEnabled", mqttEnabled,
                "mqttPort", mqttPort,
                "deviceCount", devices,
                "telemetryCount", telemetry,
                "openAlarmCount", openAlarms);
    }

    public void ingestFromMqtt(String topic, byte[] payloadBytes) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(payloadBytes, Map.class);
            String[] parts = topic.split("/");
            if (parts.length >= 3) {
                payload.putIfAbsent("stationCode", parts[1]);
                payload.putIfAbsent("deviceCode", parts[2]);
            }
            ingest(payload);
        } catch (Exception e) {
            throw new IllegalStateException("MQTT 消息处理失败: " + topic, e);
        }
    }

    private List<Map<String, Object>> deviceByCode(String deviceCode) {
        if (deviceCode == null) {
            return List.of();
        }
        return jdbc.queryForList("""
                SELECT d.id, d.code, d.name, d.type, d.station_id, s.code AS station_code
                FROM om_device d
                JOIN om_station s ON s.id = d.station_id
                WHERE d.code = ?
                """, deviceCode);
    }

    private Map<String, Object> raiseAlarm(Map<String, Object> device, String alarmType,
                                           String level, String content) {
        long deviceId = MapUtils.toLong(device.get("id"), 0L);
        Timestamp threshold = new Timestamp(System.currentTimeMillis() - 30 * 60_000L);
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT id FROM om_alarm
                WHERE device_id = ? AND alarm_type = ? AND status IN ('OPEN', 'ACKED')
                  AND alarm_time >= ?
                """, deviceId, alarmType, threshold);
        if (!existing.isEmpty()) {
            long alarmId = MapUtils.toLong(existing.get(0).get("id"), 0L);
            return MapUtils.map("alarm", omService.alarm(alarmId), "workOrder", null);
        }
        long id = idGen.next("om_alarm");
        String code = codeGen.next("ALM");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        jdbc.update("""
                        INSERT INTO om_alarm
                        (id, code, station_id, device_id, alarm_type, level, content, alarm_time, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN')
                        """,
                id, code, device.get("stationId"), deviceId, alarmType, level, content, now);
        Map<String, Object> workOrder = omService.createWorkOrderForAlarm(id, 9L);
        Map<String, Object> alarm = omService.alarm(id);
        return MapUtils.map("alarm", alarm, "workOrder", workOrder);
    }

    private BigDecimal decimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(String.valueOf(value));
    }
}
