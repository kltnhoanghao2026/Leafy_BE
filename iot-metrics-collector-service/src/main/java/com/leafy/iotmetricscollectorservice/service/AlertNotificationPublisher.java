package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaAnalysis;

public interface AlertNotificationPublisher {
    void publishAlertTriggered(AlertEvent alertEvent);

    void publishAlertTriggered(AlertEvent alertEvent, Boolean notifyWeb, Boolean notifyMobile);

    void publishDiseaseAlertTriggered(
        AlertEvent alertEvent,
        DeviceMediaAnalysis analysis,
        Boolean notifyWeb,
        Boolean notifyMobile
    );
}
