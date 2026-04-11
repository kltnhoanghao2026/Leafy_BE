package com.leafy.iotmetricscollectorservice.dto.dashboard;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlertSummaryResponse {
    private Integer openAlerts;
    private Integer highSeverityAlerts;
    private Integer criticalAlerts;
    private Instant latestAlertAt;
}
