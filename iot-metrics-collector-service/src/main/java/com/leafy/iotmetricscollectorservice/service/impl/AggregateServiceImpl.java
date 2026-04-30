package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorReadingSeries;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.aggregate.SensorReadingAgg1d;
import com.leafy.iotmetricscollectorservice.model.aggregate.SensorReadingAgg1h;
import com.leafy.iotmetricscollectorservice.model.aggregate.SensorReadingAgg5m;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingAgg1dRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingAgg1hRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingAgg5mRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingSeriesRepository;
import com.leafy.iotmetricscollectorservice.service.AggregateService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AggregateServiceImpl implements AggregateService {

    private static final long FIVE_MINUTE_BUCKET_SECONDS = Duration.ofMinutes(5).toSeconds();
    private static final long ONE_HOUR_BUCKET_SECONDS = Duration.ofHours(1).toSeconds();
    private static final long ONE_DAY_BUCKET_SECONDS = Duration.ofDays(1).toSeconds();

    private final SensorReadingSeriesRepository sensorReadingSeriesRepository;
    private final SensorReadingAgg5mRepository sensorReadingAgg5mRepository;
    private final SensorReadingAgg1hRepository sensorReadingAgg1hRepository;
    private final SensorReadingAgg1dRepository sensorReadingAgg1dRepository;

    @Override
    @Transactional
    public void rebuild5mWindow(Instant from, Instant to) {
        validateWindow(from, to);

        List<SensorReadingSeries> readings = sensorReadingSeriesRepository
            .findAllByReadingTimeGreaterThanEqualAndReadingTimeLessThanOrderByReadingTimeAsc(from, to);

        if (readings.isEmpty()) {
            return;
        }

        Map<AggregateBucketKey, AggregateAccumulator> groupedReadings = new LinkedHashMap<>();
        for (SensorReadingSeries reading : readings) {
            AggregateBucketKey bucketKey = toBucketKey(reading);
            groupedReadings.compute(
                bucketKey,
                (ignored, accumulator) -> {
                    AggregateAccumulator currentAccumulator = accumulator != null
                        ? accumulator
                        : AggregateAccumulator.from(reading);
                    return currentAccumulator.add(reading.getReadingValue());
                }
            );
        }

        Instant rebuildTime = Instant.now();
        List<SensorReadingAgg5m> aggregateRows = new ArrayList<>(groupedReadings.size());

        for (Map.Entry<AggregateBucketKey, AggregateAccumulator> entry : groupedReadings.entrySet()) {
            AggregateBucketKey bucketKey = entry.getKey();
            AggregateAccumulator accumulator = entry.getValue();

            SensorReadingAgg5m aggregateRow = findExistingAggregate(bucketKey)
                .orElseGet(SensorReadingAgg5m::new);

            if (aggregateRow.getId() == null) {
                aggregateRow.setDevice(accumulator.device());
                aggregateRow.setSensorType(accumulator.sensorType());
                aggregateRow.setZone(accumulator.zone());
                aggregateRow.setBucketStart(bucketKey.bucketStart());
                aggregateRow.setBucketEnd(bucketKey.bucketEnd());
                aggregateRow.setCreatedAt(rebuildTime);
            }

            aggregateRow.setMinValue(accumulator.minValue());
            aggregateRow.setMaxValue(accumulator.maxValue());
            aggregateRow.setAvgValue(accumulator.avgValue());
            aggregateRow.setSampleCount(accumulator.sampleCount());
            aggregateRows.add(aggregateRow);
        }

        sensorReadingAgg5mRepository.saveAll(aggregateRows);
    }

    @Override
    @Transactional
    public void rebuild1hWindow(Instant from, Instant to) {
        validateWindow(from, to);

        List<SensorReadingAgg5m> sourceAggregates = sensorReadingAgg5mRepository
            .findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to);

        if (sourceAggregates.isEmpty()) {
            return;
        }

        Map<AggregateBucketKey, WeightedAggregateAccumulator> groupedAggregates = new LinkedHashMap<>();
        for (SensorReadingAgg5m sourceAggregate : sourceAggregates) {
            Integer sourceSampleCount = sourceAggregate.getSampleCount();
            if (sourceSampleCount == null || sourceSampleCount <= 0) {
                continue;
            }

            AggregateBucketKey bucketKey = toHourlyBucketKey(sourceAggregate);
            groupedAggregates.compute(
                bucketKey,
                (ignored, accumulator) -> {
                    WeightedAggregateAccumulator currentAccumulator = accumulator != null
                        ? accumulator
                        : WeightedAggregateAccumulator.from(sourceAggregate);
                    return currentAccumulator.add(sourceAggregate);
                }
            );
        }

        if (groupedAggregates.isEmpty()) {
            return;
        }

        Instant rebuildTime = Instant.now();
        List<SensorReadingAgg1h> aggregateRows = new ArrayList<>(groupedAggregates.size());

        for (Map.Entry<AggregateBucketKey, WeightedAggregateAccumulator> entry : groupedAggregates.entrySet()) {
            AggregateBucketKey bucketKey = entry.getKey();
            WeightedAggregateAccumulator accumulator = entry.getValue();

            SensorReadingAgg1h aggregateRow = findExistingHourlyAggregate(bucketKey)
                .orElseGet(SensorReadingAgg1h::new);

            if (aggregateRow.getId() == null) {
                aggregateRow.setDevice(accumulator.device());
                aggregateRow.setSensorType(accumulator.sensorType());
                aggregateRow.setZone(accumulator.zone());
                aggregateRow.setBucketStart(bucketKey.bucketStart());
                aggregateRow.setBucketEnd(bucketKey.bucketEnd());
                aggregateRow.setCreatedAt(rebuildTime);
            }

            aggregateRow.setMinValue(accumulator.minValue());
            aggregateRow.setMaxValue(accumulator.maxValue());
            aggregateRow.setAvgValue(accumulator.avgValue());
            aggregateRow.setSampleCount(accumulator.sampleCount());
            aggregateRows.add(aggregateRow);
        }

        sensorReadingAgg1hRepository.saveAll(aggregateRows);
    }

    @Override
    @Transactional
    public void rebuild1dWindow(Instant from, Instant to) {
        validateWindow(from, to);

        List<SensorReadingAgg1h> sourceAggregates = sensorReadingAgg1hRepository
            .findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to);

        if (sourceAggregates.isEmpty()) {
            return;
        }

        Map<AggregateBucketKey, WeightedAggregateAccumulator> groupedAggregates = new LinkedHashMap<>();
        for (SensorReadingAgg1h sourceAggregate : sourceAggregates) {
            Integer sourceSampleCount = sourceAggregate.getSampleCount();
            if (sourceSampleCount == null || sourceSampleCount <= 0) {
                continue;
            }

            AggregateBucketKey bucketKey = toDailyBucketKey(sourceAggregate);
            groupedAggregates.compute(
                bucketKey,
                (ignored, accumulator) -> {
                    WeightedAggregateAccumulator currentAccumulator = accumulator != null
                        ? accumulator
                        : WeightedAggregateAccumulator.from(sourceAggregate);
                    return currentAccumulator.add(sourceAggregate);
                }
            );
        }

        if (groupedAggregates.isEmpty()) {
            return;
        }

        Instant rebuildTime = Instant.now();
        List<SensorReadingAgg1d> aggregateRows = new ArrayList<>(groupedAggregates.size());

        for (Map.Entry<AggregateBucketKey, WeightedAggregateAccumulator> entry : groupedAggregates.entrySet()) {
            AggregateBucketKey bucketKey = entry.getKey();
            WeightedAggregateAccumulator accumulator = entry.getValue();

            SensorReadingAgg1d aggregateRow = findExistingDailyAggregate(bucketKey)
                .orElseGet(SensorReadingAgg1d::new);

            if (aggregateRow.getId() == null) {
                aggregateRow.setDevice(accumulator.device());
                aggregateRow.setSensorType(accumulator.sensorType());
                aggregateRow.setZone(accumulator.zone());
                aggregateRow.setBucketStart(bucketKey.bucketStart());
                aggregateRow.setBucketEnd(bucketKey.bucketEnd());
                aggregateRow.setCreatedAt(rebuildTime);
            }

            aggregateRow.setMinValue(accumulator.minValue());
            aggregateRow.setMaxValue(accumulator.maxValue());
            aggregateRow.setAvgValue(accumulator.avgValue());
            aggregateRow.setSampleCount(accumulator.sampleCount());
            aggregateRows.add(aggregateRow);
        }

        sensorReadingAgg1dRepository.saveAll(aggregateRows);
    }

    private void validateWindow(Instant from, Instant to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");

        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }

    private AggregateBucketKey toBucketKey(SensorReadingSeries reading) {
        String zoneId = reading.getZone() != null ? reading.getZone().getId() : null;
        Instant bucketStart = alignTo5MinuteBucketStart(reading.getReadingTime());
        Instant bucketEnd = compute5MinuteBucketEnd(bucketStart);

        return new AggregateBucketKey(
            reading.getDevice().getId(),
            reading.getSensorType().getId(),
            zoneId,
            bucketStart,
            bucketEnd
        );
    }

    private Instant alignTo5MinuteBucketStart(Instant timestamp) {
        long alignedEpochSecond = Math.floorDiv(timestamp.getEpochSecond(), FIVE_MINUTE_BUCKET_SECONDS)
            * FIVE_MINUTE_BUCKET_SECONDS;
        return Instant.ofEpochSecond(alignedEpochSecond);
    }

    private Instant alignTo1HourBucketStart(Instant timestamp) {
        long alignedEpochSecond = Math.floorDiv(timestamp.getEpochSecond(), ONE_HOUR_BUCKET_SECONDS)
            * ONE_HOUR_BUCKET_SECONDS;
        return Instant.ofEpochSecond(alignedEpochSecond);
    }

    private Instant alignTo1DayBucketStart(Instant timestamp) {
        long alignedEpochSecond = Math.floorDiv(timestamp.getEpochSecond(), ONE_DAY_BUCKET_SECONDS)
            * ONE_DAY_BUCKET_SECONDS;
        return Instant.ofEpochSecond(alignedEpochSecond);
    }

    private Instant compute5MinuteBucketEnd(Instant bucketStart) {
        return bucketStart.plus(Duration.ofMinutes(5));
    }

    private Instant compute1HourBucketEnd(Instant bucketStart) {
        return bucketStart.plus(Duration.ofHours(1));
    }

    private Instant compute1DayBucketEnd(Instant bucketStart) {
        return bucketStart.plus(Duration.ofDays(1));
    }

    private Optional<SensorReadingAgg5m> findExistingAggregate(AggregateBucketKey bucketKey) {
        if (bucketKey.zoneId() == null) {
            return sensorReadingAgg5mRepository.findByDeviceIdAndSensorTypeIdAndZoneIsNullAndBucketStartAndBucketEnd(
                bucketKey.deviceId(),
                bucketKey.sensorTypeId(),
                bucketKey.bucketStart(),
                bucketKey.bucketEnd()
            );
        }

        return sensorReadingAgg5mRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            bucketKey.deviceId(),
            bucketKey.sensorTypeId(),
            bucketKey.zoneId(),
            bucketKey.bucketStart(),
            bucketKey.bucketEnd()
        );
    }

    private AggregateBucketKey toHourlyBucketKey(SensorReadingAgg5m aggregateReading) {
        String zoneId = aggregateReading.getZone() != null ? aggregateReading.getZone().getId() : null;
        Instant bucketStart = alignTo1HourBucketStart(aggregateReading.getBucketStart());
        Instant bucketEnd = compute1HourBucketEnd(bucketStart);

        return new AggregateBucketKey(
            aggregateReading.getDevice().getId(),
            aggregateReading.getSensorType().getId(),
            zoneId,
            bucketStart,
            bucketEnd
        );
    }

    private Optional<SensorReadingAgg1h> findExistingHourlyAggregate(AggregateBucketKey bucketKey) {
        if (bucketKey.zoneId() == null) {
            return sensorReadingAgg1hRepository.findByDeviceIdAndSensorTypeIdAndZoneIsNullAndBucketStartAndBucketEnd(
                bucketKey.deviceId(),
                bucketKey.sensorTypeId(),
                bucketKey.bucketStart(),
                bucketKey.bucketEnd()
            );
        }

        return sensorReadingAgg1hRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            bucketKey.deviceId(),
            bucketKey.sensorTypeId(),
            bucketKey.zoneId(),
            bucketKey.bucketStart(),
            bucketKey.bucketEnd()
        );
    }

    private AggregateBucketKey toDailyBucketKey(SensorReadingAgg1h aggregateReading) {
        String zoneId = aggregateReading.getZone() != null ? aggregateReading.getZone().getId() : null;
        Instant bucketStart = alignTo1DayBucketStart(aggregateReading.getBucketStart());
        Instant bucketEnd = compute1DayBucketEnd(bucketStart);

        return new AggregateBucketKey(
            aggregateReading.getDevice().getId(),
            aggregateReading.getSensorType().getId(),
            zoneId,
            bucketStart,
            bucketEnd
        );
    }

    private Optional<SensorReadingAgg1d> findExistingDailyAggregate(AggregateBucketKey bucketKey) {
        if (bucketKey.zoneId() == null) {
            return sensorReadingAgg1dRepository.findByDeviceIdAndSensorTypeIdAndZoneIsNullAndBucketStartAndBucketEnd(
                bucketKey.deviceId(),
                bucketKey.sensorTypeId(),
                bucketKey.bucketStart(),
                bucketKey.bucketEnd()
            );
        }

        return sensorReadingAgg1dRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            bucketKey.deviceId(),
            bucketKey.sensorTypeId(),
            bucketKey.zoneId(),
            bucketKey.bucketStart(),
            bucketKey.bucketEnd()
        );
    }

    private record AggregateBucketKey(
        UUID deviceId,
        UUID sensorTypeId,
        String zoneId,
        Instant bucketStart,
        Instant bucketEnd
    ) {
    }

    private record AggregateAccumulator(
        IoTDevice device,
        SensorType sensorType,
        FarmZoneRef zone,
        double sumValue,
        double minValue,
        double maxValue,
        int sampleCount
    ) {

        private static AggregateAccumulator from(SensorReadingSeries reading) {
            return new AggregateAccumulator(
                reading.getDevice(),
                reading.getSensorType(),
                reading.getZone(),
                0d,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                0
            );
        }

        private AggregateAccumulator add(Double readingValue) {
            double value = readingValue;
            return new AggregateAccumulator(
                device,
                sensorType,
                zone,
                sumValue + value,
                Math.min(minValue, value),
                Math.max(maxValue, value),
                sampleCount + 1
            );
        }

        private double avgValue() {
            return sumValue / sampleCount;
        }
    }

    private record WeightedAggregateAccumulator(
        IoTDevice device,
        SensorType sensorType,
        FarmZoneRef zone,
        double weightedSum,
        double minValue,
        double maxValue,
        int sampleCount
    ) {

        private static WeightedAggregateAccumulator from(SensorReadingAgg5m aggregateReading) {
            return new WeightedAggregateAccumulator(
                aggregateReading.getDevice(),
                aggregateReading.getSensorType(),
                aggregateReading.getZone(),
                0d,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                0
            );
        }

        private WeightedAggregateAccumulator add(SensorReadingAgg5m aggregateReading) {
            int sourceSampleCount = aggregateReading.getSampleCount();
            return new WeightedAggregateAccumulator(
                device,
                sensorType,
                zone,
                weightedSum + (aggregateReading.getAvgValue() * sourceSampleCount),
                Math.min(minValue, aggregateReading.getMinValue()),
                Math.max(maxValue, aggregateReading.getMaxValue()),
                sampleCount + sourceSampleCount
            );
        }

        private static WeightedAggregateAccumulator from(SensorReadingAgg1h aggregateReading) {
            return new WeightedAggregateAccumulator(
                aggregateReading.getDevice(),
                aggregateReading.getSensorType(),
                aggregateReading.getZone(),
                0d,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                0
            );
        }

        private WeightedAggregateAccumulator add(SensorReadingAgg1h aggregateReading) {
            int sourceSampleCount = aggregateReading.getSampleCount();
            return new WeightedAggregateAccumulator(
                device,
                sensorType,
                zone,
                weightedSum + (aggregateReading.getAvgValue() * sourceSampleCount),
                Math.min(minValue, aggregateReading.getMinValue()),
                Math.max(maxValue, aggregateReading.getMaxValue()),
                sampleCount + sourceSampleCount
            );
        }

        private double avgValue() {
            return weightedSum / sampleCount;
        }
    }
}
