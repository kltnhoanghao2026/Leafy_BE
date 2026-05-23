package com.leafy.iotmetricscollectorservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IngestTimestampResolverTest {

    @Test
    void resolveLiveEventTime_usesReceivedAtWhenDeviceTimestampIsMissingOrStale() {
        Instant receivedAt = Instant.parse("2026-05-21T06:40:00Z");

        assertThat(IngestTimestampResolver.resolveLiveEventTime(null, receivedAt)).isEqualTo(receivedAt);
        assertThat(IngestTimestampResolver.resolveLiveEventTime(Instant.parse("1970-01-01T00:00:00Z"), receivedAt))
            .isEqualTo(receivedAt);
        assertThat(IngestTimestampResolver.resolveLiveEventTime(Instant.parse("2026-01-21T15:15:00Z"), receivedAt))
            .isEqualTo(receivedAt);
    }

    @Test
    void resolveLiveEventTime_keepsRecentDeviceTimestamp() {
        Instant receivedAt = Instant.parse("2026-05-21T06:40:00Z");
        Instant deviceTimestamp = Instant.parse("2026-05-21T06:39:58Z");

        assertThat(IngestTimestampResolver.resolveLiveEventTime(deviceTimestamp, receivedAt)).isEqualTo(deviceTimestamp);
    }

    @Test
    void resolveTelemetryTime_preservesHistoricalSeedTimestamp() {
        Instant receivedAt = Instant.parse("2026-05-21T06:40:00Z");
        Instant historicalTimestamp = Instant.parse("2026-01-21T15:15:00Z");

        assertThat(IngestTimestampResolver.resolveTelemetryTime(historicalTimestamp, receivedAt, "seed-history-1.0"))
            .isEqualTo(historicalTimestamp);
    }
}
