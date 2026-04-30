package com.leafy.iotmetricscollectorservice.controller;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leafy.iotmetricscollectorservice.dto.alert_rule.AlertRuleResponse;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryExceptionHandler;
import com.leafy.iotmetricscollectorservice.service.AlertRuleService;
import java.time.Instant;
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
class AlertRuleControllerTest {

    @Mock
    private AlertRuleService alertRuleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AlertRuleController(alertRuleService))
            .setControllerAdvice(new TelemetryQueryExceptionHandler())
            .build();
    }

    @Test
    void postAlertRule_returnsCreatedPayload() throws Exception {
        String currentUserId = UUID.randomUUID().toString();
        UUID sensorTypeId = UUID.randomUUID();
        AlertRuleResponse response = createResponse();
        response.setOwnerUserId(currentUserId);
        response.setSensorTypeId(sensorTypeId);

        when(alertRuleService.createRule(org.mockito.ArgumentMatchers.eq(currentUserId), org.mockito.ArgumentMatchers.any()))
            .thenReturn(response);

        mockMvc.perform(
                post("/iot/alert-rules")
                    .header(AlertRuleController.USER_ID_HEADER, currentUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "sensorTypeId": "%s",
                          "zoneId": "%s",
                          "minThreshold": 10.0,
                          "severity": "HIGH"
                        }
                        """.formatted(sensorTypeId, UUID.randomUUID()))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ownerUserId").value(currentUserId))
            .andExpect(jsonPath("$.sensorTypeId").value(sensorTypeId.toString()));
    }

    @Test
    void getAlertRules_returnsOwnedRows() throws Exception {
        String currentUserId = UUID.randomUUID().toString();
        AlertRuleResponse response = createResponse();
        PagedResponse<AlertRuleResponse> pagedResponse = new PagedResponse<>(java.util.List.of(response), 0, 20, 1, 1, false, false);

        when(alertRuleService.listRules(
            org.mockito.ArgumentMatchers.eq(currentUserId),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(pagedResponse);

        mockMvc.perform(get("/iot/alert-rules").header(AlertRuleController.USER_ID_HEADER, currentUserId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(response.getId().toString()))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void getAlertRuleById_returnsRuleDetail() throws Exception {
        String currentUserId = UUID.randomUUID().toString();
        AlertRuleResponse response = createResponse();

        when(alertRuleService.getRule(currentUserId, response.getId())).thenReturn(response);

        mockMvc.perform(
                get("/iot/alert-rules/{ruleId}", response.getId())
                    .header(AlertRuleController.USER_ID_HEADER, currentUserId)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(response.getId().toString()));
    }

    @Test
    void putAlertRule_returnsUpdatedRule() throws Exception {
        String currentUserId = UUID.randomUUID().toString();
        AlertRuleResponse response = createResponse();
        response.setSeverity("CRITICAL");

        when(alertRuleService.updateRule(
            org.mockito.ArgumentMatchers.eq(currentUserId),
            org.mockito.ArgumentMatchers.eq(response.getId()),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(response);

        mockMvc.perform(
                put("/iot/alert-rules/{ruleId}", response.getId())
                    .header(AlertRuleController.USER_ID_HEADER, currentUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "sensorTypeId": "%s",
                          "deviceId": "%s",
                          "maxThreshold": 45.0,
                          "severity": "CRITICAL",
                          "notifyWeb": true,
                          "notifyMobile": false,
                          "enabled": true
                        }
                        """.formatted(UUID.randomUUID(), UUID.randomUUID()))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.severity").value("CRITICAL"));
    }

    @Test
    void patchAlertRuleEnabled_returnsToggledRule() throws Exception {
        String currentUserId = UUID.randomUUID().toString();
        AlertRuleResponse response = createResponse();
        response.setEnabled(false);

        when(alertRuleService.updateRuleEnabled(currentUserId, response.getId(), false)).thenReturn(response);

        mockMvc.perform(
                patch("/iot/alert-rules/{ruleId}/enabled", response.getId())
                    .header(AlertRuleController.USER_ID_HEADER, currentUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "enabled": false
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void deleteAlertRule_returnsNoContent() throws Exception {
        String currentUserId = UUID.randomUUID().toString();
        UUID ruleId = UUID.randomUUID();
        doNothing().when(alertRuleService).deleteRule(currentUserId, ruleId);

        mockMvc.perform(
                delete("/iot/alert-rules/{ruleId}", ruleId)
                    .header(AlertRuleController.USER_ID_HEADER, currentUserId)
            )
            .andExpect(status().isNoContent());
    }

    private AlertRuleResponse createResponse() {
        AlertRuleResponse response = new AlertRuleResponse();
        response.setId(UUID.randomUUID());
        response.setOwnerUserId(UUID.randomUUID().toString());
        response.setSensorTypeId(UUID.randomUUID());
        response.setDeviceId(UUID.randomUUID());
        response.setMinThreshold(10d);
        response.setMaxThreshold(50d);
        response.setSeverity("HIGH");
        response.setCooldownMinutes(15);
        response.setNotifyWeb(true);
        response.setNotifyMobile(false);
        response.setEnabled(true);
        response.setCreatedAt(Instant.parse("2026-04-15T00:00:00Z"));
        response.setUpdatedAt(Instant.parse("2026-04-15T00:10:00Z"));
        return response;
    }
}
