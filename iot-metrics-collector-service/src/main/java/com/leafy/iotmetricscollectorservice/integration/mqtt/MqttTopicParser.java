package com.leafy.iotmetricscollectorservice.integration.mqtt;

import com.leafy.iotmetricscollectorservice.integration.mqtt.DeviceTopicInfo;
import java.util.Optional;

public final class MqttTopicParser {

    private MqttTopicParser() {
    }

    /**
     * Supported topics:
     * - coffee/prod/devices/{deviceUid}/telemetry
     * - coffee/prod/devices/{deviceUid}/status
     * - coffee/prod/devices/{deviceUid}/ack
     * - coffee/prod/devices/{deviceUid}/image/meta
     */
    public static Optional<DeviceTopicInfo> parse(String topic) {
        if (topic == null || topic.isBlank()) {
            return Optional.empty();
        }

        String[] parts = topic.split("/");
        if (parts.length < 5) {
            return Optional.empty();
        }

        // Expected prefix: coffee/{env}/devices/{deviceUid}/...
        if (!"coffee".equals(parts[0])) {
            return Optional.empty();
        }

        if (!"devices".equals(parts[2])) {
            return Optional.empty();
        }

        String deviceUid = parts[3];
        if (deviceUid == null || deviceUid.isBlank()) {
            return Optional.empty();
        }

        if (parts.length == 5) {
            String messageType = parts[4];
            if (isSupportedSingleSegmentType(messageType)) {
                return Optional.of(new DeviceTopicInfo(deviceUid, messageType));
            }
            return Optional.empty();
        }

        if (parts.length == 6 && "image".equals(parts[4]) && "meta".equals(parts[5])) {
            return Optional.of(new DeviceTopicInfo(deviceUid, "image/meta"));
        }

        return Optional.empty();
    }

    private static boolean isSupportedSingleSegmentType(String messageType) {
        return "telemetry".equals(messageType)
                || "status".equals(messageType)
                || "ack".equals(messageType);
    }
}