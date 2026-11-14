#!/usr/bin/env node
/**
 * ZL-Volt MQTT 假设备模拟器
 *
 * 用法：
 *   node mqtt-device-simulator.js --broker mqtt://localhost:1883 --stations 2 --devices 5 --interval 5000
 *   node mqtt-device-simulator.js --mode subscribe --broker mqtt://localhost:1883
 *
 * 说明：
 *   - 每台设备使用独立 MQTT 连接，clientId = deviceCode；
 *   - 每 5 秒上报一次遥测，白天功率按正弦曲线波动，夜间为 0；
 *   - --fault-rate 控制故障概率，故障时温度 90℃、功率 0，便于验证告警规则；
 *   - 仅用于本地开发/测试，生产设备必须使用证书或一机一密。
 */
'use strict';

const mqtt = require('mqtt');
const crypto = require('crypto');

function parseArgs(argv) {
  const args = {
    broker: 'mqtt://localhost:1883',
    stations: 2,
    devices: 5,
    interval: 5000,
    faultRate: 0.02,
    faultDuration: 720,
    mode: 'simulator',
    username: 'simulator',
    password: 'simulator'
  };
  for (let i = 2; i < argv.length; i++) {
    const key = argv[i];
    const value = argv[i + 1];
    switch (key) {
      case '--broker': args.broker = value; i++; break;
      case '--stations': args.stations = Number(value); i++; break;
      case '--devices': args.devices = Number(value); i++; break;
      case '--interval': args.interval = Number(value); i++; break;
      case '--fault-rate': args.faultRate = Number(value); i++; break;
      case '--fault-duration': args.faultDuration = Number(value); i++; break;
      case '--mode': args.mode = value; i++; break;
      case '--username': args.username = value; i++; break;
      case '--password': args.password = value; i++; break;
      case '--help':
        printHelp();
        process.exit(0);
        break;
      default:
        break;
    }
  }
  return args;
}

function printHelp() {
  console.log(`
ZL-Volt MQTT 假设备模拟器

参数：
  --broker       MQTT Broker，默认 mqtt://localhost:1883
  --stations     模拟电站数，默认 2
  --devices      每站设备数，默认 5
  --interval     上报间隔毫秒，默认 5000
  --fault-rate   故障概率 0~1，默认 0.02
  --fault-duration 单次故障持续秒数，默认 720
  --mode         simulator | subscribe，默认 simulator
  --username     MQTT 用户名，默认 simulator
  --password     MQTT 密码，默认 simulator
`);
}

function stationCode(index) {
  return `ST-2026-${String(index).padStart(4, '0')}`;
}

function buildDevices(stationCount, devicesPerStation) {
  const types = ['INVERTER', 'INVERTER', 'METER', 'COMBINER_BOX', 'WEATHER_STATION'];
  const devices = [];
  for (let s = 1; s <= stationCount; s++) {
    const base = s === 1 ? 0 : (s - 1) * 100;
    for (let d = 1; d <= devicesPerStation; d++) {
      const type = d <= types.length ? types[d - 1] : 'INVERTER';
      let code;
      if (type === 'INVERTER') {
        code = `INV-${String(base + (d <= 2 ? d : d)).padStart(4, '0')}`;
      } else if (type === 'METER') {
        code = `METER-${String(base + 1).padStart(4, '0')}`;
      } else if (type === 'COMBINER_BOX') {
        code = `CMB-${String(base + 1).padStart(4, '0')}`;
      } else {
        code = `WS-${String(base + 1).padStart(4, '0')}`;
      }
      devices.push({
        stationCode: stationCode(s),
        code,
        type,
        capacity: type === 'INVERTER' ? (d % 2 === 0 ? 110 : 100) : 100,
        energyToday: Math.random() * 300,
        energyTotal: 1200000 + Math.random() * 50000,
        faultUntil: 0
      });
    }
  }
  return devices;
}

function noise(scale) {
  return (Math.random() - 0.5) * 2 * scale;
}

