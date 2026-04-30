package com.leafy.iotmetricscollectorservice.integration.mqtt;

import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.leafy.iotmetricscollectorservice.service.DeviceConfigAckService;
import com.leafy.iotmetricscollectorservice.service.DeviceMediaService;
import com.leafy.iotmetricscollectorservice.service.DeviceStatusIngestService;
import com.leafy.iotmetricscollectorservice.service.TelemetryIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.support.MessageBuilder;

@ExtendWith(MockitoExtension.class)
class MqttInboundMessageHandlerTest {

    @Mock
    private TelemetryIngestService telemetryIngestService;

    @Mock
    private DeviceStatusIngestService deviceStatusIngestService;

    @Mock
    private DeviceConfigAckService deviceConfigAckService;

    @Mock
    private DeviceMediaService deviceMediaService;

    private MqttInboundMessageHandler mqttInboundMessageHandler;

    @BeforeEach
    void setUp() {
        mqttInboundMessageHandler = new MqttInboundMessageHandler(
            JsonMapper.builder().findAndAddModules().build(),
            telemetryIngestService,
            deviceStatusIngestService,
            deviceConfigAckService,
            deviceMediaService
        );
    }

    @Test
    void handleMessage_routesConfigAckToAckService() {
        mqttInboundMessageHandler.handleMessage(
            MessageBuilder.withPayload("""
                {
                  "type": "config",
                  "configVersion": 4,
                  "success": true,
                  "ts": "2026-04-10T05:00:00Z"
                }
                """)
                .setHeader(MqttHeaders.RECEIVED_TOPIC, "coffee/prod/devices/device-001/ack")
                .build()
        );

        verify(deviceConfigAckService).handleConfigAck(
            org.mockito.ArgumentMatchers.eq("device-001"),
            org.mockito.ArgumentMatchers.argThat(payload ->
                payload != null
                    && "config".equals(payload.getType())
                    && Integer.valueOf(4).equals(payload.getConfigVersion())
                    && Boolean.TRUE.equals(payload.getSuccess())
            )
        );
    }
}
