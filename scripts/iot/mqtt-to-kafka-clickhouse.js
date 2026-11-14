#!/usr/bin/env node
/**
 * ZL-Volt MQTT -> Kafka -> ClickHouse 桥接脚本（开发/验证用）
 *
 * 用途：
 *   在 Java 版 zl-iot 服务落地前，先把 MQTT 假设备数据写入 Kafka 和 ClickHouse，
 *   验证“设备接入 -> 消息队列 -> 时序库”链路。
 *
 * 用法：
 *   node mqtt-to-kafka-clickhouse.js \
 *     --broker mqtt://localhost:1883 \
 *     --kafka localhost:9092 \
 *     --clickhouse http://localhost:8123 \
 *     --topic iot.telemetry.raw
 *
 * 说明：
 *   - 需要完整模式中间件（Kafka + ClickHouse）；
 *   - ClickHouse 表由 db/clickhouse/01_schema.sql 初始化；
 *   - 生产环境请使用 Java zl-iot 服务替换本脚本。
 */
'use strict';

const mqtt = require('mqtt');
const { Kafka } = require('kafkajs');

function parseArgs(argv) {
  const args = {
    broker: 'mqtt://localhost:1883',
    kafka: 'localhost:9092',
    clickhouse: 'http://localhost:8123',
    topic: 'iot.telemetry.raw',
    skipKafka: false,
    username: 'simulator',
    password: 'simulator'
  };
  for (let i = 2; i < argv.length; i++) {
    const key = argv[i];
    const value = argv[i + 1];
    switch (key) {
      case '--broker': args.broker = value; i++; break;
      case '--kafka': args.kafka = value; i++; break;
      case '--clickhouse': args.clickhouse = value; i++; break;
      case '--topic': args.topic = value; i++; break;
      case '--skip-kafka': args.skipKafka = true; break;
      case '--username': args.username = value; i++; break;
      case '--password': args.password = value; i++; break;
      default: break;
    }
  }
  return args;
}

function formatDateTime64(ts) {
  const d = new Date(ts);
  const pad = (n, len = 2) => String(n).padStart(len, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`;
}

function toRow(payload) {
  const m = payload.metrics || {};
  return {
    msg_id: String(payload.msgId || ''),
    station_id: String(payload.stationCode || ''),
    device_id: String(payload.deviceCode || ''),
    device_type: String(payload.deviceType || ''),
    ts: formatDateTime64(payload.ts || Date.now()),
    active_power: Number(m.activePower || 0),
    dc_voltage: Number(m.dcVoltage || 0),
    dc_current: Number(m.dcCurrent || 0),
    ac_voltage: Number(m.acVoltage || 0),
    ac_current: Number(m.acCurrent || 0),
    frequency: Number(m.frequency || 0),
    temperature: Number(m.temperature || 0),
    energy_today: Number(m.energyToday || 0),
    energy_total: Number(m.energyTotal || 0),
    status: String(m.status || 'UNKNOWN'),
    fault_code: m.faultCode == null ? '' : String(m.faultCode),
    quality: Number(payload.quality == null ? 1 : payload.quality)
  };
}

async function main() {
  const args = parseArgs(process.argv);
  const kafkaBrokers = args.kafka.split(',').map((s) => s.trim()).filter(Boolean);
  const clickhouseUrl = args.clickhouse.replace(/\/$/, '');

  let producer = null;
  if (!args.skipKafka) {
    const kafka = new Kafka({
      clientId: 'zl-iot-bridge',
      brokers: kafkaBrokers,
      retry: { retries: 5 }
    });
    producer = kafka.producer();
    try {
      await producer.connect();
      console.log(`[bridge] kafka connected ${kafkaBrokers.join(',')}`);
    } catch (err) {
      console.error(`[bridge] kafka connect failed: ${err.message}`);
      console.error('[bridge] 可加 --skip-kafka 只验证 MQTT -> ClickHouse');
      process.exit(1);
    }
  } else {
    console.log('[bridge] kafka skipped');
  }

  const mqttClient = mqtt.connect(args.broker, {
    clientId: `bridge-${Date.now()}`,
    username: args.username,
    password: args.password,
    reconnectPeriod: 5000
  });

  let buffer = [];
  let flushing = false;
  let lastFlush = Date.now();
  let total = 0;
  const seenMsgIds = new Set();
  const MAX_SEEN = 100000;

  const flush = async () => {
    if (flushing || buffer.length === 0) return;
    flushing = true;
    const rows = buffer;
    buffer = [];
    lastFlush = Date.now();
    try {
      if (producer) {
        await producer.send({
          topic: args.topic,
          messages: rows.map((row) => ({
            key: row.device_id,
            value: JSON.stringify(row)
          }))
        });
      }
      const query = encodeURIComponent('INSERT INTO zl_analytics.iot_telemetry FORMAT JSONEachRow');
      const body = rows.map((row) => JSON.stringify(row)).join('\n');
      const resp = await fetch(`${clickhouseUrl}/?query=${query}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-ndjson' },
        body
      });
      if (!resp.ok) {
        const text = await resp.text();
        throw new Error(`ClickHouse ${resp.status}: ${text}`);
      }
      total += rows.length;
      console.log(`[bridge] flushed ${rows.length} rows (total ${total}) -> kafka + clickhouse`);
    } catch (err) {
      console.error(`[bridge] flush failed: ${err.message}`);
      // 失败数据放回缓冲区，下一轮重试
      buffer = rows.concat(buffer);
    } finally {
      flushing = false;
    }
  };

  mqttClient.on('connect', () => {
    console.log(`[bridge] mqtt connected ${args.broker}`);
    mqttClient.subscribe('pv/+/+/telemetry', { qos: 1 }, (err) => {
      if (err) console.error('[bridge] subscribe failed:', err.message);
      else console.log('[bridge] subscribed pv/+/+/telemetry');
    });
  });

  mqttClient.on('message', (topic, message) => {
    try {
      const payload = JSON.parse(message.toString());
      const msgId = String(payload.msgId || '');
      if (msgId) {
        if (seenMsgIds.has(msgId)) {
          return;
        }
        seenMsgIds.add(msgId);
        if (seenMsgIds.size > MAX_SEEN) {
          seenMsgIds.clear();
        }
      }
      buffer.push(toRow(payload));
    } catch (err) {
      console.error(`[bridge] invalid message on ${topic}: ${err.message}`);
    }
  });

  mqttClient.on('error', (err) => {
    console.error('[bridge] mqtt error:', err.message);
  });

  const timer = setInterval(() => {
    if (buffer.length > 0 || Date.now() - lastFlush > 1000) {
      flush();
    }
  }, 1000);

  const shutdown = async () => {
    console.log('\n[bridge] shutting down...');
    clearInterval(timer);
    mqttClient.end(true);
    await flush();
    if (producer) await producer.disconnect();
    setTimeout(() => process.exit(0), 500);
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

main().catch((err) => {
  console.error('[bridge] fatal:', err);
  process.exit(1);
});
