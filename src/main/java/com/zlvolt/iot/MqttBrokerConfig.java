package com.zlvolt.iot;

import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.interception.AbstractInterceptHandler;
import io.moquette.interception.messages.InterceptPublishMessage;
import io.netty.buffer.ByteBuf;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;

/**
 * 演示版内置 MQTT Broker（Moquette），默认监听 1883。
 * 这样不装 Docker/EMQX 也能用 Node 模拟器验证设备接入。
 * 生产环境替换为 EMQX 集群 + Kafka 桥接。
 */
@Component
public class MqttBrokerConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttBrokerConfig.class);

    private final IotService iotService;

    @Value("${zl.mqtt.enabled:true}")
    private boolean enabled;

    @Value("${zl.mqtt.port:1883}")
    private int port;

    private Server server;

    public MqttBrokerConfig(IotService iotService) {
        this.iotService = iotService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            log.info("Embedded MQTT broker disabled");
            return;
        }
        try {
            Properties props = new Properties();
            props.setProperty(IConfig.PORT_PROPERTY_NAME, String.valueOf(port));
            props.setProperty(IConfig.HOST_PROPERTY_NAME, "0.0.0.0");
            props.setProperty(IConfig.ALLOW_ANONYMOUS_PROPERTY_NAME, "true");
            props.setProperty(IConfig.PERSISTENCE_ENABLED_PROPERTY_NAME, "false");
            props.setProperty(IConfig.ENABLE_TELEMETRY_NAME, "false");
            server = new Server();
            server.startServer(new MemoryConfig(props), List.of(new AbstractInterceptHandler() {
                @Override
                public String getID() {
                    return "zl-volt-iot-handler";
                }

                @Override
                public void onSessionLoopError(Throwable error) {
                    log.warn("MQTT session loop error: {}", error.getMessage());
                }

                @Override
                public void onPublish(InterceptPublishMessage msg) {
                    String topic = msg.getTopicName();
                    if (topic == null || !topic.endsWith("/telemetry")) {
                        return;
                    }
                    ByteBuf buffer = msg.getPayload();
                    byte[] bytes = new byte[buffer.readableBytes()];
                    buffer.getBytes(buffer.readerIndex(), bytes);
                    try {
                        iotService.ingestFromMqtt(topic, bytes);
                    } catch (Exception e) {
                        log.warn("MQTT telemetry handling failed, topic={}, error={}", topic, e.getMessage());
                    }
                }
            }));
            log.info("Embedded MQTT broker started on 0.0.0.0:{}", port);
        } catch (Exception e) {
            log.warn("Embedded MQTT broker failed to start on port {}: {}", port, e.getMessage());
            log.warn("MQTT 验证可改用 POST /api/iot/telemetry 或 POST /api/iot/simulate");
        }
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            try {
                server.stopServer();
                log.info("Embedded MQTT broker stopped");
            } catch (Exception e) {
                log.warn("Stop MQTT broker failed: {}", e.getMessage());
            }
        }
    }
}
