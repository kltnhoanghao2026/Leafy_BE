package com.leafy.iotmetricscollectorservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.dto.DeviceCameraScheduleRequest;
import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureRequest;
import com.leafy.iotmetricscollectorservice.dto.media.CaptureQuality;
import com.leafy.iotmetricscollectorservice.dto.media.CaptureResolution;
import com.leafy.iotmetricscollectorservice.entity.DeviceCameraSchedule;
import com.leafy.iotmetricscollectorservice.entity.Recurrence;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import com.leafy.iotmetricscollectorservice.repository.DeviceCameraScheduleRepository;
import com.leafy.iotmetricscollectorservice.repository.DeviceMediaEventRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.service.DeviceCameraCaptureService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class DeviceCameraScheduleServiceImplTest {

    @Mock
    private DeviceCameraScheduleRepository scheduleRepository;

    @Mock
    private IoTDeviceRepository deviceRepository;

    @Mock
    private DeviceMediaEventRepository mediaEventRepository;

    @Mock
    private DeviceCameraCaptureService cameraCaptureService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private DeviceCameraScheduleServiceImpl service;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-15T08:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new DeviceCameraScheduleServiceImpl(
            scheduleRepository,
            deviceRepository,
            mediaEventRepository,
            cameraCaptureService,
            fixedClock,
            transactionManager
        );
    }

    @Test
    void createSchedule_validatesDeviceAndComputesNextRunAt() {
        String deviceUid = "device-001";
        when(deviceRepository.existsByDeviceUid(deviceUid)).thenReturn(true);
        when(scheduleRepository.save(any(DeviceCameraSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceCameraScheduleRequest request = request(deviceUid, LocalTime.of(9, 30), Recurrence.DAILY);

        var response = service.createSchedule(request);

        assertThat(response.getDeviceUid()).isEqualTo(deviceUid);
        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getTriggerType()).isEqualTo(TriggerType.SCHEDULED);
        assertThat(response.getNextRunAt()).isAfter(Instant.now(fixedClock));
    }

    @Test
    void createSchedule_missingDeviceThrowsModuleException() {
        String deviceUid = "missing-device";
        when(deviceRepository.existsByDeviceUid(deviceUid)).thenReturn(false);

        assertThatThrownBy(() -> service.createSchedule(request(deviceUid, LocalTime.NOON, Recurrence.DAILY)))
            .isInstanceOf(TelemetryQueryException.class)
            .hasMessageContaining("IoT device not found");
    }

    @Test
    void updateSchedule_recomputesNextRunAt() {
        UUID scheduleId = UUID.randomUUID();
        String deviceUid = "device-001";
        DeviceCameraSchedule existing = schedule(scheduleId, deviceUid);
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existing));
        when(deviceRepository.existsByDeviceUid(deviceUid)).thenReturn(true);
        when(scheduleRepository.save(any(DeviceCameraSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateSchedule(scheduleId, request(deviceUid, LocalTime.of(10, 0), Recurrence.WEEKLY));

        assertThat(response.getTimeOfDay()).isEqualTo(LocalTime.of(10, 0));
        assertThat(response.getRecurrence()).isEqualTo(Recurrence.WEEKLY);
        assertThat(response.getNextRunAt()).isAfter(Instant.now(fixedClock));
    }

    @Test
    void triggerSchedules_runsDueSchedulesAndAdvancesRunState() {
        UUID scheduleId = UUID.randomUUID();
        String deviceUid = "device-001";
        DeviceCameraSchedule due = schedule(scheduleId, deviceUid);
        due.setNextRunAt(Instant.parse("2026-05-15T07:59:00Z"));
        when(scheduleRepository.findDueScheduleIds(any())).thenReturn(List.of(scheduleId));
        when(scheduleRepository.findLockedById(scheduleId)).thenReturn(Optional.of(due));
        when(scheduleRepository.save(any(DeviceCameraSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.triggerSchedules();

        verify(cameraCaptureService).requestCapture(org.mockito.ArgumentMatchers.eq(deviceUid), any(CameraCaptureRequest.class), org.mockito.ArgumentMatchers.eq(TriggerType.SCHEDULED));
        assertThat(due.getLastRunAt()).isEqualTo(Instant.now(fixedClock));
        assertThat(due.getNextRunAt()).isAfter(Instant.now(fixedClock));
    }

    @Test
    void triggerSchedules_logsFailureAndContinuesRemainingSchedules() {
        String failingDevice = "device-failing";
        String succeedingDevice = "device-succeeding";
        DeviceCameraSchedule failing = schedule(UUID.randomUUID(), failingDevice);
        DeviceCameraSchedule succeeding = schedule(UUID.randomUUID(), succeedingDevice);
        failing.setNextRunAt(Instant.parse("2026-05-15T07:59:00Z"));
        succeeding.setNextRunAt(Instant.parse("2026-05-15T07:59:00Z"));
        when(scheduleRepository.findDueScheduleIds(any())).thenReturn(List.of(failing.getId(), succeeding.getId()));
        when(scheduleRepository.findLockedById(failing.getId())).thenReturn(Optional.of(failing));
        when(scheduleRepository.findLockedById(succeeding.getId())).thenReturn(Optional.of(succeeding));
        when(scheduleRepository.save(any(DeviceCameraSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("mqtt down")).when(cameraCaptureService)
            .requestCapture(org.mockito.ArgumentMatchers.eq(failingDevice), any(CameraCaptureRequest.class), org.mockito.ArgumentMatchers.eq(TriggerType.SCHEDULED));

        service.triggerSchedules();

        verify(cameraCaptureService).requestCapture(org.mockito.ArgumentMatchers.eq(failingDevice), any(CameraCaptureRequest.class), org.mockito.ArgumentMatchers.eq(TriggerType.SCHEDULED));
        verify(cameraCaptureService).requestCapture(org.mockito.ArgumentMatchers.eq(succeedingDevice), any(CameraCaptureRequest.class), org.mockito.ArgumentMatchers.eq(TriggerType.SCHEDULED));
        assertThat(succeeding.getLastRunAt()).isEqualTo(Instant.now(fixedClock));
    }

    @Test
    void runNow_disablesOneShotScheduleAfterAttempt() {
        UUID scheduleId = UUID.randomUUID();
        String deviceUid = "device-001";
        DeviceCameraSchedule oneShot = schedule(scheduleId, deviceUid);
        oneShot.setRecurrence(Recurrence.NONE);
        when(scheduleRepository.findLockedById(scheduleId)).thenReturn(Optional.of(oneShot));
        when(scheduleRepository.save(any(DeviceCameraSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.runNow(scheduleId);

        assertThat(response.isEnabled()).isFalse();
        assertThat(response.getNextRunAt()).isNull();
        verify(cameraCaptureService).requestCapture(org.mockito.ArgumentMatchers.eq(deviceUid), any(CameraCaptureRequest.class), org.mockito.ArgumentMatchers.eq(TriggerType.SCHEDULED));
    }

    @Test
    void tryAcquireSchedule_locksDueScheduleAndTriggersOnce() {
        UUID scheduleId = UUID.randomUUID();
        String deviceUid = "device-001";
        DeviceCameraSchedule due = schedule(scheduleId, deviceUid);
        due.setNextRunAt(Instant.parse("2026-05-15T07:59:00Z"));
        when(scheduleRepository.findLockedById(scheduleId)).thenReturn(Optional.of(due));
        when(scheduleRepository.save(any(DeviceCameraSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean acquired = service.tryAcquireSchedule(scheduleId);

        assertThat(acquired).isTrue();
        verify(cameraCaptureService).requestCapture(org.mockito.ArgumentMatchers.eq(deviceUid), any(CameraCaptureRequest.class), org.mockito.ArgumentMatchers.eq(TriggerType.SCHEDULED));
        assertThat(due.getLastRunAt()).isEqualTo(Instant.now(fixedClock));
        assertThat(due.getNextRunAt()).isAfter(Instant.now(fixedClock));
    }

    @Test
    void tryAcquireSchedule_skipsScheduleThatAnotherCollectorAlreadyAdvanced() {
        UUID scheduleId = UUID.randomUUID();
        String deviceUid = "device-001";
        DeviceCameraSchedule schedule = schedule(scheduleId, deviceUid);
        schedule.setNextRunAt(Instant.parse("2026-05-15T07:59:00Z"));
        when(scheduleRepository.findLockedById(scheduleId)).thenReturn(Optional.of(schedule), Optional.of(schedule));
        when(scheduleRepository.save(any(DeviceCameraSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean firstCollectorAcquired = service.tryAcquireSchedule(scheduleId);
        boolean secondCollectorAcquired = service.tryAcquireSchedule(scheduleId);

        assertThat(firstCollectorAcquired).isTrue();
        assertThat(secondCollectorAcquired).isFalse();
        verify(cameraCaptureService, times(1)).requestCapture(org.mockito.ArgumentMatchers.eq(deviceUid), any(CameraCaptureRequest.class), org.mockito.ArgumentMatchers.eq(TriggerType.SCHEDULED));
    }

    @Test
    void tryAcquireSchedule_returnsFalseWhenDatabaseLockIsUnavailable() {
        UUID scheduleId = UUID.randomUUID();
        when(scheduleRepository.findLockedById(scheduleId))
            .thenThrow(new PessimisticLockingFailureException("row already locked"));

        boolean acquired = service.tryAcquireSchedule(scheduleId);

        assertThat(acquired).isFalse();
        verify(cameraCaptureService, never()).requestCapture(any(String.class), any(TriggerType.class));
    }

    @Test
    void deleteSchedule_deletesExistingSchedule() {
        UUID scheduleId = UUID.randomUUID();
        DeviceCameraSchedule existing = schedule(scheduleId, "device-001");
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existing));

        service.deleteSchedule(scheduleId);

        verify(scheduleRepository).delete(existing);
    }

    @Test
    void createSchedule_savesExpectedEntity() {
        String deviceUid = "device-001";
        when(deviceRepository.existsByDeviceUid(deviceUid)).thenReturn(true);
        when(scheduleRepository.save(any(DeviceCameraSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceCameraScheduleRequest request = request(deviceUid, LocalTime.of(6, 0), Recurrence.DAILY);
        request.setResolution("HD");
        request.setQuality("HIGH");
        request.setUploadEndpoint("https://files.example.com/files/upload");

        service.createSchedule(request);

        ArgumentCaptor<DeviceCameraSchedule> captor = ArgumentCaptor.forClass(DeviceCameraSchedule.class);
        verify(scheduleRepository).save(captor.capture());
        assertThat(captor.getValue().getDeviceUid()).isEqualTo(deviceUid);
        assertThat(captor.getValue().getTimeOfDay()).isEqualTo(LocalTime.of(6, 0));
        assertThat(captor.getValue().getResolution()).isEqualTo("HD");
        assertThat(captor.getValue().getQuality()).isEqualTo("HIGH");
        assertThat(captor.getValue().getUploadEndpoint()).isEqualTo("https://files.example.com/files/upload");
    }

    @Test
    void runScheduleNow_sendsPersistedCaptureOptions() {
        UUID scheduleId = UUID.randomUUID();
        String deviceUid = "device-001";
        DeviceCameraSchedule existing = schedule(scheduleId, deviceUid);
        existing.setResolution("QVGA");
        existing.setQuality("LOW");
        existing.setUploadEndpoint("https://files.example.com/custom-upload");
        when(scheduleRepository.findByIdAndDeviceUid(scheduleId, deviceUid)).thenReturn(Optional.of(existing));
        when(scheduleRepository.findLockedById(scheduleId)).thenReturn(Optional.of(existing));
        when(scheduleRepository.save(any(DeviceCameraSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.runScheduleNow(deviceUid, scheduleId);

        ArgumentCaptor<CameraCaptureRequest> captor = ArgumentCaptor.forClass(CameraCaptureRequest.class);
        verify(cameraCaptureService).requestCapture(
            org.mockito.ArgumentMatchers.eq(deviceUid),
            captor.capture(),
            org.mockito.ArgumentMatchers.eq(TriggerType.SCHEDULED)
        );
        assertThat(captor.getValue().getResolution()).isEqualTo(CaptureResolution.QVGA);
        assertThat(captor.getValue().getQuality()).isEqualTo(CaptureQuality.LOW);
        assertThat(captor.getValue().getUploadEndpoint()).isEqualTo("https://files.example.com/custom-upload");
    }

    @Test
    void updateScheduleForDevice_rejectsScheduleFromAnotherDevice() {
        UUID scheduleId = UUID.randomUUID();
        when(scheduleRepository.findByIdAndDeviceUid(scheduleId, "device-001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateScheduleForDevice("device-001", scheduleId, request("device-001", LocalTime.NOON, Recurrence.DAILY)))
            .isInstanceOf(TelemetryQueryException.class)
            .hasMessageContaining("Device camera schedule not found");
    }

    @Test
    void createSchedule_rejectsInvalidCaptureOptions() {
        DeviceCameraScheduleRequest request = request("device-001", LocalTime.NOON, Recurrence.DAILY);
        request.setResolution("4K");

        assertThatThrownBy(() -> service.createSchedule(request))
            .isInstanceOf(TelemetryQueryException.class)
            .hasMessageContaining("resolution must be one of QVGA, VGA, HD");
    }

    private DeviceCameraScheduleRequest request(String deviceUid, LocalTime time, Recurrence recurrence) {
        DeviceCameraScheduleRequest request = new DeviceCameraScheduleRequest();
        request.setDeviceUid(deviceUid);
        request.setTimeOfDay(time);
        request.setRecurrence(recurrence);
        request.setTriggerType(TriggerType.SCHEDULED);
        return request;
    }

    private DeviceCameraSchedule schedule(UUID scheduleId, String deviceUid) {
        DeviceCameraSchedule schedule = new DeviceCameraSchedule();
        schedule.setId(scheduleId);
        schedule.setDeviceUid(deviceUid);
        schedule.setEnabled(true);
        schedule.setTriggerType(TriggerType.SCHEDULED);
        schedule.setTimeOfDay(LocalTime.of(8, 30));
        schedule.setRecurrence(Recurrence.DAILY);
        schedule.setNextRunAt(Instant.parse("2026-05-15T08:30:00Z"));
        return schedule;
    }
}
