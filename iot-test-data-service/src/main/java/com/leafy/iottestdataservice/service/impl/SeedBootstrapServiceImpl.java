package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.dto.BootstrapRequest;
import com.leafy.iottestdataservice.dto.BootstrapResponse;
import com.leafy.iottestdataservice.model.AlertRuleBootstrapResult;
import com.leafy.iottestdataservice.model.BootstrappedDevice;
import com.leafy.iottestdataservice.model.ReferenceSeedResult;
import com.leafy.iottestdataservice.model.SeedTarget;
import com.leafy.iottestdataservice.service.AlertRuleBootstrapService;
import com.leafy.iottestdataservice.service.DeviceBootstrapService;
import com.leafy.iottestdataservice.service.ReferenceSeedService;
import com.leafy.iottestdataservice.service.SeedBootstrapService;
import java.util.ArrayList;
import java.util.List;
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
        ReferenceSeedResult references = referenceSeedService.seedMinimalReferenceData(request);
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
        ReferenceSeedResult references = referenceSeedService.seedFullReferenceData(request);
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
        List<DevicePlan> plans = new ArrayList<>();
        for (int index = 0; index < references.targets().size(); index++) {
            SeedTarget target = references.targets().get(index);
            plans.add(new DevicePlan(
                target.ownerUserId(),
                target.farmPlotId(),
                target.zoneId(),
                buildDeviceUid("minimal", index + 1),
                "MIN-" + String.format("%03d", index + 1),
                "Minimal Zone " + (index + 1) + " Sensor Hub",
                "ESP32"
            ));
        }
        return List.copyOf(plans);
    }

    private List<DevicePlan> buildFullPlans(ReferenceSeedResult references) {
        List<DevicePlan> plans = new ArrayList<>();
        for (int index = 0; index < references.targets().size(); index++) {
            SeedTarget target = references.targets().get(index);
            plans.add(new DevicePlan(
                target.ownerUserId(),
                target.farmPlotId(),
                target.zoneId(),
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
        String ownerUserId,
        String farmPlotId,
        String zoneId,
        String deviceUid,
        String deviceCode,
        String deviceName,
        String deviceType
    ) {
    }
}
