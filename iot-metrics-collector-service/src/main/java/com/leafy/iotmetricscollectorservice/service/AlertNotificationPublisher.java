package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.model.AlertEvent;

public interface AlertNotificationPublisher {
    void publishAlertTriggered(AlertEvent alertEvent);
}
