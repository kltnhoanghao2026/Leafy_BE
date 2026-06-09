package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.dto.alert_rule.AlertRuleResponse;
import com.leafy.iotmetricscollectorservice.dto.alert_rule.CreateAlertRuleRequest;
import com.leafy.iotmetricscollectorservice.dto.alert_rule.UpdateAlertRuleRequest;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.mapper.AlertRuleMapper;
import com.leafy.iotmetricscollectorservice.model.AlertRule;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.ref.FarmPlotRef;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import com.leafy.iotmetricscollectorservice.repository.AlertEventRepository;
import com.leafy.iotmetricscollectorservice.repository.AlertRuleRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorTypeRepository;
import com.leafy.iotmetricscollectorservice.service.DeviceAccessService;
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
class AlertRuleServiceImplTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @Mock
    private AlertEventRepository alertEventRepository;

    @Mock
    private SensorTypeRepository sensorTypeRepository;

    @Mock
    private IoTDeviceRepository ioTDeviceRepository;

    @Mock
    private DeviceAccessService deviceAccessService;

    @Spy
    private AlertRuleMapper alertRuleMapper;

    @InjectMocks
    private AlertRuleServiceImpl alertRuleService;

    @Test
    void createRule_succeedsWithValidMinThreshold() {
        String currentUserId = UUID.randomUUID().toString();
        UUID sensorTypeId = UUID.randomUUID();
        String zoneId = UUID.randomUUID().toString();
        CreateAlertRuleRequest request = createRequest(sensorTypeId, null, zoneId, null, 12d, null);

        when(sensorTypeRepository.findById(sensorTypeId)).thenReturn(Optional.of(createSensorType(sensorTypeId)));
        when(alertRuleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> {
            AlertRule alertRule = invocation.getArgument(0);
            alertRule.setId(UUID.randomUUID());
            alertRule.setCreatedAt(Instant.parse("2026-04-15T00:00:00Z"));
            alertRule.setUpdatedAt(Instant.parse("2026-04-15T00:00:30Z"));
            return alertRule;
        });

        AlertRuleResponse response = alertRuleService.createRule(currentUserId, request);

        assertEquals(currentUserId, response.getOwnerUserId());
        assertEquals(sensorTypeId, response.getSensorTypeId());
        assertEquals(zoneId, response.getZoneId());
        assertEquals(12d, response.getMinThreshold());
        assertEquals(Boolean.FALSE, response.getNotifyMobile());
        assertEquals(Boolean.FALSE, response.getNotifyWeb());
        assertEquals(Boolean.TRUE, response.getEnabled());
    }

    @Test
    void createRule_succeedsWithValidMaxThreshold() {
        String currentUserId = UUID.randomUUID().toString();
        UUID sensorTypeId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        CreateAlertRuleRequest request = createRequest(sensorTypeId, deviceId, null, null, null, 40d);

        when(sensorTypeRepository.findById(sensorTypeId)).thenReturn(Optional.of(createSensorType(sensorTypeId)));
        when(alertRuleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRuleResponse response = alertRuleService.createRule(currentUserId, request);

        assertEquals(deviceId, response.getDeviceId());
        assertEquals(40d, response.getMaxThreshold());
    }

    @Test
    void createRule_succeedsWithBothThresholdsWhenMinLessThanMax() {
        UUID sensorTypeId = UUID.randomUUID();
        String farmPlotId = UUID.randomUUID().toString();
        CreateAlertRuleRequest request = createRequest(sensorTypeId, null, null, farmPlotId, 10d, 20d);

        when(sensorTypeRepository.findById(sensorTypeId)).thenReturn(Optional.of(createSensorType(sensorTypeId)));
        when(alertRuleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRuleResponse response = alertRuleService.createRule(UUID.randomUUID().toString(), request);

        assertEquals(10d, response.getMinThreshold());
        assertEquals(20d, response.getMaxThreshold());
        assertEquals(farmPlotId, response.getFarmPlotId());
    }

    @Test
    void createRule_failsWhenBothThresholdsAreNull() {
        UUID sensorTypeId = UUID.randomUUID();
        CreateAlertRuleRequest request = createRequest(sensorTypeId, null, UUID.randomUUID().toString(), null, null, null);

        when(sensorTypeRepository.findById(sensorTypeId)).thenReturn(Optional.of(createSensorType(sensorTypeId)));

        assertThrows(TelemetryQueryException.class, () -> alertRuleService.createRule(UUID.randomUUID().toString(), request));
        verify(alertRuleRepository, never()).save(any(AlertRule.class));
    }

    @Test
    void createRule_failsWhenMinIsGreaterThanOrEqualToMax() {
        UUID sensorTypeId = UUID.randomUUID();
        CreateAlertRuleRequest request = createRequest(sensorTypeId, null, UUID.randomUUID().toString(), null, 25d, 25d);

        when(sensorTypeRepository.findById(sensorTypeId)).thenReturn(Optional.of(createSensorType(sensorTypeId)));

        assertThrows(TelemetryQueryException.class, () -> alertRuleService.createRule(UUID.randomUUID().toString(), request));
        verify(alertRuleRepository, never()).save(any(AlertRule.class));
    }

    @Test
    void createRule_failsWhenAllScopesAreNull() {
        UUID sensorTypeId = UUID.randomUUID();
        CreateAlertRuleRequest request = createRequest(sensorTypeId, null, null, null, 10d, null);

        when(sensorTypeRepository.findById(sensorTypeId)).thenReturn(Optional.of(createSensorType(sensorTypeId)));

        assertThrows(TelemetryQueryException.class, () -> alertRuleService.createRule(UUID.randomUUID().toString(), request));
        verify(alertRuleRepository, never()).save(any(AlertRule.class));
    }

    @Test
    void createRule_failsOnInvalidCooldown() {
        UUID sensorTypeId = UUID.randomUUID();
        CreateAlertRuleRequest request = createRequest(sensorTypeId, null, UUID.randomUUID().toString(), null, 10d, null);
        request.setCooldownMinutes(-1);

        when(sensorTypeRepository.findById(sensorTypeId)).thenReturn(Optional.of(createSensorType(sensorTypeId)));

        assertThrows(TelemetryQueryException.class, () -> alertRuleService.createRule(UUID.randomUUID().toString(), request));
        verify(alertRuleRepository, never()).save(any(AlertRule.class));
    }

    @Test
    void listRules_filtersByOwnerAndOptionalFilters() {
        String currentUserId = UUID.randomUUID().toString();
        AlertRule alertRule = createRuleEntity(currentUserId);

        when(alertRuleRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(alertRule)));

        PagedResponse<AlertRuleResponse> responses = alertRuleService.listRules(
            currentUserId,
            alertRule.getSensorType().getId(),
            alertRule.getDevice().getId(),
            null,
            null,
            true,
            0,
            20,
            "updatedAt",
            "desc"
        );

        assertEquals(1, responses.items().size());
        assertEquals(currentUserId, responses.items().getFirst().getOwnerUserId());
        assertEquals(alertRule.getDevice().getId(), responses.items().getFirst().getDeviceId());
    }

    @Test
    void listRules_usesRequestedPaginationAndSorting() {
        String currentUserId = UUID.randomUUID().toString();

        when(alertRuleRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        alertRuleService.listRules(currentUserId, null, null, null, null, null, 1, 15, "updatedAt", "desc");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(alertRuleRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        List<Sort.Order> orders = pageable.getSort().toList();
        assertEquals(1, pageable.getPageNumber());
        assertEquals(15, pageable.getPageSize());
        assertEquals("updatedAt", orders.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(0).getDirection());
    }

    @Test
    void listRules_rejectsInvalidSortDir() {
        assertThrows(
            TelemetryQueryException.class,
            () -> alertRuleService.listRules(UUID.randomUUID().toString(), null, null, null, null, null, 0, 20, "updatedAt", "down")
        );
    }

    @Test
    void listRules_clampsMaxPageSize() {
        when(alertRuleRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        alertRuleService.listRules(UUID.randomUUID().toString(), null, null, null, null, null, 0, 500, "updatedAt", "desc");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(alertRuleRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertEquals(100, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void getRule_returnsOwnedRule() {
        String currentUserId = UUID.randomUUID().toString();
        AlertRule alertRule = createRuleEntity(currentUserId);

        when(alertRuleRepository.findByIdAndOwnerUserId(alertRule.getId(), currentUserId)).thenReturn(Optional.of(alertRule));

        AlertRuleResponse response = alertRuleService.getRule(currentUserId, alertRule.getId());

        assertEquals(alertRule.getId(), response.getId());
        assertEquals(currentUserId, response.getOwnerUserId());
    }

    @Test
    void getRule_failsForMissingRule() {
        String currentUserId = UUID.randomUUID().toString();
        UUID ruleId = UUID.randomUUID();
        when(alertRuleRepository.findByIdAndOwnerUserId(ruleId, currentUserId)).thenReturn(Optional.empty());

        assertThrows(TelemetryQueryException.class, () -> alertRuleService.getRule(currentUserId, ruleId));
    }

    @Test
    void updateRule_succeeds() {
        String currentUserId = UUID.randomUUID().toString();
        UUID ruleId = UUID.randomUUID();
        UUID sensorTypeId = UUID.randomUUID();
        String zoneId = UUID.randomUUID().toString();
        AlertRule alertRule = createRuleEntity(currentUserId);
        alertRule.setId(ruleId);

        UpdateAlertRuleRequest request = new UpdateAlertRuleRequest();
        request.setSensorTypeId(sensorTypeId);
        request.setZoneId(zoneId);
        request.setMinThreshold(5d);
        request.setMaxThreshold(15d);
        request.setSeverity("CRITICAL");
        request.setCooldownMinutes(30);
        request.setNotifyWeb(true);
        request.setNotifyMobile(false);
        request.setEnabled(false);

        when(alertRuleRepository.findByIdAndOwnerUserId(ruleId, currentUserId)).thenReturn(Optional.of(alertRule));
        when(sensorTypeRepository.findById(sensorTypeId)).thenReturn(Optional.of(createSensorType(sensorTypeId)));
        when(alertRuleRepository.save(alertRule)).thenReturn(alertRule);

        AlertRuleResponse response = alertRuleService.updateRule(currentUserId, ruleId, request);

        assertEquals(sensorTypeId, response.getSensorTypeId());
        assertEquals(zoneId, response.getZoneId());
        assertEquals("CRITICAL", response.getSeverity());
        assertEquals(Boolean.FALSE, response.getEnabled());
    }

    @Test
    void updateRule_preservesExistingScopeAndSensorTypeWhenOmitted() {
        String currentUserId = UUID.randomUUID().toString();
        UUID ruleId = UUID.randomUUID();
        AlertRule alertRule = createRuleEntity(currentUserId);
        alertRule.setId(ruleId);

        UpdateAlertRuleRequest request = new UpdateAlertRuleRequest();
        request.setMinThreshold(8d);
        request.setMaxThreshold(28d);
        request.setSeverity("MEDIUM");
        request.setEnabled(false);

        when(alertRuleRepository.findByIdAndOwnerUserId(ruleId, currentUserId)).thenReturn(Optional.of(alertRule));
        when(alertRuleRepository.save(alertRule)).thenReturn(alertRule);

        AlertRuleResponse response = alertRuleService.updateRule(currentUserId, ruleId, request);

        assertEquals(alertRule.getSensorType().getId(), response.getSensorTypeId());
        assertEquals(alertRule.getDevice().getId(), response.getDeviceId());
        assertEquals(alertRule.getZone().getId(), response.getZoneId());
        assertEquals(alertRule.getFarmPlot().getId(), response.getFarmPlotId());
        assertEquals(8d, response.getMinThreshold());
        assertEquals(28d, response.getMaxThreshold());
        assertEquals("MEDIUM", response.getSeverity());
        assertEquals(Boolean.FALSE, response.getEnabled());
        verify(sensorTypeRepository, never()).findById(any());
        verify(deviceAccessService, never()).requireOwnedDevice(any(), any());
        verify(deviceAccessService, never()).requireOwnedZone(any(), any());
        verify(deviceAccessService, never()).requireOwnedFarmPlot(any(), any());
    }

    @Test
    void updateRule_rebindsDeviceScopedRuleToCurrentDeviceAssignmentWhenScopeOmitted() {
        String currentUserId = UUID.randomUUID().toString();
        UUID ruleId = UUID.randomUUID();
        String currentFarmPlotId = UUID.randomUUID().toString();
        String currentZoneId = UUID.randomUUID().toString();
        AlertRule alertRule = createRuleEntity(currentUserId);
        alertRule.setId(ruleId);
        IoTDevice currentDevice = createDevice(alertRule.getDevice().getId());
        currentDevice.setOwnerUser(toUserRef(currentUserId));
        currentDevice.setFarmPlot(toFarmPlot(currentFarmPlotId));
        currentDevice.setZone(toZone(currentZoneId));

        UpdateAlertRuleRequest request = new UpdateAlertRuleRequest();
        request.setMinThreshold(8d);
        request.setMaxThreshold(28d);
        request.setSeverity("MEDIUM");

        when(alertRuleRepository.findByIdAndOwnerUserId(ruleId, currentUserId)).thenReturn(Optional.of(alertRule));
        when(ioTDeviceRepository.findById(alertRule.getDevice().getId())).thenReturn(Optional.of(currentDevice));
        when(alertRuleRepository.save(alertRule)).thenReturn(alertRule);

        AlertRuleResponse response = alertRuleService.updateRule(currentUserId, ruleId, request);

        assertEquals(currentDevice.getId(), response.getDeviceId());
        assertEquals(currentZoneId, response.getZoneId());
        assertEquals(currentFarmPlotId, response.getFarmPlotId());
        verify(deviceAccessService).requireOwnedDevice(currentDevice.getId(), currentUserId);
        verify(deviceAccessService).requireOwnedZone(currentZoneId, currentUserId);
        verify(deviceAccessService).requireOwnedFarmPlot(currentFarmPlotId, currentUserId);
    }

    @Test
    void updateRuleEnabled_succeeds() {
        String currentUserId = UUID.randomUUID().toString();
        AlertRule alertRule = createRuleEntity(currentUserId);

        when(alertRuleRepository.findByIdAndOwnerUserId(alertRule.getId(), currentUserId)).thenReturn(Optional.of(alertRule));
        when(alertRuleRepository.save(alertRule)).thenReturn(alertRule);

        AlertRuleResponse response = alertRuleService.updateRuleEnabled(currentUserId, alertRule.getId(), false);

        assertEquals(Boolean.FALSE, response.getEnabled());
    }

    @Test
    void deleteRule_succeeds() {
        String currentUserId = UUID.randomUUID().toString();
        AlertRule alertRule = createRuleEntity(currentUserId);

        when(alertRuleRepository.findByIdAndOwnerUserId(alertRule.getId(), currentUserId)).thenReturn(Optional.of(alertRule));

        alertRuleService.deleteRule(currentUserId, alertRule.getId());

        verify(alertEventRepository).clearAlertRuleByAlertRuleId(alertRule.getId());
        verify(alertRuleRepository).delete(alertRule);
    }

    private CreateAlertRuleRequest createRequest(
        UUID sensorTypeId,
        UUID deviceId,
        String zoneId,
        String farmPlotId,
        Double minThreshold,
        Double maxThreshold
    ) {
        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setSensorTypeId(sensorTypeId);
        request.setDeviceId(deviceId);
        request.setZoneId(zoneId);
        request.setFarmPlotId(farmPlotId);
        request.setMinThreshold(minThreshold);
        request.setMaxThreshold(maxThreshold);
        request.setSeverity("HIGH");
        request.setCooldownMinutes(10);
        return request;
    }

    private SensorType createSensorType(UUID sensorTypeId) {
        SensorType sensorType = new SensorType();
        sensorType.setId(sensorTypeId);
        sensorType.setCode("temperature");
        sensorType.setName("Temperature");
        return sensorType;
    }

    private AlertRule createRuleEntity(String ownerUserId) {
        AlertRule alertRule = new AlertRule();
        alertRule.setId(UUID.randomUUID());
        alertRule.setOwnerUser(toUserRef(ownerUserId));
        alertRule.setSensorType(createSensorType(UUID.randomUUID()));
        alertRule.setDevice(createDevice(UUID.randomUUID()));
        alertRule.setZone(toZone(UUID.randomUUID().toString()));
        alertRule.setFarmPlot(toFarmPlot(UUID.randomUUID().toString()));
        alertRule.setMinThreshold(10d);
        alertRule.setMaxThreshold(30d);
        alertRule.setSeverity(AlertSeverity.HIGH);
        alertRule.setCooldownMinutes(10);
        alertRule.setNotifyWeb(true);
        alertRule.setNotifyMobile(false);
        alertRule.setEnabled(true);
        alertRule.setCreatedAt(Instant.parse("2026-04-15T00:00:00Z"));
        alertRule.setUpdatedAt(Instant.parse("2026-04-15T00:10:00Z"));
        return alertRule;
    }

    private UserRef toUserRef(String userId) {
        UserRef userRef = new UserRef();
        userRef.setId(userId);
        return userRef;
    }

    private IoTDevice createDevice(UUID deviceId) {
        IoTDevice device = new IoTDevice();
        device.setId(deviceId);
        return device;
    }

    private FarmZoneRef toZone(String zoneId) {
        FarmZoneRef zone = new FarmZoneRef();
        zone.setId(zoneId);
        return zone;
    }

    private FarmPlotRef toFarmPlot(String farmPlotId) {
        FarmPlotRef farmPlot = new FarmPlotRef();
        farmPlot.setId(farmPlotId);
        return farmPlot;
    }
}
