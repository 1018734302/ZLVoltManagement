-- ============================================================
-- ZL-Volt ClickHouse 时序库
-- 用途：IoT 遥测原始数据、5 分钟聚合、日发电量
-- 说明：
--   1. 原始数据保留 365 天；
--   2. 5 分钟/日聚合由物化视图自动生成；
--   3. 查询接口默认限制 31 天，超范围走聚合表。
-- ============================================================

CREATE DATABASE IF NOT EXISTS zl_analytics;

DROP TABLE IF EXISTS zl_analytics.iot_telemetry;
DROP TABLE IF EXISTS zl_analytics.iot_telemetry_5m;
DROP TABLE IF EXISTS zl_analytics.iot_energy_daily;

CREATE TABLE zl_analytics.iot_telemetry
(
    msg_id        String,
    station_id    String,
    device_id     String,
    device_type   String,
    ts            DateTime64(3),
    active_power  Float64,
    dc_voltage    Float64,
    dc_current    Float64,
    ac_voltage    Float64,
    ac_current    Float64,
    frequency     Float64,
    temperature   Float64,
    energy_today  Float64,
    energy_total  Float64,
    status        String,
    fault_code    String,
    quality       UInt8,
    received_at   DateTime DEFAULT now()
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(ts)
ORDER BY (station_id, device_id, ts)
TTL ts + INTERVAL 365 DAY
SETTINGS index_granularity = 8192;

CREATE TABLE zl_analytics.iot_telemetry_5m
(
    station_id      String,
    device_id       String,
    window_start    DateTime,
    avg_power       Float64,
    max_temperature Float64,
    energy_delta    Float64,
    sample_count    UInt32
)
ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMM(window_start)
ORDER BY (station_id, device_id, window_start)
TTL window_start + INTERVAL 1825 DAY;

CREATE MATERIALIZED VIEW zl_analytics.mv_iot_telemetry_5m
TO zl_analytics.iot_telemetry_5m
AS
SELECT
    station_id,
    device_id,
    toStartOfFiveMinute(ts) AS window_start,
    avg(active_power) AS avg_power,
    max(temperature) AS max_temperature,
    max(energy_today) - min(energy_today) AS energy_delta,
    count() AS sample_count
FROM zl_analytics.iot_telemetry
GROUP BY station_id, device_id, window_start;

CREATE TABLE zl_analytics.iot_energy_daily
(
    station_id   String,
    device_id    String,
    day          Date,
    energy_delta Float64,
    max_power    Float64,
    sample_count UInt32
)
ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMM(day)
ORDER BY (station_id, device_id, day);

CREATE MATERIALIZED VIEW zl_analytics.mv_iot_energy_daily
TO zl_analytics.iot_energy_daily
AS
SELECT
    station_id,
    device_id,
    toDate(ts) AS day,
    max(energy_today) - min(energy_today) AS energy_delta,
    max(active_power) AS max_power,
    count() AS sample_count
FROM zl_analytics.iot_telemetry
GROUP BY station_id, device_id, day;
