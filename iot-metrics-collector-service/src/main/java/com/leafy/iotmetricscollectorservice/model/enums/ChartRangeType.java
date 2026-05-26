package com.leafy.iotmetricscollectorservice.model.enums;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

public enum ChartRangeType {
    H1(Duration.ofHours(1), AggregateSource.AGG_5M, Set.of("H1", "1H")),
    H24(Duration.ofHours(24), AggregateSource.AGG_5M, Set.of("H24", "24H")),
    D3(Duration.ofDays(3), AggregateSource.AGG_5M, Set.of("D3", "3D")),
    D7(Duration.ofDays(7), AggregateSource.AGG_1H, Set.of("D7", "7D")),
    D30(Duration.ofDays(30), AggregateSource.AGG_1H, Set.of("D30", "30D")),
    D90(Duration.ofDays(90), AggregateSource.AGG_1D, Set.of("D90", "90D"));

    private final Duration lookback;
    private final AggregateSource aggregateSource;
    private final Set<String> aliases;

    ChartRangeType(Duration lookback, AggregateSource aggregateSource, Set<String> aliases) {
        this.lookback = lookback;
        this.aggregateSource = aggregateSource;
        this.aliases = aliases;
    }

    public Instant resolveFrom(Instant to) {
        return to.minus(lookback);
    }

    public AggregateSource getAggregateSource() {
        return aggregateSource;
    }

    public static ChartRangeType fromValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Chart range is required");
        }

        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        for (ChartRangeType rangeType : values()) {
            if (rangeType.aliases.contains(normalized)) {
                return rangeType;
            }
        }

        throw new IllegalArgumentException("Unsupported chart range: " + rawValue);
    }

    public enum AggregateSource {
        AGG_5M,
        AGG_1H,
        AGG_1D
    }
}
