package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.dto.HistorySeedRequest;
import com.leafy.iottestdataservice.dto.HistorySeedResponse;
import com.leafy.iottestdataservice.dto.mqtt.StatusPayload;
import com.leafy.iottestdataservice.dto.mqtt.TelemetryPayload;
import com.leafy.iottestdataservice.mqtt.SeedMqttPublisher;
import com.leafy.iottestdataservice.scenario.TelemetryScenarioGenerator;
import com.leafy.iottestdataservice.service.CollectorInventoryService;
import com.leafy.iottestdataservice.service.HistoricalTelemetrySeedService;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoricalTelemetrySeedServiceImpl implements HistoricalTelemetrySeedService {

    private static final long HISTORY_STATUS_INTERVAL_HOURS = 6;

    private final SeedProperties seedProperties;
    private final CollectorInventoryService collectorInventoryService;
    private final SeedMqttPublisher seedMqttPublisher;
    private final TelemetryScenarioGenerator telemetryScenarioGenerator;

    @Override
    public HistorySeedResponse seedLast7Days(HistorySeedRequest request) {
        return seedHistory(request, 7);
    }

    @Override
    public HistorySeedResponse seedLast30Days(HistorySeedRequest request) {
        return seedHistory(request, 30);
    }

    private HistorySeedResponse seedHistory(HistorySeedRequest request, int defaultDays) {
        HistorySeedRequest normalizedRequest = request != null ? request : new HistorySeedRequest(null, null, null, null, null, null);
        int days = normalizedRequest.days() != null && normalizedRequest.days() > 0 ? normalizedRequest.days() : defaultDays;
        int readingsPerHour = normalizedRequest.readingsPerHour() != null && normalizedRequest.readingsPerHour() > 0
            ? normalizedRequest.readingsPerHour()
            : seedProperties.getScenario().getDefaultReadingsPerHour();
        boolean includeAnomalies = normalizedRequest.includeAnomalies() == null || normalizedRequest.includeAnomalies();

        List<CollectorDeviceResponse> devices = collectorInventoryService.findDevices(
            normalizedRequest.userId(),
            normalizedRequest.farmPlotId(),
            normalizedRequest.zoneId()
        );

        List<String> warnings = new ArrayList<>();
        if (devices.isEmpty()) {
            warnings.add("No claimed devices were found for the requested history seed scope.");
            Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
            return new HistorySeedResponse(0, 0, 0, 0, now.minus(days, ChronoUnit.DAYS), now, List.copyOf(warnings));
        }

        Instant to = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant from = to.minus(days, ChronoUnit.DAYS);
        long stepSeconds = Math.max(60L, 3600L / Math.max(1, readingsPerHour));
        long telemetryMessagesPublished = 0;
        long statusMessagesPublished = 0;
        long anomaliesInjectedCount = 0;

        for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
            CollectorDeviceResponse device = devices.get(deviceIndex);
            try {
                long deviceUptime = 86_400L;
                for (Instant timestamp = from; !timestamp.isAfter(to); timestamp = timestamp.plusSeconds(stepSeconds)) {
                    GeneratedMetrics generatedMetrics = resolveMetrics(device.deviceUid(), timestamp, includeAnomalies, deviceIndex);
                    seedMqttPublisher.publishTelemetry(
                        device.deviceUid(),
                        new TelemetryPayload(timestamp, generatedMetrics.metrics(), 88, -60 + deviceIndex, "seed-history-1.0")
                    );
                    telemetryMessagesPublished++;
                    if (generatedMetrics.anomalyInjected()) {
                        anomaliesInjectedCount++;
                    }

                    if (shouldPublishStatus(timestamp, stepSeconds)) {
                        seedMqttPublisher.publishStatus(
                            device.deviceUid(),
                            new StatusPayload(timestamp, true, "192.168.10." + (deviceIndex + 10), "LeafySeed", -58 + deviceIndex, deviceUptime)
                        );
                        statusMessagesPublished++;
                        deviceUptime += HISTORY_STATUS_INTERVAL_HOURS * 3600L;
                    }
                }
            } catch (Exception exception) {
                warnings.add("Failed to seed history for device " + device.deviceUid() + ": " + exception.getMessage());
                log.warn("Failed to seed history for device {}", device.deviceUid(), exception);
            }
        }

        return new HistorySeedResponse(
            devices.size(),
            telemetryMessagesPublished,
            statusMessagesPublished,
            anomaliesInjectedCount,
            from,
            to,
            List.copyOf(warnings)
        );
    }

    private GeneratedMetrics resolveMetrics(String deviceUid, Instant timestamp, boolean includeAnomalies, int deviceIndex) {
        if (!includeAnomalies) {
            return new GeneratedMetrics(telemetryScenarioGenerator.baselineMetrics(deviceUid, timestamp), false);
        }

        int dayOfMonth = timestamp.atZone(ZoneOffset.UTC).getDayOfMonth();
        int hour = timestamp.atZone(ZoneOffset.UTC).getHour();

        if (deviceIndex % 3 == 0 && hour == 14 && dayOfMonth % 5 == 0) {
            return new GeneratedMetrics(telemetryScenarioGenerator.highTemperature(deviceUid, timestamp, 41d), true);
        }
        if (deviceIndex % 3 == 1 && hour == 6 && dayOfMonth % 4 == 0) {
            return new GeneratedMetrics(telemetryScenarioGenerator.lowSoilMoisture(deviceUid, timestamp, 19d), true);
        }
        if (deviceIndex % 3 == 2 && hour == 4 && dayOfMonth % 6 == 0) {
            return new GeneratedMetrics(telemetryScenarioGenerator.highHumidity(deviceUid, timestamp, 91d), true);
        }
        return new GeneratedMetrics(telemetryScenarioGenerator.baselineMetrics(deviceUid, timestamp), false);
    }

    private boolean shouldPublishStatus(Instant timestamp, long stepSeconds) {
        long intervalSeconds = HISTORY_STATUS_INTERVAL_HOURS * 3600L;
        return Math.floorMod(timestamp.getEpochSecond(), intervalSeconds) < stepSeconds;
    }

    private record GeneratedMetrics(java.util.Map<String, Double> metrics, boolean anomalyInjected) {
    }
}
