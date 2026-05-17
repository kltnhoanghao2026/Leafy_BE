package com.leafy.iotmetricscollectorservice.controller;

import com.leafy.iotmetricscollectorservice.dto.DeviceCameraScheduleRequest;
import com.leafy.iotmetricscollectorservice.dto.DeviceCameraScheduleResponse;
import com.leafy.iotmetricscollectorservice.service.DeviceCameraScheduleService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for automatic camera capture schedules.
 */
@RestController
@RequestMapping("/iot/camera-schedules")
@RequiredArgsConstructor
public class DeviceCameraScheduleController {

    private final DeviceCameraScheduleService scheduleService;

    /**
     * Lists all camera schedules.
     */
    @GetMapping
    public ResponseEntity<List<DeviceCameraScheduleResponse>> listSchedules() {
        return ResponseEntity.ok(scheduleService.listSchedules());
    }

    /**
     * Creates a camera schedule.
     */
    @PostMapping
    public ResponseEntity<DeviceCameraScheduleResponse> createSchedule(
        @RequestBody DeviceCameraScheduleRequest request
    ) {
        return ResponseEntity.ok(scheduleService.createSchedule(request));
    }

    /**
     * Updates an existing camera schedule.
     */
    @PutMapping("/{scheduleId}")
    public ResponseEntity<DeviceCameraScheduleResponse> updateSchedule(
        @PathVariable UUID scheduleId,
        @RequestBody DeviceCameraScheduleRequest request
    ) {
        return ResponseEntity.ok(scheduleService.updateSchedule(scheduleId, request));
    }

    /**
     * Deletes an existing camera schedule.
     */
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable UUID scheduleId) {
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Immediately runs an existing schedule.
     */
    @PostMapping("/{scheduleId}/run-now")
    public ResponseEntity<DeviceCameraScheduleResponse> runNow(@PathVariable UUID scheduleId) {
        return ResponseEntity.ok(scheduleService.runNow(scheduleId));
    }
}
