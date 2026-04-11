package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventItemResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AlertQueryServiceImplTest {

    @Mock
    private AlertEventRepository alertEventRepository;

    @Spy
    private DashboardQueryMapper dashboardQueryMapper;

    @InjectMocks
    private AlertQueryServiceImpl alertQueryService;

    @Test
    void searchAlerts_returnsMappedRowsForDeviceFilter() {
        UUID deviceId = UUID.randomUUID();
        AlertEvent alertEvent = createAlertEvent(deviceId, UUID.randomUUID(), AlertStatus.OPEN, AlertSeverity.HIGH);

        when(alertEventRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(alertEvent));

        List<AlertEventItemResponse> response = alertQueryService.searchAlerts(
            null,
            deviceId,
            AlertStatus.OPEN,
            null,
            null,
            null
        );

        assertEquals(1, response.size());
        assertEquals(deviceId, response.getFirst().getDeviceId());
        assertEquals("OPEN", response.getFirst().getStatus());
        assertEquals("HIGH", response.getFirst().getSeverity());
    }

    @Test
    void searchAlerts_returnsMappedRowsForZoneSeverityAndTimeRangeFilters() {
        UUID zoneId = UUID.randomUUID();
        Instant from = Instant.parse("2026-04-10T00:00:00Z");
        Instant to = Instant.parse("2026-04-11T00:00:00Z");
        AlertEvent alertEvent = createAlertEvent(UUID.randomUUID(), zoneId, AlertStatus.ACKNOWLEDGED, AlertSeverity.CRITICAL);
        alertEvent.setOpenedAt(Instant.parse("2026-04-10T06:00:00Z"));

        when(alertEventRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(alertEvent));

        List<AlertEventItemResponse> response = alertQueryService.searchAlerts(
            zoneId,
            null,
            AlertStatus.ACKNOWLEDGED,
            AlertSeverity.CRITICAL,
            from,
            to
        );

        assertEquals(1, response.size());
        assertEquals(zoneId, response.getFirst().getZoneId());
        assertEquals("ACKNOWLEDGED", response.getFirst().getStatus());
        assertEquals(Instant.parse("2026-04-10T06:00:00Z"), response.getFirst().getOpenedAt());
    }

    @Test
    void searchAlerts_returnsEmptyListWhenRepositoryHasNoMatches() {
        when(alertEventRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        List<AlertEventItemResponse> response = alertQueryService.searchAlerts(
            null,
            null,
            null,
            null,
            null,
            null
        );

        assertEquals(List.of(), response);
    }

    @Test
    void searchAlerts_rejectsInvalidWindow() {
        Instant from = Instant.parse("2026-04-11T00:00:00Z");
        Instant to = Instant.parse("2026-04-11T00:00:00Z");

        assertThrows(
            TelemetryQueryException.class,
            () -> alertQueryService.searchAlerts(null, null, null, null, from, to)
        );
    }

    @Test
    void getAlertEvent_returnsMappedDetail() {
        UUID alertEventId = UUID.randomUUID();
        AlertEvent alertEvent = createAlertEvent(UUID.randomUUID(), UUID.randomUUID(), AlertStatus.OPEN, AlertSeverity.HIGH);
        alertEvent.setId(alertEventId);

        when(alertEventRepository.findById(alertEventId)).thenReturn(Optional.of(alertEvent));

        AlertEventDetailResponse response = alertQueryService.getAlertEvent(alertEventId);

        assertEquals(alertEventId, response.getId());
        assertEquals(alertEvent.getDevice().getId(), response.getDeviceId());
        assertEquals(alertEvent.getZone().getId(), response.getZoneId());
        assertEquals(alertEvent.getSensorType().getId(), response.getSensorTypeId());
    }

    @Test
    void getAlertEvent_rejectsUnknownId() {
        UUID alertEventId = UUID.randomUUID();
        when(alertEventRepository.findById(alertEventId)).thenReturn(Optional.empty());

        assertThrows(TelemetryQueryException.class, () -> alertQueryService.getAlertEvent(alertEventId));
    }

    @Test
    void searchAlerts_usesDescendingOpenedAtSort() {
        when(alertEventRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        alertQueryService.searchAlerts(null, null, null, null, null, null);

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(alertEventRepository).findAll(any(Specification.class), sortCaptor.capture());
        List<Sort.Order> orders = sortCaptor.getValue().toList();
        assertEquals("openedAt", orders.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(0).getDirection());
    }

    private AlertEvent createAlertEvent(UUID deviceId, UUID zoneId, AlertStatus status, AlertSeverity severity) {
        AlertEvent alertEvent = new AlertEvent();
        alertEvent.setId(UUID.randomUUID());
        alertEvent.setAlertType("threshold");
        alertEvent.setMessage("Threshold exceeded");
        alertEvent.setStatus(status);
        alertEvent.setSeverity(severity);
        alertEvent.setTriggerValue(75d);
        alertEvent.setThresholdMin(10d);
        alertEvent.setThresholdMax(60d);
        alertEvent.setOpenedAt(Instant.parse("2026-04-10T04:00:00Z"));
        alertEvent.setAcknowledgedAt(Instant.parse("2026-04-10T04:05:00Z"));
        alertEvent.setResolvedAt(Instant.parse("2026-04-10T04:10:00Z"));
        alertEvent.setPushSent(true);

        IoTDevice device = new IoTDevice();
        device.setId(deviceId);
        alertEvent.setDevice(device);

        FarmZoneRef zone = new FarmZoneRef();
        zone.setId(zoneId);
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
