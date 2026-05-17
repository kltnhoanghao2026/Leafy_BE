package com.leafy.iottestdataservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.dto.CameraCaptureManualRequest;
import com.leafy.iottestdataservice.dto.CameraCaptureQuality;
import com.leafy.iottestdataservice.dto.CameraCaptureRecurrence;
import com.leafy.iottestdataservice.dto.CameraCaptureResolution;
import com.leafy.iottestdataservice.dto.CameraCaptureScheduledRequest;
import com.leafy.iottestdataservice.dto.CameraTriggerType;
import com.leafy.iottestdataservice.dto.mqtt.CameraCaptureCommandPayload;
import com.leafy.iottestdataservice.dto.mqtt.ImageMetaPayload;
import com.leafy.iottestdataservice.mqtt.SeedMqttPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@ExtendWith(MockitoExtension.class)
class CameraCaptureSimulationServiceImplTest {

    @Mock
    private SeedMqttPublisher seedMqttPublisher;

    @Mock
    private ThreadPoolTaskScheduler seedTaskScheduler;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    private CameraCaptureSimulationServiceImpl service;

    @BeforeEach
    void setUp() {
        SeedProperties seedProperties = new SeedProperties();
        seedProperties.getMqtt().setProduct("coffee");
        seedProperties.getMqtt().setNamespaceEnv("prod");
        lenient().doReturn(scheduledFuture).when(seedTaskScheduler).schedule(any(Runnable.class), any(Instant.class));
        service = new CameraCaptureSimulationServiceImpl(
            seedProperties,
            seedMqttPublisher,
            seedTaskScheduler,
            Clock.fixed(Instant.parse("2026-05-15T08:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void simulateManualCapturePublishesRequestedCountAndManualTriggerType() {
        var response = service.simulateManualCapture(
            new CameraCaptureManualRequest("device-001", CameraCaptureResolution.QVGA, CameraCaptureQuality.LOW, 3)
        );

        assertThat(response.scenario()).isEqualTo("camera-capture-manual");
        assertThat(response.captures()).hasSize(3);
        assertThat(response.captures()).allSatisfy(capture -> {
            assertThat(capture.triggerType()).isEqualTo(CameraTriggerType.MANUAL);
            assertThat(capture.width()).isEqualTo(320);
            assertThat(capture.height()).isEqualTo(240);
            assertThat(capture.fileId()).startsWith("mock-file-");
        });
        verify(seedMqttPublisher, times(3)).publishCameraCaptureCommand(eq("device-001"), any(CameraCaptureCommandPayload.class));
        verify(seedMqttPublisher, times(3)).publishImageMeta(eq("device-001"), any(ImageMetaPayload.class));
    }

    @Test
    void scheduleCaptureRunNowPublishesScheduledTriggerTypeAndNextRunAt() {
        var response = service.scheduleCapture(
            new CameraCaptureScheduledRequest(
                "device-002",
                LocalTime.of(8, 30),
                CameraCaptureRecurrence.DAILY,
                CameraCaptureResolution.VGA,
                CameraCaptureQuality.MEDIUM
            ),
            true
        );

        assertThat(response.scenario()).isEqualTo("camera-capture-scheduled");
        assertThat(response.scheduleId()).isNotNull();
        assertThat(response.nextRunAt()).isEqualTo(Instant.parse("2026-05-15T08:30:00Z"));
        assertThat(response.captures()).hasSize(1);
        assertThat(response.captures().getFirst().triggerType()).isEqualTo(CameraTriggerType.SCHEDULED);

        ArgumentCaptor<CameraCaptureCommandPayload> commandCaptor = ArgumentCaptor.forClass(CameraCaptureCommandPayload.class);
        ArgumentCaptor<ImageMetaPayload> metadataCaptor = ArgumentCaptor.forClass(ImageMetaPayload.class);
        verify(seedMqttPublisher).publishCameraCaptureCommand(eq("device-002"), commandCaptor.capture());
        verify(seedMqttPublisher).publishImageMeta(eq("device-002"), metadataCaptor.capture());
        assertThat(commandCaptor.getValue().triggerType()).isEqualTo("SCHEDULED");
        assertThat(commandCaptor.getValue().resolution()).isEqualTo("VGA");
        assertThat(metadataCaptor.getValue().triggerType()).isEqualTo("SCHEDULED");
        assertThat(metadataCaptor.getValue().status()).isEqualTo("SUCCESS");
    }

    @Test
    void simulateTwoDevicesInParallelCreatesDistinctRequestIdsPerDevice() {
        var first = service.simulateManualCapture(new CameraCaptureManualRequest("device-a", CameraCaptureResolution.VGA, CameraCaptureQuality.MEDIUM, 1));
        var second = service.simulateManualCapture(new CameraCaptureManualRequest("device-b", CameraCaptureResolution.HD, CameraCaptureQuality.HIGH, 1));

        assertThat(first.captures().getFirst().requestId()).isNotEqualTo(second.captures().getFirst().requestId());
        assertThat(first.captures().getFirst().deviceUid()).isEqualTo("device-a");
        assertThat(second.captures().getFirst().deviceUid()).isEqualTo("device-b");
        verify(seedMqttPublisher).publishImageMeta(eq("device-a"), any(ImageMetaPayload.class));
        verify(seedMqttPublisher).publishImageMeta(eq("device-b"), any(ImageMetaPayload.class));
    }
}
