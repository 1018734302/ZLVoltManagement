-- ZL-Volt 演示版扩展表：本地 IoT 遥测（生产环境使用 ClickHouse）
DROP TABLE IF EXISTS iot_telemetry;

CREATE TABLE iot_telemetry (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    msg_id        VARCHAR(64),
    station_code  VARCHAR(32),
    device_code   VARCHAR(32),
    device_type   VARCHAR(32),
    ts            TIMESTAMP,
    active_power  DECIMAL(18,2),
    dc_voltage    DECIMAL(18,2),
    dc_current    DECIMAL(18,2),
    ac_voltage    DECIMAL(18,2),
    ac_current    DECIMAL(18,2),
    frequency     DECIMAL(10,3),
    temperature   DECIMAL(10,2),
    energy_today  DECIMAL(18,3),
    energy_total  DECIMAL(18,3),
    status        VARCHAR(32),
    fault_code    VARCHAR(64),
    quality       INT DEFAULT 1,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
