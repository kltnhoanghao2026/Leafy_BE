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
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/iot/devices/{deviceUid}/camera")
@RequiredArgsConstructor
public class DeviceCameraWorkflowController {

    private final DeviceCameraScheduleService scheduleService;
    private final DeviceMediaAnalysisService analysisService;

    @PostMapping("/capture-schedule")
    public ResponseEntity<DeviceCameraScheduleResponse> createCaptureSchedule(
        @PathVariable String deviceUid,
        @RequestBody ClientCameraScheduleRequest request
    ) {
        ClientCameraScheduleRequest normalized = request != null ? request : new ClientCameraScheduleRequest();
        DeviceCameraScheduleRequest scheduleRequest = new DeviceCameraScheduleRequest();
        scheduleRequest.setDeviceUid(deviceUid);
        scheduleRequest.setEnabled(normalized.getEnabled() == null || normalized.getEnabled());
        scheduleRequest.setTriggerType(TriggerType.SCHEDULED);
        scheduleRequest.setTimeOfDay(normalized.getTimeOfDay() != null ? normalized.getTimeOfDay() : LocalTime.now());
        scheduleRequest.setRecurrence(normalized.getRecurrence() != null ? normalized.getRecurrence() : Recurrence.DAILY);
        return ResponseEntity.ok(scheduleService.createSchedule(scheduleRequest));
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
}
