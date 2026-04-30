package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.dto.BootstrapRequest;
import com.leafy.iottestdataservice.dto.BootstrapResponse;
import com.leafy.iottestdataservice.model.AlertRuleBootstrapResult;
import com.leafy.iottestdataservice.model.BootstrappedDevice;
import com.leafy.iottestdataservice.model.ReferenceSeedResult;
import com.leafy.iottestdataservice.service.AlertRuleBootstrapService;
import com.leafy.iottestdataservice.service.DeviceBootstrapService;
import com.leafy.iottestdataservice.service.ReferenceSeedService;
import com.leafy.iottestdataservice.service.SeedBootstrapService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeedBootstrapServiceImpl implements SeedBootstrapService {

    private final SeedProperties seedProperties;
    private final ReferenceSeedService referenceSeedService;
    private final DeviceBootstrapService deviceBootstrapService;
    private final AlertRuleBootstrapService alertRuleBootstrapService;

    @Override
    public BootstrapResponse bootstrapMinimal(BootstrapRequest request) {
        ReferenceSeedResult references = referenceSeedService.seedMinimalReferenceData();
        List<String> warnings = new ArrayList<>();
        List<BootstrappedDevice> devices = bootstrapDevices(buildMinimalPlans(references), warnings);
        AlertRuleBootstrapResult rules = alertRuleBootstrapService.bootstrapMinimalRules(references.sensorTypeIds(), devices);
        warnings.addAll(rules.warnings());
        int provisionedDevices = devices.stream().mapToInt(device -> device.provisioned() ? 1 : 0).sum();
        int claimedDevices = devices.stream().mapToInt(device -> device.claimed() ? 1 : 0).sum();

        log.info(
            "Completed minimal bootstrap: usersCreated={}, plotsCreated={}, zonesCreated={}, devicesProvisioned={}, devicesClaimed={}, rulesCreated={}",
            references.usersSeeded(),
            references.farmPlotsSeeded(),
            references.zonesSeeded(),
            provisionedDevices,
            claimedDevices,
            rules.createdCount()
        );

        return new BootstrapResponse(
            "minimal",
            references.usersSeeded(),
            references.farmPlotsSeeded(),
            references.zonesSeeded(),
            references.sensorTypesSeeded(),
            provisionedDevices,
            claimedDevices,
            rules.createdCount(),
            List.copyOf(warnings)
        );
    }

    @Override
    public BootstrapResponse bootstrapFull(BootstrapRequest request) {
        ReferenceSeedResult references = referenceSeedService.seedFullReferenceData();
        List<String> warnings = new ArrayList<>();
        List<BootstrappedDevice> devices = bootstrapDevices(buildFullPlans(references), warnings);
        AlertRuleBootstrapResult rules = alertRuleBootstrapService.bootstrapFullRules(references.sensorTypeIds(), devices);
        warnings.addAll(rules.warnings());
        int provisionedDevices = devices.stream().mapToInt(device -> device.provisioned() ? 1 : 0).sum();
        int claimedDevices = devices.stream().mapToInt(device -> device.claimed() ? 1 : 0).sum();

        log.info(
            "Completed full bootstrap: usersCreated={}, plotsCreated={}, zonesCreated={}, devicesProvisioned={}, devicesClaimed={}, rulesCreated={}",
            references.usersSeeded(),
            references.farmPlotsSeeded(),
            references.zonesSeeded(),
            provisionedDevices,
            claimedDevices,
            rules.createdCount()
        );

        return new BootstrapResponse(
            "full",
            references.usersSeeded(),
            references.farmPlotsSeeded(),
            references.zonesSeeded(),
            references.sensorTypesSeeded(),
            provisionedDevices,
            claimedDevices,
            rules.createdCount(),
            List.copyOf(warnings)
        );
    }

    private List<BootstrappedDevice> bootstrapDevices(List<DevicePlan> plans, List<String> warnings) {
        List<BootstrappedDevice> devices = new ArrayList<>();
        for (DevicePlan plan : plans) {
            try {
                devices.add(
                    deviceBootstrapService.bootstrapDevice(
                        plan.ownerUserId(),
                        plan.farmPlotId(),
                        plan.zoneId(),
                        plan.deviceUid(),
                        plan.deviceCode(),
                        plan.deviceName(),
                        plan.deviceType()
                    )
                );
            } catch (Exception exception) {
                warnings.add("Failed to bootstrap device " + plan.deviceUid() + ": " + exception.getMessage());
            }
        }
        return devices;
    }

    private List<DevicePlan> buildMinimalPlans(ReferenceSeedResult references) {
        UUID ownerUserId = references.userIds().getFirst();
        UUID farmPlotId = references.farmPlotIds().getFirst();
        return List.of(
            new DevicePlan(
                ownerUserId,
                farmPlotId,
                references.zoneIds().get(0),
                buildDeviceUid("minimal", 1),
                "MIN-001",
                "Minimal Zone 1 Sensor Hub",
                "ESP32"
            ),
            new DevicePlan(
                ownerUserId,
                farmPlotId,
                references.zoneIds().get(1),
                buildDeviceUid("minimal", 2),
                "MIN-002",
                "Minimal Zone 2 Sensor Hub",
                "ESP32"
            )
        );
    }

    private List<DevicePlan> buildFullPlans(ReferenceSeedResult references) {
        List<DevicePlan> plans = new ArrayList<>();
        List<UUID> userIds = references.userIds();
        List<UUID> farmPlotIds = references.farmPlotIds();

        for (int index = 0; index < references.zoneIds().size(); index++) {
            UUID ownerUserId = userIds.get(index % Math.min(2, userIds.size()));
            UUID farmPlotId = farmPlotIds.get(index % farmPlotIds.size());
            plans.add(new DevicePlan(
                ownerUserId,
                farmPlotId,
                references.zoneIds().get(index),
                buildDeviceUid("full", index + 1),
                "FULL-" + String.format("%03d", index + 1),
                "Full Demo Zone " + (index + 1) + " Sensor Hub",
                index % 2 == 0 ? "ESP32" : "RaspberryPi"
            ));
        }

        return List.copyOf(plans);
    }

    private String buildDeviceUid(String mode, int index) {
        return seedProperties.getMqtt().getNamespaceEnv() + "-" + mode + "-device-" + index;
    }

    private record DevicePlan(
        UUID ownerUserId,
        UUID farmPlotId,
        UUID zoneId,
        String deviceUid,
        String deviceCode,
        String deviceName,
        String deviceType
    ) {
    }
}
