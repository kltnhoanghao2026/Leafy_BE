package com.leafy.iotmetricscollectorservice.service.impl;

import java.time.Duration;
import java.time.Instant;

/**
 * Normalizes timestamps provided by MQTT devices before they are persisted.
 *
 * Device RTC/NTP can be unavailable or stale, especially on ESP32-CAM boot.
 * In that case the device may omit time or publish an old placeholder. The
 * collector is the source of truth for UI status freshness, so live events use
 * the server receive time when the device timestamp is clearly not usable.
 */
final class IngestTimestampResolver {

    private static final Duration MAX_PAST_SKEW = Duration.ofDays(45);
    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);
    private static final Instant MIN_VALID_DEVICE_TIME = Instant.parse("2024-01-01T00:00:00Z");

    private IngestTimestampResolver() {
    }

    static Instant resolveLiveEventTime(Instant candidate, Instant receivedAt) {
        Instant fallback = receivedAt != null ? receivedAt : Instant.now();
        if (candidate == null) {
            return fallback;
        }
        if (candidate.isBefore(MIN_VALID_DEVICE_TIME)) {
            return fallback;
        }
        if (candidate.isBefore(fallback.minus(MAX_PAST_SKEW))) {
            return fallback;
        }
        if (candidate.isAfter(fallback.plus(MAX_FUTURE_SKEW))) {
            return fallback;
        }
        return candidate;
    }

    static Instant resolveTelemetryTime(Instant candidate, Instant receivedAt, String firmwareVersion) {
        if (isHistoricalSeed(firmwareVersion)) {
            return candidate != null ? candidate : (receivedAt != null ? receivedAt : Instant.now());
        }
        return resolveLiveEventTime(candidate, receivedAt);
    }

    private static boolean isHistoricalSeed(String firmwareVersion) {
        return firmwareVersion != null && firmwareVersion.toLowerCase().startsWith("seed-history");
    }
}
