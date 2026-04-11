package com.leafy.iotmetricscollectorservice.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventItemResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryExceptionHandler;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import com.leafy.iotmetricscollectorservice.service.AlertLifecycleService;
import com.leafy.iotmetricscollectorservice.service.AlertQueryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

    @Mock
    private AlertQueryService alertQueryService;

    @Mock
    private AlertLifecycleService alertLifecycleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AlertController(alertQueryService, alertLifecycleService))
            .setControllerAdvice(new TelemetryQueryExceptionHandler())
            .build();
    }

    @Test
    void searchAlerts_returnsFilteredPayload() throws Exception {
        UUID zoneId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        AlertEventItemResponse item = new AlertEventItemResponse();
        item.setId(UUID.randomUUID());
        item.setStatus("OPEN");
        item.setSeverity("HIGH");

        when(alertQueryService.searchAlerts(
            eq(zoneId),
            eq(deviceId),
            eq(AlertStatus.OPEN),
            eq(AlertSeverity.HIGH),
            eq(Instant.parse("2026-04-10T00:00:00Z")),
            eq(Instant.parse("2026-04-11T00:00:00Z"))
        )).thenReturn(List.of(item));

        mockMvc.perform(
            get("/iot/alert-events")
                .param("zoneId", zoneId.toString())
                .param("deviceId", deviceId.toString())
                .param("status", "OPEN")
                .param("severity", "HIGH")
                .param("from", "2026-04-10T00:00:00Z")
                .param("to", "2026-04-11T00:00:00Z")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("OPEN"))
            .andExpect(jsonPath("$[0].severity").value("HIGH"));
    }

    @Test
    void getAlertEvent_returnsDetailPayload() throws Exception {
        UUID alertEventId = UUID.randomUUID();
        AlertEventDetailResponse response = new AlertEventDetailResponse();
        response.setId(alertEventId);
        response.setMessage("Threshold exceeded");

        when(alertQueryService.getAlertEvent(alertEventId)).thenReturn(response);

        mockMvc.perform(get("/iot/alert-events/{alertEventId}", alertEventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(alertEventId.toString()))
            .andExpect(jsonPath("$.message").value("Threshold exceeded"));
    }

    @Test
    void getAlertEvent_returnsNotFoundWhenMissing() throws Exception {
        UUID alertEventId = UUID.randomUUID();
        when(alertQueryService.getAlertEvent(alertEventId))
            .thenThrow(TelemetryQueryException.alertEventNotFound(alertEventId));

        mockMvc.perform(get("/iot/alert-events/{alertEventId}", alertEventId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(4604));
    }

    @Test
    void acknowledgeAlert_returnsUpdatedPayload() throws Exception {
        UUID alertEventId = UUID.randomUUID();
        AlertEventDetailResponse response = new AlertEventDetailResponse();
        response.setId(alertEventId);
        response.setStatus("ACKNOWLEDGED");

        when(alertLifecycleService.acknowledgeAlert(alertEventId)).thenReturn(response);

        mockMvc.perform(post("/iot/alert-events/{alertEventId}/acknowledge", alertEventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(alertEventId.toString()))
            .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    void resolveAlert_returnsUpdatedPayload() throws Exception {
        UUID alertEventId = UUID.randomUUID();
        AlertEventDetailResponse response = new AlertEventDetailResponse();
        response.setId(alertEventId);
        response.setStatus("RESOLVED");

        when(alertLifecycleService.resolveAlert(alertEventId)).thenReturn(response);

        mockMvc.perform(post("/iot/alert-events/{alertEventId}/resolve", alertEventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(alertEventId.toString()))
            .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void acknowledgeAlert_returnsBusinessErrorWhenTransitionInvalid() throws Exception {
        UUID alertEventId = UUID.randomUUID();
        when(alertLifecycleService.acknowledgeAlert(alertEventId))
            .thenThrow(TelemetryQueryException.cannotAcknowledgeAlert(alertEventId, "RESOLVED"));

        mockMvc.perform(post("/iot/alert-events/{alertEventId}/acknowledge", alertEventId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(4606));
    }

    @Test
    void resolveAlert_returnsNotFoundWhenMissing() throws Exception {
        UUID alertEventId = UUID.randomUUID();
        when(alertLifecycleService.resolveAlert(alertEventId))
            .thenThrow(TelemetryQueryException.alertEventNotFound(alertEventId));

        mockMvc.perform(post("/iot/alert-events/{alertEventId}/resolve", alertEventId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(4604));
    }
}
