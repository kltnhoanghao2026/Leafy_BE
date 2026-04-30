package com.leafy.iotmetricscollectorservice.dto.dashboard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceDetailResponse {
    private UUID deviceId;
    private String deviceUid;
    private String deviceCode;
    private String deviceName;
    private String deviceType;
    private String firmwareVersion;
    private String status;
    private String provisioningStatus;
    private Boolean isActive;
    private String ownerUserId;
    private String farmPlotId;
    private String zoneId;
    private Instant lastSeenAt;
    private AlertSummaryResponse alertSummary;
    private DeviceConfigSnapshotResponse config;
    private DeviceMediaSummaryResponse latestMedia;
    private List<LatestReadingItemResponse> latestReadings = new ArrayList<>();
}
