package com.leafy.iotmetricscollectorservice.controller;

import com.leafy.iotmetricscollectorservice.dto.DeviceCameraScheduleRequest;
import com.leafy.iotmetricscollectorservice.dto.DeviceCameraScheduleResponse;
import com.leafy.iotmetricscollectorservice.dto.disease.DiseaseDetectRequest;
import com.leafy.iotmetricscollectorservice.dto.media.ClientCameraScheduleRequest;
import com.leafy.iotmetricscollectorservice.dto.media.DeviceMediaAnalysisResponse;
import com.leafy.iotmetricscollectorservice.entity.Recurrence;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import com.leafy.iotmetricscollectorservice.service.DeviceCameraScheduleService;
import com.leafy.iotmetricscollectorservice.service.DeviceMediaAnalysisService;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/iot/devices/{deviceUid}/camera")
@RequiredArgsConstructor
public class DeviceCameraWorkflowController {

    private final DeviceCameraScheduleService scheduleService;
    private final DeviceMediaAnalysisService analysisService;

    @GetMapping("/capture-schedule")
    public ResponseEntity<List<DeviceCameraScheduleResponse>> listCaptureSchedules(
        @PathVariable String deviceUid
    ) {
        return ResponseEntity.ok(scheduleService.listSchedulesForDevice(deviceUid));
    }

    @PostMapping("/capture-schedule")
    public ResponseEntity<DeviceCameraScheduleResponse> createCaptureSchedule(
        @PathVariable String deviceUid,
        @RequestBody ClientCameraScheduleRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(scheduleService.createScheduleForDevice(deviceUid, toScheduleRequest(deviceUid, request)));
    }

    @PutMapping("/capture-schedule/{scheduleId}")
    public ResponseEntity<DeviceCameraScheduleResponse> updateCaptureSchedule(
        @PathVariable String deviceUid,
        @PathVariable UUID scheduleId,
        @RequestBody ClientCameraScheduleRequest request
    ) {
        return ResponseEntity.ok(scheduleService.updateScheduleForDevice(deviceUid, scheduleId, toScheduleRequest(deviceUid, request)));
    }

    @DeleteMapping("/capture-schedule/{scheduleId}")
    public ResponseEntity<Void> deleteCaptureSchedule(
        @PathVariable String deviceUid,
        @PathVariable UUID scheduleId
    ) {
        scheduleService.deleteScheduleForDevice(deviceUid, scheduleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/run-scheduled/{scheduleId}")
    public ResponseEntity<DeviceCameraScheduleResponse> runScheduledCaptureNow(
        @PathVariable String deviceUid,
        @PathVariable UUID scheduleId
    ) {
        return ResponseEntity.ok(scheduleService.runScheduleNow(deviceUid, scheduleId));
    }

    @PostMapping("/detect")
    public ResponseEntity<DeviceMediaAnalysisResponse> detectDisease(
        @PathVariable String deviceUid,
        @RequestBody DiseaseDetectRequest request
    ) {
        DiseaseDetectRequest normalized = request != null ? request : new DiseaseDetectRequest();
        normalized.setDeviceUid(deviceUid);
        return ResponseEntity.ok(analysisService.detect(normalized));
    }

    private DeviceCameraScheduleRequest toScheduleRequest(String deviceUid, ClientCameraScheduleRequest request) {
        ClientCameraScheduleRequest normalized = request != null ? request : new ClientCameraScheduleRequest();
        DeviceCameraScheduleRequest scheduleRequest = new DeviceCameraScheduleRequest();
        scheduleRequest.setDeviceUid(deviceUid);
        scheduleRequest.setEnabled(normalized.getEnabled() == null || normalized.getEnabled());
        scheduleRequest.setTriggerType(TriggerType.SCHEDULED);
        scheduleRequest.setTimeOfDay(normalized.getTimeOfDay() != null ? normalized.getTimeOfDay() : LocalTime.now());
        scheduleRequest.setRecurrence(normalized.getRecurrence() != null ? normalized.getRecurrence() : Recurrence.DAILY);
        scheduleRequest.setResolution(normalized.getResolution());
        scheduleRequest.setQuality(normalized.getQuality());
        scheduleRequest.setUploadEndpoint(normalized.getUploadEndpoint());
        return scheduleRequest;
    }
}
