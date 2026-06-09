package com.leafy.iotmetricscollectorservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leafy.iotmetricscollectorservice.dto.DeviceCameraScheduleRequest;
import com.leafy.iotmetricscollectorservice.dto.DeviceCameraScheduleResponse;
import com.leafy.iotmetricscollectorservice.entity.Recurrence;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryExceptionHandler;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import com.leafy.iotmetricscollectorservice.service.DeviceAccessService;
import com.leafy.iotmetricscollectorservice.service.DeviceCameraScheduleService;
import com.leafy.iotmetricscollectorservice.service.DeviceMediaAnalysisService;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DeviceCameraWorkflowControllerTest {

    @Mock
    private DeviceCameraScheduleService scheduleService;

    @Mock
    private DeviceMediaAnalysisService analysisService;

    @Mock
    private DeviceAccessService deviceAccessService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DeviceCameraWorkflowController(deviceAccessService, scheduleService, analysisService))
            .setControllerAdvice(new TelemetryQueryExceptionHandler())
            .build();
    }

    @Test
    void createCaptureSchedule_passesCaptureOptionsToClientScopedService() throws Exception {
        String deviceUid = "device-001";
        DeviceCameraScheduleResponse response = response(UUID.randomUUID(), deviceUid);
        response.setResolution("HD");
        response.setQuality("HIGH");
        response.setUploadEndpoint("https://files.example.com/upload");
        when(scheduleService.createScheduleForDevice(eq(deviceUid), any(DeviceCameraScheduleRequest.class))).thenReturn(response);

        mockMvc.perform(
                post("/iot/devices/{deviceUid}/camera/capture-schedule", deviceUid)
                    .header(DeviceController.USER_ID_HEADER, "user-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "enabled": true,
                          "timeOfDay": "06:15:00",
                          "recurrence": "DAILY",
                          "resolution": "HD",
                          "quality": "HIGH",
                          "uploadEndpoint": "https://files.example.com/upload"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resolution").value("HD"))
            .andExpect(jsonPath("$.quality").value("HIGH"))
            .andExpect(jsonPath("$.uploadEndpoint").value("https://files.example.com/upload"));

        ArgumentCaptor<DeviceCameraScheduleRequest> captor = ArgumentCaptor.forClass(DeviceCameraScheduleRequest.class);
        verify(scheduleService).createScheduleForDevice(eq(deviceUid), captor.capture());
        assertThat(captor.getValue().getTriggerType()).isEqualTo(TriggerType.SCHEDULED);
        assertThat(captor.getValue().getResolution()).isEqualTo("HD");
        assertThat(captor.getValue().getQuality()).isEqualTo("HIGH");
        assertThat(captor.getValue().getUploadEndpoint()).isEqualTo("https://files.example.com/upload");
    }

    @Test
    void updateDeleteAndRunNowUseDeviceScopedServiceMethods() throws Exception {
        String deviceUid = "device-001";
        UUID scheduleId = UUID.randomUUID();
        when(scheduleService.updateScheduleForDevice(eq(deviceUid), eq(scheduleId), any(DeviceCameraScheduleRequest.class)))
            .thenReturn(response(scheduleId, deviceUid));
        when(scheduleService.runScheduleNow(deviceUid, scheduleId)).thenReturn(response(scheduleId, deviceUid));

        mockMvc.perform(
                put("/iot/devices/{deviceUid}/camera/capture-schedule/{scheduleId}", deviceUid, scheduleId)
                    .header(DeviceController.USER_ID_HEADER, "user-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "timeOfDay": "07:30:00",
                          "recurrence": "WEEKLY",
                          "resolution": "VGA",
                          "quality": "MEDIUM"
                        }
                        """)
            )
            .andExpect(status().isOk());

        mockMvc.perform(post("/iot/devices/{deviceUid}/camera/run-scheduled/{scheduleId}", deviceUid, scheduleId).header(DeviceController.USER_ID_HEADER, "user-1"))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/iot/devices/{deviceUid}/camera/capture-schedule/{scheduleId}", deviceUid, scheduleId).header(DeviceController.USER_ID_HEADER, "user-1"))
            .andExpect(status().isNoContent());

        verify(scheduleService).updateScheduleForDevice(eq(deviceUid), eq(scheduleId), any(DeviceCameraScheduleRequest.class));
        verify(scheduleService).runScheduleNow(deviceUid, scheduleId);
        verify(scheduleService).deleteScheduleForDevice(deviceUid, scheduleId);
    }

    private DeviceCameraScheduleResponse response(UUID scheduleId, String deviceUid) {
        DeviceCameraScheduleResponse response = new DeviceCameraScheduleResponse();
        response.setId(scheduleId);
        response.setDeviceUid(deviceUid);
        response.setEnabled(true);
        response.setTriggerType(TriggerType.SCHEDULED);
        response.setTimeOfDay(LocalTime.of(6, 15));
        response.setRecurrence(Recurrence.DAILY);
        response.setNextRunAt(Instant.parse("2026-05-16T06:15:00Z"));
        return response;
    }
}
