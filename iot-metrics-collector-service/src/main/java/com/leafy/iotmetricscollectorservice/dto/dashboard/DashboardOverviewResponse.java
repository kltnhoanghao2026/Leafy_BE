package com.leafy.iotmetricscollectorservice.dto.dashboard;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardOverviewResponse {
    private String farmPlotId;
    private Long totalDevices;
    private Long onlineDevices;
    private Long offlineDevices;
    private Long totalZones;
    private Long openAlerts;
    private Instant lastUpdatedAt;
}
