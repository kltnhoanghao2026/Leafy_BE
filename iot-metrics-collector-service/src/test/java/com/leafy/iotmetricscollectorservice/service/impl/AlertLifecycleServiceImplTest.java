package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventDetailResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.mapper.DashboardQueryMapper;
import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.AlertRule;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.repository.AlertEventRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertLifecycleServiceImplTest {

    @Mock
    private AlertEventRepository alertEventRepository;

    @Spy
    private DashboardQueryMapper dashboardQueryMapper;

    @InjectMocks
    private AlertLifecycleServiceImpl alertLifecycleService;

    @Test
    void acknowledgeAlert_fromOpen_succeeds() {
        AlertEvent alertEvent = createAlertEvent(AlertStatus.OPEN);
        Instant before = Instant.now();

        when(alertEventRepository.findById(alertEvent.getId())).thenReturn(Optional.of(alertEvent));
        when(alertEventRepository.save(alertEvent)).thenReturn(alertEvent);

        AlertEventDetailResponse response = alertLifecycleService.acknowledgeAlert(alertEvent.getId());

        Instant after = Instant.now();
        assertEquals(AlertStatus.ACKNOWLEDGED, alertEvent.getStatus());
        assertNotNull(alertEvent.getAcknowledgedAt());
        assertFalse(alertEvent.getAcknowledgedAt().isBefore(before));
        assertFalse(alertEvent.getAcknowledgedAt().isAfter(after));
        assertEquals("ACKNOWLEDGED", response.getStatus());
        verify(alertEventRepository).save(alertEvent);
    }

    @Test
    void acknowledgeAlert_fromAcknowledged_fails() {
        AlertEvent alertEvent = createAlertEvent(AlertStatus.ACKNOWLEDGED);
        when(alertEventRepository.findById(alertEvent.getId())).thenReturn(Optional.of(alertEvent));

        assertThrows(TelemetryQueryException.class, () -> alertLifecycleService.acknowledgeAlert(alertEvent.getId()));
        verify(alertEventRepository, never()).save(any(AlertEvent.class));
    }

    @Test
    void acknowledgeAlert_fromResolved_fails() {
        AlertEvent alertEvent = createAlertEvent(AlertStatus.RESOLVED);
        when(alertEventRepository.findById(alertEvent.getId())).thenReturn(Optional.of(alertEvent));

        assertThrows(TelemetryQueryException.class, () -> alertLifecycleService.acknowledgeAlert(alertEvent.getId()));
        verify(alertEventRepository, never()).save(any(AlertEvent.class));
    }

    @Test
    void resolveAlert_fromOpen_succeeds() {
        AlertEvent alertEvent = createAlertEvent(AlertStatus.OPEN);
        Instant before = Instant.now();

        when(alertEventRepository.findById(alertEvent.getId())).thenReturn(Optional.of(alertEvent));
        when(alertEventRepository.save(alertEvent)).thenReturn(alertEvent);

        AlertEventDetailResponse response = alertLifecycleService.resolveAlert(alertEvent.getId());

        Instant after = Instant.now();
        assertEquals(AlertStatus.RESOLVED, alertEvent.getStatus());
        assertNotNull(alertEvent.getResolvedAt());
        assertFalse(alertEvent.getResolvedAt().isBefore(before));
        assertFalse(alertEvent.getResolvedAt().isAfter(after));
        assertEquals("RESOLVED", response.getStatus());
        verify(alertEventRepository).save(alertEvent);
    }

    @Test
    void resolveAlert_fromAcknowledged_succeeds() {
        AlertEvent alertEvent = createAlertEvent(AlertStatus.ACKNOWLEDGED);
        when(alertEventRepository.findById(alertEvent.getId())).thenReturn(Optional.of(alertEvent));
        when(alertEventRepository.save(alertEvent)).thenReturn(alertEvent);

        AlertEventDetailResponse response = alertLifecycleService.resolveAlert(alertEvent.getId());

        assertEquals(AlertStatus.RESOLVED, alertEvent.getStatus());
        assertNotNull(alertEvent.getResolvedAt());
        assertEquals("RESOLVED", response.getStatus());
        verify(alertEventRepository).save(alertEvent);
    }

    @Test
    void resolveAlert_fromResolved_fails() {
        AlertEvent alertEvent = createAlertEvent(AlertStatus.RESOLVED);
        when(alertEventRepository.findById(alertEvent.getId())).thenReturn(Optional.of(alertEvent));

        assertThrows(TelemetryQueryException.class, () -> alertLifecycleService.resolveAlert(alertEvent.getId()));
        verify(alertEventRepository, never()).save(any(AlertEvent.class));
    }

    @Test
    void acknowledgeAlert_notFound_fails() {
        UUID alertEventId = UUID.randomUUID();
        when(alertEventRepository.findById(alertEventId)).thenReturn(Optional.empty());

        assertThrows(TelemetryQueryException.class, () -> alertLifecycleService.acknowledgeAlert(alertEventId));
    }

    @Test
    void resolveAlert_notFound_fails() {
        UUID alertEventId = UUID.randomUUID();
        when(alertEventRepository.findById(alertEventId)).thenReturn(Optional.empty());

        assertThrows(TelemetryQueryException.class, () -> alertLifecycleService.resolveAlert(alertEventId));
    }

    private AlertEvent createAlertEvent(AlertStatus status) {
        AlertEvent alertEvent = new AlertEvent();
        alertEvent.setId(UUID.randomUUID());
        alertEvent.setStatus(status);
        alertEvent.setSeverity(AlertSeverity.HIGH);
        alertEvent.setAlertType("threshold");
        alertEvent.setMessage("Threshold exceeded");
        alertEvent.setTriggerValue(80d);
        alertEvent.setThresholdMin(10d);
        alertEvent.setThresholdMax(70d);
        alertEvent.setOpenedAt(Instant.parse("2026-04-10T02:00:00Z"));
        alertEvent.setPushSent(true);

        IoTDevice device = new IoTDevice();
        device.setId(UUID.randomUUID());
        alertEvent.setDevice(device);

        FarmZoneRef zone = new FarmZoneRef();
        zone.setId(UUID.randomUUID());
        alertEvent.setZone(zone);

        SensorType sensorType = new SensorType();
        sensorType.setId(UUID.randomUUID());
        alertEvent.setSensorType(sensorType);

        AlertRule alertRule = new AlertRule();
        alertRule.setId(UUID.randomUUID());
        alertEvent.setAlertRule(alertRule);
        return alertEvent;
    }
}
