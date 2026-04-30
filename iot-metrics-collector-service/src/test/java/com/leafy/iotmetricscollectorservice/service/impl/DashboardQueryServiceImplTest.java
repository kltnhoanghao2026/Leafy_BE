package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.dto.dashboard.DashboardOverviewResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.DeviceDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.ZoneOverviewResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.mapper.DashboardQueryMapper;
import com.leafy.iotmetricscollectorservice.model.DeviceConfig;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.aggregate.SensorLatestReading;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceStatus;
import com.leafy.iotmetricscollectorservice.model.enums.ProvisioningStatus;
import com.leafy.iotmetricscollectorservice.model.enums.ReadingQualityStatus;
import com.leafy.iotmetricscollectorservice.model.ref.FarmPlotRef;
import com.leafy.iotmetricscollectorservice.model.ref.FileRef;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import com.leafy.iotmetricscollectorservice.repository.AlertEventRepository;
import com.leafy.iotmetricscollectorservice.repository.DeviceConfigRepository;
import com.leafy.iotmetricscollectorservice.repository.DeviceMediaEventRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorLatestReadingRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardQueryServiceImplTest {

    @Mock
    private IoTDeviceRepository ioTDeviceRepository;

    @Mock
    private SensorLatestReadingRepository sensorLatestReadingRepository;

    @Mock
    private AlertEventRepository alertEventRepository;

    @Mock
    private DeviceMediaEventRepository deviceMediaEventRepository;

    @Mock
    private DeviceConfigRepository deviceConfigRepository;

    @Spy
    private DashboardQueryMapper dashboardQueryMapper;

    @InjectMocks
    private DashboardQueryServiceImpl dashboardQueryService;

    @Test
    void getZoneOverview_returnsSortedReadingsAlertCountAndLastUpdatedAt() {
        String zoneId = UUID.randomUUID().toString();
        SensorLatestReading temperature = createLatestReading("soilTemp", "Soil Temperature", "C", 27.5d, "2026-04-10T04:00:00Z");
        SensorLatestReading humidity = createLatestReading("humidity", "Humidity", "%", 65d, "2026-04-10T05:00:00Z");
        DeviceMediaEvent mediaEvent = createMediaEvent(zoneId, UUID.randomUUID());

        when(sensorLatestReadingRepository.findAllByZoneId(zoneId)).thenReturn(List.of(temperature, humidity));
        when(alertEventRepository.countByZoneIdAndStatus(zoneId, AlertStatus.OPEN))
            .thenReturn(2L);
        when(alertEventRepository.countByZoneIdAndStatusAndSeverity(zoneId, AlertStatus.OPEN, AlertSeverity.HIGH))
            .thenReturn(1L);
        when(alertEventRepository.countByZoneIdAndStatusAndSeverity(zoneId, AlertStatus.OPEN, AlertSeverity.CRITICAL))
            .thenReturn(1L);
        when(alertEventRepository.findMaxOpenedAtByZoneIdAndStatus(zoneId, AlertStatus.OPEN))
            .thenReturn(Instant.parse("2026-04-10T05:30:00Z"));
        when(deviceMediaEventRepository.findTopByZoneIdOrderByCapturedAtDesc(zoneId)).thenReturn(Optional.of(mediaEvent));

        ZoneOverviewResponse response = dashboardQueryService.getZoneOverview(zoneId);

        assertEquals(zoneId, response.getZoneId());
        assertEquals(2, response.getOpenAlerts());
        assertEquals(Instant.parse("2026-04-10T05:00:00Z"), response.getLastUpdatedAt());
        assertEquals(2, response.getAlertSummary().getOpenAlerts());
        assertEquals(1, response.getAlertSummary().getHighSeverityAlerts());
        assertEquals(1, response.getAlertSummary().getCriticalAlerts());
        assertEquals(Instant.parse("2026-04-10T05:30:00Z"), response.getAlertSummary().getLatestAlertAt());
        assertEquals(mediaEvent.getId(), response.getLatestMedia().getMediaEventId());
        assertEquals(2, response.getLatestReadings().size());
        assertEquals("humidity", response.getLatestReadings().get(0).getSensorCode());
        assertEquals("soilTemp", response.getLatestReadings().get(1).getSensorCode());
    }

    @Test
    void getZoneOverview_returnsEmptyMonitoringDataWhenNoReadingsExist() {
        String zoneId = UUID.randomUUID().toString();
        when(sensorLatestReadingRepository.findAllByZoneId(zoneId)).thenReturn(List.of());
        when(alertEventRepository.countByZoneIdAndStatus(zoneId, AlertStatus.OPEN))
            .thenReturn(0L);
        when(alertEventRepository.countByZoneIdAndStatusAndSeverity(zoneId, AlertStatus.OPEN, AlertSeverity.HIGH))
            .thenReturn(0L);
        when(alertEventRepository.countByZoneIdAndStatusAndSeverity(zoneId, AlertStatus.OPEN, AlertSeverity.CRITICAL))
            .thenReturn(0L);
        when(alertEventRepository.findMaxOpenedAtByZoneIdAndStatus(zoneId, AlertStatus.OPEN)).thenReturn(null);
        when(deviceMediaEventRepository.findTopByZoneIdOrderByCapturedAtDesc(zoneId)).thenReturn(Optional.empty());

        ZoneOverviewResponse response = dashboardQueryService.getZoneOverview(zoneId);

        assertEquals(zoneId, response.getZoneId());
        assertEquals(0, response.getOpenAlerts());
        assertNull(response.getLastUpdatedAt());
        assertEquals(0, response.getAlertSummary().getOpenAlerts());
        assertEquals(0, response.getAlertSummary().getHighSeverityAlerts());
        assertEquals(0, response.getAlertSummary().getCriticalAlerts());
        assertNull(response.getAlertSummary().getLatestAlertAt());
        assertNull(response.getLatestMedia());
        assertEquals(List.of(), response.getLatestReadings());
    }

    @Test
    void getFarmOverview_returnsDashboardCounts() {
        String farmPlotId = UUID.randomUUID().toString();
        Instant lastSeenAt = Instant.parse("2026-04-10T06:00:00Z");

        when(ioTDeviceRepository.countByFarmPlotId(farmPlotId)).thenReturn(8L);
        when(ioTDeviceRepository.countByFarmPlotIdAndStatus(farmPlotId, DeviceStatus.ONLINE)).thenReturn(5L);
        when(ioTDeviceRepository.countByFarmPlotIdAndStatus(farmPlotId, DeviceStatus.OFFLINE)).thenReturn(2L);
        when(ioTDeviceRepository.countDistinctZoneIdsByFarmPlotId(farmPlotId)).thenReturn(3L);
        when(alertEventRepository.countByFarmPlotIdAndStatus(farmPlotId, AlertStatus.OPEN))
            .thenReturn(4L);
        when(ioTDeviceRepository.findMaxLastSeenAtByFarmPlotId(farmPlotId)).thenReturn(lastSeenAt);

        DashboardOverviewResponse response = dashboardQueryService.getFarmOverview(farmPlotId);

        assertEquals(farmPlotId, response.getFarmPlotId());
        assertEquals(8L, response.getTotalDevices());
        assertEquals(5L, response.getOnlineDevices());
        assertEquals(2L, response.getOfflineDevices());
        assertEquals(3L, response.getTotalZones());
        assertEquals(4L, response.getOpenAlerts());
        assertEquals(lastSeenAt, response.getLastUpdatedAt());
    }

    @Test
    void getDeviceDetail_returnsMetadataLatestReadingsAndEnrichment() {
        UUID deviceId = UUID.randomUUID();
        IoTDevice device = createDevice(deviceId);
        SensorLatestReading temperature = createLatestReading("soilTemp", "Soil Temperature", "C", 28.1d, "2026-04-10T06:05:00Z");
        SensorLatestReading humidity = createLatestReading("humidity", "Humidity", "%", 61d, "2026-04-10T06:04:00Z");
        DeviceMediaEvent mediaEvent = createMediaEvent(device.getZone().getId(), deviceId);
        DeviceConfig deviceConfig = createDeviceConfig(device);

        when(ioTDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(sensorLatestReadingRepository.findAllByDeviceId(deviceId)).thenReturn(List.of(temperature, humidity));
        when(alertEventRepository.countByDeviceIdAndStatus(deviceId, AlertStatus.OPEN)).thenReturn(3L);
        when(alertEventRepository.countByDeviceIdAndStatusAndSeverity(deviceId, AlertStatus.OPEN, AlertSeverity.HIGH))
            .thenReturn(2L);
        when(alertEventRepository.countByDeviceIdAndStatusAndSeverity(deviceId, AlertStatus.OPEN, AlertSeverity.CRITICAL))
            .thenReturn(1L);
        when(alertEventRepository.findMaxOpenedAtByDeviceIdAndStatus(deviceId, AlertStatus.OPEN))
            .thenReturn(Instant.parse("2026-04-10T06:07:00Z"));
        when(deviceMediaEventRepository.findTopByDeviceIdOrderByCapturedAtDesc(deviceId)).thenReturn(Optional.of(mediaEvent));
        when(deviceConfigRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceConfig));

        DeviceDetailResponse response = dashboardQueryService.getDeviceDetail(deviceId);

        assertEquals(deviceId, response.getDeviceId());
        assertEquals("device-001", response.getDeviceUid());
        assertEquals("IOT-001", response.getDeviceCode());
        assertEquals("Greenhouse Node", response.getDeviceName());
        assertEquals("ONLINE", response.getStatus());
        assertEquals("PROVISIONED", response.getProvisioningStatus());
        assertEquals(device.getOwnerUser().getId(), response.getOwnerUserId());
        assertEquals(device.getFarmPlot().getId(), response.getFarmPlotId());
        assertEquals(device.getZone().getId(), response.getZoneId());
        assertEquals(3, response.getAlertSummary().getOpenAlerts());
        assertEquals(2, response.getAlertSummary().getHighSeverityAlerts());
        assertEquals(1, response.getAlertSummary().getCriticalAlerts());
        assertEquals(Instant.parse("2026-04-10T06:07:00Z"), response.getAlertSummary().getLatestAlertAt());
        assertEquals(mediaEvent.getId(), response.getLatestMedia().getMediaEventId());
        assertEquals(4, response.getConfig().getConfigVersion());
        assertEquals(60, response.getConfig().getSamplingIntervalSec());
        assertEquals(2, response.getLatestReadings().size());
        assertEquals("humidity", response.getLatestReadings().get(0).getSensorCode());
    }

    @Test
    void getDeviceDetail_rejectsUnknownDevice() {
        UUID deviceId = UUID.randomUUID();
        when(ioTDeviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        assertThrows(TelemetryQueryException.class, () -> dashboardQueryService.getDeviceDetail(deviceId));
    }

    @Test
    void getDeviceDetail_handlesMissingConfigMediaAndAlertsCleanly() {
        UUID deviceId = UUID.randomUUID();
        IoTDevice device = createDevice(deviceId);

        when(ioTDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(sensorLatestReadingRepository.findAllByDeviceId(deviceId)).thenReturn(List.of());
        when(alertEventRepository.countByDeviceIdAndStatus(deviceId, AlertStatus.OPEN)).thenReturn(0L);
        when(alertEventRepository.countByDeviceIdAndStatusAndSeverity(deviceId, AlertStatus.OPEN, AlertSeverity.HIGH))
            .thenReturn(0L);
        when(alertEventRepository.countByDeviceIdAndStatusAndSeverity(deviceId, AlertStatus.OPEN, AlertSeverity.CRITICAL))
            .thenReturn(0L);
        when(alertEventRepository.findMaxOpenedAtByDeviceIdAndStatus(deviceId, AlertStatus.OPEN)).thenReturn(null);
        when(deviceMediaEventRepository.findTopByDeviceIdOrderByCapturedAtDesc(deviceId)).thenReturn(Optional.empty());
        when(deviceConfigRepository.findByDeviceId(deviceId)).thenReturn(Optional.empty());

        DeviceDetailResponse response = dashboardQueryService.getDeviceDetail(deviceId);

        assertEquals(List.of(), response.getLatestReadings());
        assertEquals(0, response.getAlertSummary().getOpenAlerts());
        assertEquals(0, response.getAlertSummary().getHighSeverityAlerts());
        assertEquals(0, response.getAlertSummary().getCriticalAlerts());
        assertNull(response.getAlertSummary().getLatestAlertAt());
        assertNull(response.getLatestMedia());
        assertNull(response.getConfig());
    }

    private SensorLatestReading createLatestReading(
        String sensorCode,
        String sensorName,
        String unit,
        double readingValue,
        String readingTime
    ) {
        SensorType sensorType = new SensorType();
        sensorType.setId(UUID.nameUUIDFromBytes(sensorCode.getBytes()));
        sensorType.setCode(sensorCode);
        sensorType.setName(sensorName);
        sensorType.setUnit(unit);

        SensorLatestReading latestReading = new SensorLatestReading();
        latestReading.setSensorType(sensorType);
        latestReading.setReadingValue(readingValue);
        latestReading.setReadingTime(Instant.parse(readingTime));
        latestReading.setQualityStatus(ReadingQualityStatus.GOOD);
        return latestReading;
    }

    private IoTDevice createDevice(UUID deviceId) {
        IoTDevice device = new IoTDevice();
        device.setId(deviceId);
        device.setDeviceUid("device-001");
        device.setDeviceCode("IOT-001");
        device.setDeviceName("Greenhouse Node");
        device.setDeviceType("sensor-hub");
        device.setFirmwareVersion("1.0.5");
        device.setStatus(DeviceStatus.ONLINE);
        device.setProvisioningStatus(ProvisioningStatus.PROVISIONED);
        device.setIsActive(true);
        device.setLastSeenAt(Instant.parse("2026-04-10T06:06:00Z"));

        UserRef owner = new UserRef();
        owner.setId(UUID.randomUUID().toString());
        device.setOwnerUser(owner);

        FarmPlotRef farmPlot = new FarmPlotRef();
        farmPlot.setId(UUID.randomUUID().toString());
        device.setFarmPlot(farmPlot);

        FarmZoneRef zone = new FarmZoneRef();
        zone.setId(UUID.randomUUID().toString());
        device.setZone(zone);
        return device;
    }

    private DeviceMediaEvent createMediaEvent(String zoneId, UUID deviceId) {
        DeviceMediaEvent mediaEvent = new DeviceMediaEvent();
        mediaEvent.setId(UUID.randomUUID());
        mediaEvent.setCapturedAt(Instant.parse("2026-04-10T06:08:00Z"));
        mediaEvent.setMediaType("IMAGE");
        mediaEvent.setTriggerType("ALERT");

        IoTDevice device = new IoTDevice();
        device.setId(deviceId);
        mediaEvent.setDevice(device);

        FarmZoneRef zone = new FarmZoneRef();
        zone.setId(zoneId);
        mediaEvent.setZone(zone);

        FileRef file = new FileRef();
        file.setId(UUID.randomUUID().toString());
        mediaEvent.setFile(file);
        return mediaEvent;
    }

    private DeviceConfig createDeviceConfig(IoTDevice device) {
        DeviceConfig deviceConfig = new DeviceConfig();
        deviceConfig.setDevice(device);
        deviceConfig.setConfigVersion(4);
        deviceConfig.setSamplingIntervalSec(60);
        deviceConfig.setPublishIntervalSec(120);
        deviceConfig.setOfflineTimeoutSec(600);
        deviceConfig.setAlertEnabled(true);
        deviceConfig.setAppliedAt(Instant.parse("2026-04-10T06:03:00Z"));
        return deviceConfig;
    }
}
