package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.client.IotCollectorClient;
import com.leafy.iottestdataservice.client.dto.CollectorAlertRuleRequest;
import com.leafy.iottestdataservice.client.dto.CollectorAlertRuleResponse;
import com.leafy.iottestdataservice.client.dto.CollectorPagedResponse;
import com.leafy.iottestdataservice.model.AlertRuleBootstrapResult;
import com.leafy.iottestdataservice.model.BootstrappedDevice;
import com.leafy.iottestdataservice.service.AlertRuleBootstrapService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleBootstrapServiceImpl implements AlertRuleBootstrapService {

    private static final int PAGE_SIZE = 100;

    private final IotCollectorClient iotCollectorClient;

    @Override
    public AlertRuleBootstrapResult bootstrapMinimalRules(Map<String, UUID> sensorTypeIds, List<BootstrappedDevice> devices) {
        if (devices.isEmpty()) {
            return new AlertRuleBootstrapResult(0, List.of("No devices available for minimal alert rule bootstrap."));
        }

        List<RuleSeedPlan> plans = new ArrayList<>();
        BootstrappedDevice primaryDevice = devices.getFirst();
        plans.add(new RuleSeedPlan(
            primaryDevice.ownerUserId(),
            new CollectorAlertRuleRequest(sensorTypeIds.get("AIR_TEMP"), primaryDevice.device().id(), null, null, null, 38d, "HIGH", 10, true, false, true)
        ));
        plans.add(new RuleSeedPlan(
            primaryDevice.ownerUserId(),
            new CollectorAlertRuleRequest(sensorTypeIds.get("SOIL_MOISTURE"), primaryDevice.device().id(), null, null, 28d, null, "CRITICAL", 30, true, true, true)
        ));

        if (devices.size() > 1) {
            BootstrappedDevice secondaryDevice = devices.get(1);
            plans.add(new RuleSeedPlan(
                secondaryDevice.ownerUserId(),
                new CollectorAlertRuleRequest(sensorTypeIds.get("AIR_HUMIDITY"), null, secondaryDevice.zoneId(), null, 52d, null, "MEDIUM", 15, true, false, true)
            ));
            plans.add(new RuleSeedPlan(
                secondaryDevice.ownerUserId(),
                new CollectorAlertRuleRequest(sensorTypeIds.get("LIGHT_INTENSITY"), secondaryDevice.device().id(), null, null, 80d, null, "LOW", 5, true, false, true)
            ));
        }

        return createRules(plans);
    }

    @Override
    public AlertRuleBootstrapResult bootstrapFullRules(Map<String, UUID> sensorTypeIds, List<BootstrappedDevice> devices) {
        if (devices.isEmpty()) {
            return new AlertRuleBootstrapResult(0, List.of("No devices available for full alert rule bootstrap."));
        }

        List<RuleSeedPlan> plans = new ArrayList<>();
        for (int index = 0; index < devices.size(); index++) {
            BootstrappedDevice device = devices.get(index);
            plans.add(new RuleSeedPlan(
                device.ownerUserId(),
                new CollectorAlertRuleRequest(sensorTypeIds.get("AIR_TEMP"), device.device().id(), null, null, null, 37d + index, "HIGH", 10, true, false, true)
            ));
            plans.add(new RuleSeedPlan(
                device.ownerUserId(),
                new CollectorAlertRuleRequest(sensorTypeIds.get("SOIL_MOISTURE"), null, device.zoneId(), null, 30d, null, "CRITICAL", 20, true, true, true)
            ));
        }

        BootstrappedDevice firstDevice = devices.getFirst();
        plans.add(new RuleSeedPlan(
            firstDevice.ownerUserId(),
            new CollectorAlertRuleRequest(sensorTypeIds.get("AIR_HUMIDITY"), null, null, firstDevice.farmPlotId(), 50d, null, "MEDIUM", 15, true, false, true)
        ));

        return createRules(plans);
    }

    private AlertRuleBootstrapResult createRules(List<RuleSeedPlan> plans) {
        int createdCount = 0;
        List<String> warnings = new ArrayList<>();

        for (RuleSeedPlan plan : plans) {
            if (plan.request().sensorTypeId() == null) {
                warnings.add("Skipped alert rule because sensor type reference data is missing.");
                continue;
            }
            try {
                if (ruleExists(plan.ownerUserId(), plan.request())) {
                    log.info("Skipping existing alert rule for owner={}, sensorType={}", plan.ownerUserId(), plan.request().sensorTypeId());
                    continue;
                }
                iotCollectorClient.createAlertRule(plan.ownerUserId(), plan.request());
                createdCount++;
            } catch (Exception exception) {
                warnings.add("Failed to create alert rule for user " + plan.ownerUserId() + ": " + exception.getMessage());
            }
        }

        return new AlertRuleBootstrapResult(createdCount, List.copyOf(warnings));
    }

    private boolean ruleExists(String ownerUserId, CollectorAlertRuleRequest request) {
        int page = 0;
        CollectorPagedResponse<CollectorAlertRuleResponse> response;

        do {
            response = iotCollectorClient.getAlertRules(
                ownerUserId,
                request.sensorTypeId(),
                request.deviceId(),
                request.zoneId(),
                request.farmPlotId(),
                request.enabled(),
                page,
                PAGE_SIZE,
                "updatedAt",
                "desc"
            );

            if (response != null && response.items() != null && response.items().stream().anyMatch(item -> matches(item, request))) {
                return true;
            }
            page++;
        } while (response != null && response.hasNext());

        return false;
    }

    private boolean matches(CollectorAlertRuleResponse item, CollectorAlertRuleRequest request) {
        return Objects.equals(item.sensorTypeId(), request.sensorTypeId())
            && Objects.equals(item.deviceId(), request.deviceId())
            && Objects.equals(item.zoneId(), request.zoneId())
            && Objects.equals(item.farmPlotId(), request.farmPlotId())
            && Objects.equals(item.minThreshold(), request.minThreshold())
            && Objects.equals(item.maxThreshold(), request.maxThreshold())
            && Objects.equals(item.severity(), request.severity())
            && Objects.equals(item.cooldownMinutes(), request.cooldownMinutes())
            && Objects.equals(item.notifyWeb(), request.notifyWeb())
            && Objects.equals(item.notifyMobile(), request.notifyMobile())
            && Objects.equals(item.enabled(), request.enabled());
    }

    private record RuleSeedPlan(String ownerUserId, CollectorAlertRuleRequest request) {
    }
}
