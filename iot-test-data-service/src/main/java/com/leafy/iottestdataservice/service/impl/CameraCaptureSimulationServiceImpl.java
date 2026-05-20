package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.dto.CameraCaptureManualRequest;
import com.leafy.iottestdataservice.dto.CameraCaptureQuality;
import com.leafy.iottestdataservice.dto.CameraCaptureRecurrence;
import com.leafy.iottestdataservice.dto.CameraCaptureResolution;
import com.leafy.iottestdataservice.dto.CameraCaptureScheduledRequest;
import com.leafy.iottestdataservice.dto.CameraCaptureSimulationResponse;
import com.leafy.iottestdataservice.dto.CameraImageMetadataResponse;
import com.leafy.iottestdataservice.dto.CameraTriggerType;
import com.leafy.iottestdataservice.dto.mqtt.CameraCaptureCommandPayload;
import com.leafy.iottestdataservice.dto.mqtt.ImageMetaPayload;
import com.leafy.iottestdataservice.mqtt.SeedMqttPublisher;
import com.leafy.iottestdataservice.service.CameraCaptureSimulationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraCaptureSimulationServiceImpl implements CameraCaptureSimulationService {

    private final SeedProperties seedProperties;
    private final SeedMqttPublisher seedMqttPublisher;
    private final ThreadPoolTaskScheduler seedTaskScheduler;
    private final Clock clock;
    private final Map<UUID, ScheduledCameraScenario> schedules = new ConcurrentHashMap<>();

    @Override
    public CameraCaptureSimulationResponse simulateManualCapture(CameraCaptureManualRequest request) {
        CameraCaptureManualRequest normalized = normalizeManualRequest(request);
        Instant requestedAt = Instant.now(clock);
        List<CameraImageMetadataResponse> captures = new ArrayList<>();
        for (int index = 0; index < normalized.count(); index++) {
            captures.add(publishCapture(
                normalized.deviceUid(),
                CameraTriggerType.MANUAL,
                normalized.resolution(),
                normalized.quality(),
                requestedAt.plusMillis(index)
            ));
        }
        return new CameraCaptureSimulationResponse(
            "camera-capture-manual",
            null,
            normalized.deviceUid(),
            CameraTriggerType.MANUAL,
            normalized.resolution(),
            normalized.quality(),
            null,
            requestedAt,
            null,
            captures
        );
    }

    @Override
    public CameraCaptureSimulationResponse scheduleCapture(CameraCaptureScheduledRequest request, boolean runNow) {
        CameraCaptureScheduledRequest normalized = normalizeScheduledRequest(request);
        UUID scheduleId = UUID.randomUUID();
        Instant requestedAt = Instant.now(clock);
        Instant firstRunAt = computeNextRunAt(normalized.timeOfDay(), normalized.recurrence(), requestedAt);
        ScheduledCameraScenario scenario = new ScheduledCameraScenario(
            scheduleId,
            normalized.deviceUid(),
            normalized.timeOfDay(),
            normalized.recurrence(),
            normalized.resolution(),
            normalized.quality()
        );
        schedules.put(scheduleId, scenario);
        scheduleNextRun(scenario, firstRunAt);

        List<CameraImageMetadataResponse> captures = runNow
            ? List.of(publishCapture(
                normalized.deviceUid(),
                CameraTriggerType.SCHEDULED,
                normalized.resolution(),
                normalized.quality(),
                requestedAt
            ))
            : List.of();

        return new CameraCaptureSimulationResponse(
            "camera-capture-scheduled",
            scheduleId,
            normalized.deviceUid(),
            CameraTriggerType.SCHEDULED,
            normalized.resolution(),
            normalized.quality(),
            normalized.recurrence(),
            requestedAt,
            firstRunAt,
            captures
        );
    }

    private void scheduleNextRun(ScheduledCameraScenario scenario, Instant runAt) {
        // Scheduling is intentionally in-memory because this is non-production test tooling.
        ScheduledFuture<?> future = seedTaskScheduler.schedule(() -> executeScheduledScenario(scenario.scheduleId()), runAt);
        scenario.future(future);
        scenario.nextRunAt(runAt);
        log.info(
            "Scheduled camera capture simulation. scheduleId={}, deviceUid={}, recurrence={}, nextRunAt={}",
            scenario.scheduleId(),
            scenario.deviceUid(),
            scenario.recurrence(),
            runAt
        );
    }

    private void executeScheduledScenario(UUID scheduleId) {
        ScheduledCameraScenario scenario = schedules.get(scheduleId);
        if (scenario == null) {
            return;
        }
        Instant now = Instant.now(clock);
        publishCapture(
            scenario.deviceUid(),
            CameraTriggerType.SCHEDULED,
            scenario.resolution(),
            scenario.quality(),
            now
        );
        scheduleNextRun(scenario, computeNextRunAt(scenario.timeOfDay(), scenario.recurrence(), now.plusSeconds(1)));
    }

    private CameraImageMetadataResponse publishCapture(
        String deviceUid,
        CameraTriggerType triggerType,
        CameraCaptureResolution resolution,
        CameraCaptureQuality quality,
        Instant timestamp
    ) {
        String requestId = UUID.randomUUID().toString();
        Dimensions dimensions = dimensions(resolution);
        long sizeBytes = estimateSizeBytes(resolution, quality);
        String fileId = "mock-file-" + UUID.randomUUID();

        CameraCaptureCommandPayload commandPayload = new CameraCaptureCommandPayload(
            requestId,
            deviceUid,
            triggerType.name(),
            timestamp,
            resolution.name(),
            quality.name()
        );
        ImageMetaPayload metadataPayload = new ImageMetaPayload(
            deviceUid,
            requestId,
            triggerType.name(),
            timestamp,
            timestamp,
            "SUCCESS",
            true,
            fileId,
            "image/jpeg",
            sizeBytes,
            dimensions.width(),
            dimensions.height(),
            null,
            null
        );

        seedMqttPublisher.publishCameraCaptureCommand(deviceUid, commandPayload);
        seedMqttPublisher.publishImageMeta(deviceUid, metadataPayload);
        log.info(
            "Published simulated camera capture. deviceUid={}, triggerType={}, requestId={}, fileId={}, sizeBytes={}",
            deviceUid,
            triggerType,
            requestId,
            fileId,
            sizeBytes
        );

        return new CameraImageMetadataResponse(
            requestId,
            deviceUid,
            triggerType,
            timestamp,
            sizeBytes,
            "SUCCESS",
            true,
            fileId,
            "image/jpeg",
            dimensions.width(),
            dimensions.height(),
            null,
            buildTopic(deviceUid, "camera/capture"),
            buildTopic(deviceUid, "image/meta")
        );
    }

    private CameraCaptureManualRequest normalizeManualRequest(CameraCaptureManualRequest request) {
        CameraCaptureManualRequest normalized = request != null
            ? request
            : new CameraCaptureManualRequest(null, null, null, null);
        String deviceUid = requireDeviceUid(normalized.deviceUid());
        int count = normalized.count() != null && normalized.count() > 0 ? normalized.count() : 1;
        return new CameraCaptureManualRequest(
            deviceUid,
            normalized.resolution() != null ? normalized.resolution() : CameraCaptureResolution.VGA,
            normalized.quality() != null ? normalized.quality() : CameraCaptureQuality.MEDIUM,
            count
        );
    }

    private CameraCaptureScheduledRequest normalizeScheduledRequest(CameraCaptureScheduledRequest request) {
        CameraCaptureScheduledRequest normalized = request != null
            ? request
            : new CameraCaptureScheduledRequest(null, null, null, null, null);
        return new CameraCaptureScheduledRequest(
            requireDeviceUid(normalized.deviceUid()),
            normalized.timeOfDay() != null ? normalized.timeOfDay() : LocalTime.now(clock),
            normalized.recurrence() != null ? normalized.recurrence() : CameraCaptureRecurrence.DAILY,
            normalized.resolution() != null ? normalized.resolution() : CameraCaptureResolution.VGA,
            normalized.quality() != null ? normalized.quality() : CameraCaptureQuality.MEDIUM
        );
    }

    private String requireDeviceUid(String deviceUid) {
        if (deviceUid == null || deviceUid.isBlank()) {
            throw new IllegalArgumentException("deviceUid is required.");
        }
        return deviceUid;
    }

    private Instant computeNextRunAt(LocalTime timeOfDay, CameraCaptureRecurrence recurrence, Instant referenceInstant) {
        ZoneId zoneId = clock.getZone();
        LocalDate referenceDate = LocalDateTime.ofInstant(referenceInstant, zoneId).toLocalDate();
        LocalDateTime candidate = LocalDateTime.of(referenceDate, timeOfDay);
        if (!candidate.atZone(zoneId).toInstant().isAfter(referenceInstant)) {
            candidate = switch (recurrence) {
                case DAILY -> candidate.plusDays(1);
                case WEEKLY -> candidate.plusWeeks(1);
                case MONTHLY -> candidate.plusMonths(1);
            };
        }
        return candidate.atZone(zoneId).toInstant();
    }

    private Dimensions dimensions(CameraCaptureResolution resolution) {
        return switch (resolution) {
            case QVGA -> new Dimensions(320, 240);
            case VGA -> new Dimensions(640, 480);
            case HD -> new Dimensions(1280, 720);
        };
    }

    private long estimateSizeBytes(CameraCaptureResolution resolution, CameraCaptureQuality quality) {
        Dimensions dimensions = dimensions(resolution);
        double qualityFactor = switch (quality) {
            case LOW -> 0.08d;
            case MEDIUM -> 0.14d;
            case HIGH -> 0.22d;
        };
        return Math.max(1024L, Math.round(dimensions.width() * dimensions.height() * qualityFactor));
    }

    private String buildTopic(String deviceUid, String suffix) {
        return seedProperties.getMqtt().getProduct()
            + "/"
            + seedProperties.getMqtt().getNamespaceEnv()
            + "/devices/"
            + deviceUid
            + "/"
            + suffix;
    }

    private record Dimensions(int width, int height) {
    }

    private static final class ScheduledCameraScenario {
        private final UUID scheduleId;
        private final String deviceUid;
        private final LocalTime timeOfDay;
        private final CameraCaptureRecurrence recurrence;
        private final CameraCaptureResolution resolution;
        private final CameraCaptureQuality quality;
        private volatile Instant nextRunAt;
        private volatile ScheduledFuture<?> future;

        private ScheduledCameraScenario(
            UUID scheduleId,
            String deviceUid,
            LocalTime timeOfDay,
            CameraCaptureRecurrence recurrence,
            CameraCaptureResolution resolution,
            CameraCaptureQuality quality
        ) {
            this.scheduleId = scheduleId;
            this.deviceUid = deviceUid;
            this.timeOfDay = timeOfDay;
            this.recurrence = recurrence;
            this.resolution = resolution;
            this.quality = quality;
        }

        UUID scheduleId() {
            return scheduleId;
        }

        String deviceUid() {
            return deviceUid;
        }

        LocalTime timeOfDay() {
            return timeOfDay;
        }

        CameraCaptureRecurrence recurrence() {
            return recurrence;
        }

        CameraCaptureResolution resolution() {
            return resolution;
        }

        CameraCaptureQuality quality() {
            return quality;
        }

        void nextRunAt(Instant nextRunAt) {
            this.nextRunAt = nextRunAt;
        }

        void future(ScheduledFuture<?> future) {
            if (this.future != null) {
                this.future.cancel(false);
            }
            this.future = future;
        }
    }
}
