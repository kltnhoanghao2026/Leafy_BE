package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventDetailResponse;
import java.util.UUID;

public interface AlertLifecycleService {

    AlertEventDetailResponse acknowledgeAlert(UUID alertEventId);

    AlertEventDetailResponse resolveAlert(UUID alertEventId);
}
