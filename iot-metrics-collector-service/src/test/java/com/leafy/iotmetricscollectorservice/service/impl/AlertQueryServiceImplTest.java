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
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
        AlertEvent alertEvent = createAlertEvent(
            deviceId,
            UUID.randomUUID().toString(),
            AlertStatus.OPEN,
            AlertSeverity.HIGH
        );

        when(alertEventRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(alertEvent)));

        PagedResponse<AlertEventItemResponse> response = alertQueryService.searchAlerts(
            null,
            deviceId,
            AlertStatus.OPEN,
            null,
            null,
            null,
            0,
            20,
            "openedAt",
            "desc"
        );

        assertEquals(1, response.items().size());
        assertEquals(deviceId, response.items().getFirst().getDeviceId());
        assertEquals("OPEN", response.items().getFirst().getStatus());
        assertEquals("HIGH", response.items().getFirst().getSeverity());
    }

    @Test
    void searchAlerts_returnsMappedRowsForZoneSeverityAndTimeRangeFilters() {
        String zoneId = UUID.randomUUID().toString();
        Instant from = Instant.parse("2026-04-10T00:00:00Z");
        Instant to = Instant.parse("2026-04-11T00:00:00Z");
        AlertEvent alertEvent = createAlertEvent(UUID.randomUUID(), zoneId, AlertStatus.ACKNOWLEDGED, AlertSeverity.CRITICAL);
        alertEvent.setOpenedAt(Instant.parse("2026-04-10T06:00:00Z"));

        when(alertEventRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(alertEvent)));

        PagedResponse<AlertEventItemResponse> response = alertQueryService.searchAlerts(
            zoneId,
            null,
            AlertStatus.ACKNOWLEDGED,
            AlertSeverity.CRITICAL,
            from,
            to,
            0,
            20,
            "openedAt",
            "desc"
        );

        assertEquals(1, response.items().size());
        assertEquals(zoneId, response.items().getFirst().getZoneId());
        assertEquals("ACKNOWLEDGED", response.items().getFirst().getStatus());
        assertEquals(Instant.parse("2026-04-10T06:00:00Z"), response.items().getFirst().getOpenedAt());
    }

    @Test
    void searchAlerts_returnsEmptyListWhenRepositoryHasNoMatches() {
        when(alertEventRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(Page.empty());

        PagedResponse<AlertEventItemResponse> response = alertQueryService.searchAlerts(
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            20,
            "openedAt",
            "desc"
        );

        assertEquals(List.of(), response.items());
    }

    @Test
    void searchAlerts_rejectsInvalidWindow() {
        Instant from = Instant.parse("2026-04-11T00:00:00Z");
        Instant to = Instant.parse("2026-04-11T00:00:00Z");

        assertThrows(
            TelemetryQueryException.class,
            () -> alertQueryService.searchAlerts(null, null, null, null, from, to, 0, 20, "openedAt", "desc")
        );
    }

    @Test
    void getAlertEvent_returnsMappedDetail() {
        UUID alertEventId = UUID.randomUUID();
        AlertEvent alertEvent = createAlertEvent(
            UUID.randomUUID(),
            UUID.randomUUID().toString(),
            AlertStatus.OPEN,
            AlertSeverity.HIGH
        );
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
    void searchAlerts_paginatesAndUsesDescendingOpenedAtSort() {
        when(alertEventRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        alertQueryService.searchAlerts(null, null, null, null, null, null, 2, 10, "openedAt", "desc");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(alertEventRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        List<Sort.Order> orders = pageable.getSort().toList();
        assertEquals(2, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertEquals("openedAt", orders.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(0).getDirection());
    }

    @Test
    void searchAlerts_rejectsInvalidSortBy() {
        assertThrows(
            TelemetryQueryException.class,
            () -> alertQueryService.searchAlerts(null, null, null, null, null, null, 0, 20, "deviceId", "desc")
        );
    }

    @Test
    void searchAlerts_clampsMaxPageSize() {
        when(alertEventRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        alertQueryService.searchAlerts(null, null, null, null, null, null, 0, 999, "openedAt", "desc");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(alertEventRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertEquals(100, pageableCaptor.getValue().getPageSize());
    }

    private AlertEvent createAlertEvent(UUID deviceId, String zoneId, AlertStatus status, AlertSeverity severity) {
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
