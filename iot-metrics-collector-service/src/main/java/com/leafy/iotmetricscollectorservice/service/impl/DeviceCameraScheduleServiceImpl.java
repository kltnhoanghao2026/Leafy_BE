package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.DeviceCameraScheduleRequest;
import com.leafy.iotmetricscollectorservice.dto.DeviceCameraScheduleResponse;
import com.leafy.iotmetricscollectorservice.dto.media.DeviceMediaEventResponse;
import com.leafy.iotmetricscollectorservice.entity.DeviceCameraSchedule;
import com.leafy.iotmetricscollectorservice.entity.Recurrence;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import com.leafy.iotmetricscollectorservice.repository.DeviceCameraScheduleRepository;
import com.leafy.iotmetricscollectorservice.repository.DeviceMediaEventRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.service.DeviceCameraCaptureService;
import com.leafy.iotmetricscollectorservice.service.DeviceCameraScheduleService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default implementation for camera schedule management and execution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCameraScheduleServiceImpl implements DeviceCameraScheduleService {

    private final DeviceCameraScheduleRepository scheduleRepository;
    private final IoTDeviceRepository deviceRepository;
    private final DeviceMediaEventRepository mediaEventRepository;
    private final DeviceCameraCaptureService cameraCaptureService;
    private final Clock cameraScheduleClock;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.camera-schedule.collector-instance-id:${HOSTNAME:local-collector}}")
    private String collectorInstanceId;

    /**
     * Returns every schedule. This intentionally stays simple for the requested
     * Phase B API.
     */
    @Override
    @Transactional(readOnly = true)
    public List<DeviceCameraScheduleResponse> listSchedules() {
        return scheduleRepository.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Loads one schedule by id.
     */
    @Override
    @Transactional(readOnly = true)
    public DeviceCameraScheduleResponse getSchedule(UUID scheduleId) {
        return toResponse(findSchedule(scheduleId));
    }

    /**
     * Creates a schedule, defaulting enabled=true and triggerType=SCHEDULED when
     * the request omits them.
     */
    @Override
    @Transactional
    public DeviceCameraScheduleResponse createSchedule(DeviceCameraScheduleRequest request) {
        validateRequest(request);
        validateDeviceExists(request.getDeviceUid());

        DeviceCameraSchedule schedule = new DeviceCameraSchedule();
        applyRequest(schedule, request, true);
        schedule.setNextRunAt(computeNextRunAt(schedule.getTimeOfDay(), schedule.getRecurrence(), Instant.now(cameraScheduleClock)));
        return toResponse(scheduleRepository.save(schedule));
    }

    /**
     * Updates a schedule and recomputes its next execution from now.
     */
    @Override
    @Transactional
    public DeviceCameraScheduleResponse updateSchedule(UUID scheduleId, DeviceCameraScheduleRequest request) {
        validateRequest(request);
        validateDeviceExists(request.getDeviceUid());

        DeviceCameraSchedule schedule = findSchedule(scheduleId);
        applyRequest(schedule, request, false);
        schedule.setNextRunAt(schedule.isEnabled()
            ? computeNextRunAt(schedule.getTimeOfDay(), schedule.getRecurrence(), Instant.now(cameraScheduleClock))
            : null);
        return toResponse(scheduleRepository.save(schedule));
    }

    /**
     * Deletes a schedule if it exists.
     */
    @Override
    @Transactional
    public void deleteSchedule(UUID scheduleId) {
        DeviceCameraSchedule schedule = findSchedule(scheduleId);
        scheduleRepository.delete(schedule);
    }

    /**
     * Immediately triggers a specific schedule and advances its run state.
     */
    @Override
    @Transactional
    public DeviceCameraScheduleResponse runNow(UUID scheduleId) {
        ScheduleExecutionResult result = executeScheduleWithLock(scheduleId, true);
        if (result.failure() != null) {
            throw result.failure();
        }
        if (result.schedule() == null) {
            throw TelemetryQueryException.invalidCameraSchedule("schedule is currently locked or no longer available");
        }
        return toResponse(result.schedule());
    }

    /**
     * Finds due enabled schedules and attempts each independently. A failure for
     * one device is logged but does not block the remaining schedules.
     */
    @Override
    public void triggerSchedules() {
        Instant now = Instant.now(cameraScheduleClock);
        List<UUID> dueScheduleIds = scheduleRepository.findDueScheduleIds(now.plusMillis(1));
        for (UUID scheduleId : dueScheduleIds) {
            if (!tryAcquireSchedule(scheduleId)) {
                log.debug(
                    "Schedule skipped by collectorInstanceId={} because it was already locked or no longer due. scheduleId={}",
                    collectorInstanceId(),
                    scheduleId
                );
            }
        }
    }

    /**
     * Acquires and executes one due schedule in a short, independent
     * transaction. The database row lock is the distributed mutex: in a
     * multi-instance deployment, only the transaction holding this row can
     * create the DeviceMediaEvent, publish the MQTT command, and advance
     * lastRunAt/nextRunAt for the current due slot.
     */
    @Override
    public boolean tryAcquireSchedule(UUID scheduleId) {
        ScheduleExecutionResult result = executeScheduleWithLock(scheduleId, false);
        if (result.failure() != null) {
            log.warn(
                "Scheduled camera capture failed. scheduleId={}, collectorInstanceId={}",
                scheduleId,
                collectorInstanceId(),
                result.failure()
            );
        }
        return result.acquired();
    }

    private ScheduleExecutionResult executeScheduleWithLock(UUID scheduleId, boolean forceRun) {
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return template.execute(status -> executeLockedSchedule(scheduleId, forceRun));
        } catch (PessimisticLockingFailureException exception) {
            log.info(
                "Schedule acquisition blocked. scheduleId={}, collectorInstanceId={}, reason={}",
                scheduleId,
                collectorInstanceId(),
                exception.getClass().getSimpleName()
            );
            return ScheduleExecutionResult.notAcquired();
        } catch (TransactionException exception) {
            log.warn(
                "Schedule acquisition transaction failed. scheduleId={}, collectorInstanceId={}",
                scheduleId,
                collectorInstanceId(),
                exception
            );
            return ScheduleExecutionResult.notAcquired();
        }
    }

    private ScheduleExecutionResult executeLockedSchedule(UUID scheduleId, boolean forceRun) {
        Optional<DeviceCameraSchedule> lockedSchedule = scheduleRepository.findLockedById(scheduleId);
        if (lockedSchedule.isEmpty()) {
            return ScheduleExecutionResult.notAcquired();
        }

        DeviceCameraSchedule schedule = lockedSchedule.get();
        Instant now = Instant.now(cameraScheduleClock);
        if (!forceRun && !isDue(schedule, now)) {
            return ScheduleExecutionResult.notAcquired();
        }

        log.info(
            "Schedule acquired by collectorInstanceId={}. scheduleId={}, deviceUid={}, nextRunAt={}",
            collectorInstanceId(),
            schedule.getId(),
            schedule.getDeviceUid(),
            schedule.getNextRunAt()
        );

        RuntimeException failure = null;
        try {
            triggerSchedule(schedule);
        } catch (RuntimeException exception) {
            failure = exception;
        }

        advanceSchedule(schedule, now);
        DeviceCameraSchedule saved = scheduleRepository.save(schedule);
        return ScheduleExecutionResult.acquired(saved, failure);
    }

    /**
     * Attempts the capture and advances lastRunAt/nextRunAt after the attempt.
     */
    private void triggerSchedule(DeviceCameraSchedule schedule) {
        log.info("Triggering scheduled camera capture. scheduleId={}, deviceUid={}", schedule.getId(), schedule.getDeviceUid());
        cameraCaptureService.requestCapture(schedule.getDeviceUid(), TriggerType.SCHEDULED);
        log.info("Scheduled camera capture requested. scheduleId={}, deviceUid={}", schedule.getId(), schedule.getDeviceUid());
    }

    private boolean isDue(DeviceCameraSchedule schedule, Instant now) {
        return schedule.isEnabled()
            && schedule.getNextRunAt() != null
            && !schedule.getNextRunAt().isAfter(now);
    }

    /**
     * Advances the run pointer after any due attempt. This prevents repeated
     * scheduler scans from creating duplicate DeviceMediaEvent rows for the same
     * due slot, including when MQTT publish/capture request fails.
     */
    private void advanceSchedule(DeviceCameraSchedule schedule, Instant now) {
        schedule.setLastRunAt(now);
        if (schedule.getRecurrence() == Recurrence.NONE) {
            schedule.setEnabled(false);
            schedule.setNextRunAt(null);
        } else {
            schedule.setNextRunAt(computeNextRunAt(schedule.getTimeOfDay(), schedule.getRecurrence(), now));
        }
    }

    /**
     * Applies request fields after validation.
     */
    private void applyRequest(DeviceCameraSchedule schedule, DeviceCameraScheduleRequest request, boolean creating) {
        schedule.setDeviceUid(request.getDeviceUid());
        schedule.setEnabled(request.getEnabled() == null ? creating || schedule.isEnabled() : request.getEnabled());
        schedule.setTriggerType(request.getTriggerType() == null ? TriggerType.SCHEDULED : request.getTriggerType());
        schedule.setTimeOfDay(request.getTimeOfDay());
        schedule.setRecurrence(request.getRecurrence());
    }

    /**
     * Validates required fields and allowed trigger values.
     */
    private void validateRequest(DeviceCameraScheduleRequest request) {
        if (request == null) {
            throw TelemetryQueryException.invalidCameraSchedule("request body is required");
        }
        if (request.getDeviceUid() == null || request.getDeviceUid().isBlank()) {
            throw TelemetryQueryException.invalidCameraSchedule("deviceUid is required");
        }
        if (request.getTimeOfDay() == null) {
            throw TelemetryQueryException.invalidCameraSchedule("timeOfDay is required");
        }
        if (request.getRecurrence() == null) {
            throw TelemetryQueryException.invalidCameraSchedule("recurrence is required");
        }
        if (request.getTriggerType() != null
            && request.getTriggerType() != TriggerType.MANUAL
            && request.getTriggerType() != TriggerType.SCHEDULED) {
            throw TelemetryQueryException.invalidCameraSchedule("triggerType must be MANUAL or SCHEDULED");
        }
    }

    /**
     * Validates that the target IoT device exists.
     */
    private void validateDeviceExists(String deviceUid) {
        if (!deviceRepository.existsByDeviceUid(deviceUid)) {
            throw TelemetryQueryException.deviceNotFoundByUid(deviceUid);
        }
    }

    /**
     * Computes the next UTC run instant from the configured local time and
     * recurrence. The local zone is the JVM default zone.
     */
    Instant computeNextRunAt(LocalTime timeOfDay, Recurrence recurrence, Instant referenceInstant) {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate referenceDate = LocalDateTime.ofInstant(referenceInstant, zoneId).toLocalDate();
        LocalDateTime candidate = LocalDateTime.of(referenceDate, timeOfDay);

        if (!candidate.atZone(zoneId).toInstant().isAfter(referenceInstant)) {
            candidate = switch (recurrence) {
                case WEEKLY -> candidate.plusWeeks(1);
                case DAILY, NONE -> candidate.plusDays(1);
            };
        }

        return candidate.atZone(zoneId).toInstant();
    }

    /**
     * Finds a schedule or throws the module's standard API exception.
     */
    private DeviceCameraSchedule findSchedule(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> TelemetryQueryException.cameraScheduleNotFound(scheduleId));
    }

    /**
     * Maps entity to API response.
     */
    private DeviceCameraScheduleResponse toResponse(DeviceCameraSchedule schedule) {
        DeviceCameraScheduleResponse response = new DeviceCameraScheduleResponse();
        response.setId(schedule.getId());
        response.setDeviceUid(schedule.getDeviceUid());
        Optional.ofNullable(deviceRepository.findByDeviceUid(schedule.getDeviceUid()))
            .flatMap(device -> device)
            .ifPresent(device -> {
            response.setDeviceId(device.getId());
            mediaEventRepository.findTopByDeviceIdOrderByCapturedAtDesc(device.getId())
                .map(this::toMediaResponse)
                .ifPresent(response::setLastMediaEvent);
        });
        response.setEnabled(schedule.isEnabled());
        response.setTriggerType(schedule.getTriggerType());
        response.setTimeOfDay(schedule.getTimeOfDay());
        response.setRecurrence(schedule.getRecurrence());
        response.setLastRunAt(schedule.getLastRunAt());
        response.setNextRunAt(schedule.getNextRunAt());
        return response;
    }

    private DeviceMediaEventResponse toMediaResponse(DeviceMediaEvent event) {
        DeviceMediaEventResponse response = new DeviceMediaEventResponse();
        response.setId(event.getId());
        response.setRequestId(event.getRequestId());
        IoTDevice device = event.getDevice();
        response.setDeviceId(device != null ? device.getId() : null);
        response.setZoneId(event.getZone() != null ? event.getZone().getId() : null);
        response.setFileId(event.getFile() != null ? event.getFile().getId() : null);
        response.setMediaType(event.getMediaType());
        response.setTriggerType(event.getTriggerType());
        response.setStatus(event.getStatus());
        response.setContentType(event.getContentType());
        response.setSizeBytes(event.getSizeBytes());
        response.setWidth(event.getWidth());
        response.setHeight(event.getHeight());
        response.setError(event.getError());
        response.setRequestedAt(event.getRequestedAt());
        response.setCommandSentAt(event.getCommandSentAt());
        response.setUploadedAt(event.getUploadedAt());
        response.setCapturedAt(event.getCapturedAt());
        return response;
    }

    private String collectorInstanceId() {
        return collectorInstanceId != null && !collectorInstanceId.isBlank()
            ? collectorInstanceId
            : "local-collector";
    }

    private record ScheduleExecutionResult(boolean acquired, DeviceCameraSchedule schedule, RuntimeException failure) {

        static ScheduleExecutionResult acquired(DeviceCameraSchedule schedule, RuntimeException failure) {
            return new ScheduleExecutionResult(true, schedule, failure);
        }

        static ScheduleExecutionResult notAcquired() {
            return new ScheduleExecutionResult(false, null, null);
        }
    }
}
