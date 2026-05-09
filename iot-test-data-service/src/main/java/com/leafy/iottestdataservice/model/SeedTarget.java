package com.leafy.iottestdataservice.model;

public record SeedTarget(
    String ownerUserId,
    String profileId,
    String farmPlotId,
    String zoneId
) {
}
