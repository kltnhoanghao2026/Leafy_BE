package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.dto.ingest.TelemetryPayload;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorReadingSeries;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingSeriesRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorTypeRepository;
import com.leafy.iotmetricscollectorservice.service.AggregateLatestReadingService;
import com.leafy.iotmetricscollectorservice.service.AlertEvaluationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestServiceImplTest {

    @Mock
    private IoTDeviceRepository ioTDeviceRepository;

    @Mock
    private SensorTypeRepository sensorTypeRepository;

    @Mock
    private SensorReadingSeriesRepository sensorReadingSeriesRepository;

    @Mock
    private AggregateLatestReadingService aggregateLatestReadingService;

    @Mock
    private AlertEvaluationService alertEvaluationService;

    @InjectMocks
    private TelemetryIngestServiceImpl telemetryIngestService;

    @Test
    void ingest_savesRawReadingsBeforeUpdatingLatestSnapshotAndEvaluatesPersistedReadings() {
        IoTDevice device = new IoTDevice();
        device.setId(UUID.randomUUID());
        device.setDeviceUid("device-001");
        device.setIsActive(true);
        device.setOwnerUser(new UserRef());
        device.getOwnerUser().setId(UUID.randomUUID().toString());
        device.setZone(new FarmZoneRef());
        device.getZone().setId(UUID.randomUUID().toString());

        SensorType sensorType = new SensorType();
        sensorType.setId(UUID.randomUUID());
        sensorType.setCode("soilTemp");

        Instant readingTime = Instant.parse("2026-04-09T10:15:30Z");
        TelemetryPayload payload = new TelemetryPayload();
        payload.setTs(readingTime);
        payload.setMetrics(Map.of("soilTemp", 28.4d));

        when(ioTDeviceRepository.findByDeviceUid("device-001")).thenReturn(Optional.of(device));
        when(sensorTypeRepository.findByCode("soilTemp")).thenReturn(Optional.of(sensorType));
        when(sensorReadingSeriesRepository.saveAll(anyList())).thenAnswer(invocation -> new ArrayList<>(invocation.getArgument(0)));

        telemetryIngestService.ingest("device-001", payload);

        InOrder inOrder = inOrder(
            sensorReadingSeriesRepository,
            aggregateLatestReadingService,
            ioTDeviceRepository,
            alertEvaluationService
        );
        inOrder.verify(sensorReadingSeriesRepository).saveAll(anyList());
        inOrder.verify(aggregateLatestReadingService).updateLatestReadings(anyList());
        verify(ioTDeviceRepository).save(device);
        inOrder.verify(alertEvaluationService).evaluateReadings(anyList());
        assertEquals(readingTime, device.getLastSeenAt());
    }
}