function buildTelemetry(device, options) {
  const now = new Date();
  const hour = now.getHours() + now.getMinutes() / 60;
  const daylight = Math.max(0, Math.sin(((hour - 6) / 12) * Math.PI));
  const isInverter = device.type === 'INVERTER';
  const isMeter = device.type === 'METER';
  const isWeather = device.type === 'WEATHER_STATION';
  let fault = false;
  if (isInverter) {
    if (device.faultUntil && Date.now() < device.faultUntil) {
      fault = true;
    } else if (Math.random() < options.faultRate) {
      device.faultUntil = Date.now() + options.faultDuration * 1000;
      fault = true;
    }
  }

  let activePower = 0;
  if (!isWeather) {
    activePower = Math.max(0, device.capacity * daylight * (0.82 + noise(0.08)));
  }
  if (fault) activePower = 0;

  const dcVoltage = fault ? 0 : (isInverter ? Math.max(0, 720 + noise(20)) : 0);
  const dcCurrent = fault ? 0 : (isInverter ? activePower * 1000 / Math.max(dcVoltage, 1) : 0);
  const acVoltage = isWeather ? 0 : (fault ? 0 : 400 + noise(3));
  const acCurrent = (!isWeather && acVoltage > 0) ? activePower * 1000 / (acVoltage * 1.732) : 0;
  const frequency = isWeather ? 0 : (fault ? 0 : 50 + noise(0.05));
  const temperature = fault ? 90 + noise(2) : 28 + daylight * 18 + noise(3);
  const energyToday = device.energyToday + activePower * (options.interval / 3600000);
  device.energyToday = energyToday;
  const energyTotal = device.energyTotal + activePower * (options.interval / 3600000);

  return {
    msgId: crypto.randomUUID(),
    stationCode: device.stationCode,
    deviceCode: device.code,
    deviceType: device.type,
    ts: Date.now(),
    metrics: {
      activePower: round(activePower, 2),
      dcVoltage: round(dcVoltage, 2),
      dcCurrent: round(dcCurrent, 2),
      acVoltage: round(acVoltage, 2),
      acCurrent: round(acCurrent, 2),
      frequency: round(frequency, 3),
      temperature: round(temperature, 2),
      energyToday: round(energyToday, 3),
      energyTotal: round(energyTotal, 3),
      status: fault ? 'FAULT' : (isWeather ? 'RUNNING' : (activePower > 0 ? 'RUNNING' : 'STANDBY')),
      faultCode: fault ? 'E-TEMP-001' : null
    },
    quality: 1
  };
}

function round(value, digits) {
  const factor = Math.pow(10, digits);
  return Math.round(value * factor) / factor;
}

function topicOf(device, suffix) {
  return `pv/${device.stationCode}/${device.code}/${suffix}`;
}

function runSimulator(options) {
  const devices = buildDevices(options.stations, options.devices);
  console.log(`[simulator] broker=${options.broker} stations=${options.stations} devicesPerStation=${options.devices} total=${devices.length} interval=${options.interval}ms`);

  const clients = [];
  const timers = [];

  devices.forEach((device) => {
    const client = mqtt.connect(options.broker, {
      clientId: device.code,
      username: device.code,
      password: options.password,
      reconnectPeriod: 5000,
      connectTimeout: 10000,
      clean: true
    });
    clients.push(client);
    let deviceTimer = null;

    client.on('connect', () => {
      console.log(`[connected] ${device.stationCode}/${device.code} (${device.type})`);
      if (deviceTimer) {
        clearInterval(deviceTimer);
        deviceTimer = null;
      }
      const publish = () => {
        const payload = buildTelemetry(device, options);
        const topic = topicOf(device, 'telemetry');
        client.publish(topic, JSON.stringify(payload), { qos: 1 }, (err) => {
          if (err) {
            console.error(`[publish-error] ${topic}`, err.message);
          } else {
            console.log(`[${device.stationCode}/${device.code}] power=${payload.metrics.activePower}kW temp=${payload.metrics.temperature}℃ status=${payload.metrics.status}`);
          }
        });
        if (payload.metrics.status === 'FAULT') {
          client.publish(topicOf(device, 'event'), JSON.stringify({
            msgId: crypto.randomUUID(),
            stationCode: device.stationCode,
            deviceCode: device.code,
            ts: payload.ts,
            eventType: 'FAULT',
            faultCode: payload.metrics.faultCode,
            description: '模拟逆变器高温故障'
          }), { qos: 1 });
        }
      };
      publish();
      deviceTimer = setInterval(publish, options.interval);
      timers.push(deviceTimer);
    });

    client.on('error', (err) => {
      console.error(`[mqtt-error] ${device.code}: ${err.message}`);
    });
  });

  const shutdown = () => {
    console.log('\n[simulator] shutting down...');
    timers.forEach(clearInterval);
    clients.forEach((client) => client.end(true));
    setTimeout(() => process.exit(0), 500);
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

function runSubscriber(options) {
  const client = mqtt.connect(options.broker, {
    clientId: `sim-sub-${Date.now()}`,
    username: options.username,
    password: options.password,
    reconnectPeriod: 5000
  });
  client.on('connect', () => {
    console.log(`[subscriber] connected ${options.broker}`);
    client.subscribe('pv/+/+/telemetry', { qos: 1 }, (err) => {
      if (err) console.error('[subscribe-error]', err.message);
      else console.log('[subscriber] subscribed pv/+/+/telemetry');
    });
  });
  client.on('message', (topic, buffer) => {
    console.log(`[topic] ${topic}`);
    console.log(buffer.toString());
  });
  client.on('error', (err) => console.error('[mqtt-error]', err.message));
  process.on('SIGINT', () => {
    client.end(true, () => process.exit(0));
  });
}

const options = parseArgs(process.argv);
if (options.mode === 'subscribe') {
  runSubscriber(options);
} else {
  runSimulator(options);
}
