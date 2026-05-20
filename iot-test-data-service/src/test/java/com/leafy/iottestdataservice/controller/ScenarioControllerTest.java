package com.leafy.iottestdataservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.iottestdataservice.dto.CameraCaptureManualRequest;
import com.leafy.iottestdataservice.dto.CameraCaptureQuality;
import com.leafy.iottestdataservice.dto.CameraCaptureRecurrence;
import com.leafy.iottestdataservice.dto.CameraCaptureResolution;
import com.leafy.iottestdataservice.dto.CameraCaptureSimulationResponse;
import com.leafy.iottestdataservice.dto.CameraImageMetadataResponse;
import com.leafy.iottestdataservice.dto.CameraTriggerType;
import com.leafy.iottestdataservice.service.CameraCaptureSimulationService;
import com.leafy.iottestdataservice.service.ScenarioService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScenarioController.class)
class ScenarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScenarioService scenarioService;

    @MockBean
    private CameraCaptureSimulationService cameraCaptureSimulationService;

    @Test
    void manualCameraCaptureEndpointReturnsSimulatedMetadata() throws Exception {
        when(cameraCaptureSimulationService.simulateManualCapture(any())).thenReturn(
            new CameraCaptureSimulationResponse(
                "camera-capture-manual",
                null,
                "device-001",
                CameraTriggerType.MANUAL,
                CameraCaptureResolution.VGA,
                CameraCaptureQuality.MEDIUM,
                null,
                Instant.parse("2026-05-15T08:00:00Z"),
                null,
                List.of(metadata("device-001", CameraTriggerType.MANUAL))
            )
        );

        mockMvc.perform(
                post("/seed/scenarios/camera-capture-manual")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CameraCaptureManualRequest("device-001", CameraCaptureResolution.VGA, CameraCaptureQuality.MEDIUM, 1)
                    ))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scenario").value("camera-capture-manual"))
            .andExpect(jsonPath("$.captures[0].triggerType").value("MANUAL"))
            .andExpect(jsonPath("$.captures[0].status").value("SUCCESS"));
    }

    @Test
    void scheduledCameraCaptureEndpointPassesRunNowQueryParam() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        when(cameraCaptureSimulationService.scheduleCapture(any(), eq(true))).thenReturn(
            new CameraCaptureSimulationResponse(
                "camera-capture-scheduled",
                scheduleId,
                "device-002",
                CameraTriggerType.SCHEDULED,
                CameraCaptureResolution.QVGA,
                CameraCaptureQuality.LOW,
                CameraCaptureRecurrence.DAILY,
                Instant.parse("2026-05-15T08:00:00Z"),
                Instant.parse("2026-05-16T08:00:00Z"),
                List.of(metadata("device-002", CameraTriggerType.SCHEDULED))
            )
        );

        mockMvc.perform(
                post("/seed/scenarios/camera-capture-scheduled?run-now=true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "deviceUid": "device-002",
                          "timeOfDay": "08:00:00",
                          "recurrence": "DAILY",
                          "resolution": "QVGA",
                          "quality": "LOW"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scheduleId").value(scheduleId.toString()))
            .andExpect(jsonPath("$.triggerType").value("SCHEDULED"))
            .andExpect(jsonPath("$.captures[0].triggerType").value("SCHEDULED"));
    }

    private CameraImageMetadataResponse metadata(String deviceUid, CameraTriggerType triggerType) {
        return new CameraImageMetadataResponse(
            UUID.randomUUID().toString(),
            deviceUid,
            triggerType,
            Instant.parse("2026-05-15T08:00:00Z"),
            1024L,
            "SUCCESS",
            true,
            "mock-file-1",
            "image/jpeg",
            640,
            480,
            null,
            "coffee/prod/devices/" + deviceUid + "/camera/capture",
            "coffee/prod/devices/" + deviceUid + "/image/meta"
        );
    }
}
