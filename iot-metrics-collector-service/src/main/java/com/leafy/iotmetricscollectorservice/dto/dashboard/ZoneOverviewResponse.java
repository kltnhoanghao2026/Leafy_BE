package com.leafy.iotmetricscollectorservice.dto.dashboard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZoneOverviewResponse {
    private String zoneId;
    private Integer openAlerts;
    private Instant lastUpdatedAt;
    private AlertSummaryResponse alertSummary;
    private DeviceMediaSummaryResponse latestMedia;
    private List<LatestReadingItemResponse> latestReadings = new ArrayList<>();
}
