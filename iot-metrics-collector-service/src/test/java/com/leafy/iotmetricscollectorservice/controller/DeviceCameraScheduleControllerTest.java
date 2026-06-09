package com.leafy.iotmetricscollectorservice.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leafy.iotmetricscollectorservice.dto.DeviceCameraScheduleResponse;
import com.leafy.iotmetricscollectorservice.entity.Recurrence;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryExceptionHandler;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import com.leafy.iotmetricscollectorservice.service.DeviceCameraScheduleService;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DeviceCameraScheduleControllerTest {

    @Mock
    private DeviceCameraScheduleService scheduleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DeviceCameraScheduleController(scheduleService))
            .setControllerAdvice(new TelemetryQueryExceptionHandler())
            .build();
    }

    @Test
    void listSchedules_returnsSchedules() throws Exception {
        String deviceUid = "device-001";
        DeviceCameraScheduleResponse response = response(UUID.randomUUID(), deviceUid);
        when(scheduleService.listSchedules()).thenReturn(List.of(response));

        mockMvc.perform(get("/iot/camera-schedules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].deviceUid").value(deviceUid))
            .andExpect(jsonPath("$[0].triggerType").value("SCHEDULED"))
            .andExpect(jsonPath("$[0].recurrence").value("DAILY"));
    }

    @Test
    void createSchedule_returnsCreatedSchedule() throws Exception {
        String deviceUid = "device-001";
        DeviceCameraScheduleResponse response = response(UUID.randomUUID(), deviceUid);
        when(scheduleService.createSchedule(org.mockito.ArgumentMatchers.any())).thenReturn(response);

        mockMvc.perform(
                post("/iot/camera-schedules")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "deviceUid": "%s",
                          "enabled": true,
                          "triggerType": "SCHEDULED",
                          "timeOfDay": "08:30:00",
                          "recurrence": "DAILY"
                        }
                        """.formatted(deviceUid))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceUid").value(deviceUid))
            .andExpect(jsonPath("$.timeOfDay").value("08:30:00"));
    }

    @Test
    void updateSchedule_returnsUpdatedSchedule() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        String deviceUid = "device-001";
        DeviceCameraScheduleResponse response = response(scheduleId, deviceUid);
        response.setRecurrence(Recurrence.WEEKLY);
        when(scheduleService.updateSchedule(org.mockito.ArgumentMatchers.eq(scheduleId), org.mockito.ArgumentMatchers.any()))
            .thenReturn(response);

        mockMvc.perform(
                put("/iot/camera-schedules/{scheduleId}", scheduleId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "deviceUid": "%s",
                          "enabled": true,
                          "triggerType": "SCHEDULED",
                          "timeOfDay": "08:30:00",
                          "recurrence": "WEEKLY"
                        }
                        """.formatted(deviceUid))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recurrence").value("WEEKLY"));
    }

    @Test
    void deleteSchedule_returnsNoContent() throws Exception {
        UUID scheduleId = UUID.randomUUID();

        mockMvc.perform(delete("/iot/camera-schedules/{scheduleId}", scheduleId))
            .andExpect(status().isNoContent());

        verify(scheduleService).deleteSchedule(scheduleId);
    }

    @Test
    void runNow_returnsSchedule() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        DeviceCameraScheduleResponse response = response(scheduleId, "device-001");
        response.setLastRunAt(Instant.parse("2026-05-15T08:31:00Z"));
        when(scheduleService.runNow(scheduleId)).thenReturn(response);

        mockMvc.perform(post("/iot/camera-schedules/{scheduleId}/run-now", scheduleId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lastRunAt").value("2026-05-15T08:31:00Z"));
    }

    @Test
    void createSchedule_returnsBusinessErrorWhenInvalid() throws Exception {
        when(scheduleService.createSchedule(org.mockito.ArgumentMatchers.any()))
            .thenThrow(TelemetryQueryException.invalidCameraSchedule("timeOfDay is required"));

        mockMvc.perform(
                post("/iot/camera-schedules")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "deviceUid": "device-001",
                          "recurrence": "DAILY"
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(4634));
    }

    private DeviceCameraScheduleResponse response(UUID scheduleId, String deviceUid) {
        DeviceCameraScheduleResponse response = new DeviceCameraScheduleResponse();
        response.setId(scheduleId);
        response.setDeviceUid(deviceUid);
        response.setEnabled(true);
        response.setTriggerType(TriggerType.SCHEDULED);
        response.setTimeOfDay(LocalTime.of(8, 30));
        response.setRecurrence(Recurrence.DAILY);
        response.setNextRunAt(Instant.parse("2026-05-16T08:30:00Z"));
        return response;
    }
}
