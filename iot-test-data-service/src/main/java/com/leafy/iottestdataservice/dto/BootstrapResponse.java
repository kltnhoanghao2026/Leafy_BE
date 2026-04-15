package com.leafy.iottestdataservice.dto;

import java.util.List;

public record BootstrapResponse(
    String mode,
    int createdUsers,
    int createdFarmPlots,
    int createdZones,
    int createdSensorTypes,
    int provisionedDevices,
    int claimedDevices,
    int createdAlertRules,
    List<String> warnings
) {
}
