package com.leafy.iotmetricscollectorservice.dto.dashboard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZoneOverviewResponse {
    private UUID zoneId;
    private Integer openAlerts;
    private Instant lastUpdatedAt;
    private List<LatestReadingItemResponse> latestReadings = new ArrayList<>();
}
