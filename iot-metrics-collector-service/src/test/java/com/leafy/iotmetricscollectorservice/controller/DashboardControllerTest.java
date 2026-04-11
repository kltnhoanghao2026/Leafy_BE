package com.leafy.iotmetricscollectorservice.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leafy.iotmetricscollectorservice.dto.dashboard.DashboardOverviewResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.DeviceDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.ZoneOverviewResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertSummaryResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.DeviceConfigSnapshotResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.DeviceMediaSummaryResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryExceptionHandler;
import com.leafy.iotmetricscollectorservice.service.DashboardQueryService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardQueryService dashboardQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboardQueryService))
            .setControllerAdvice(new TelemetryQueryExceptionHandler())
            .build();
    }

    @Test
    void getZoneOverview_returnsZonePayload() throws Exception {
        UUID zoneId = UUID.randomUUID();
        ZoneOverviewResponse response = new ZoneOverviewResponse();
        response.setZoneId(zoneId);
        response.setOpenAlerts(3);
        AlertSummaryResponse alertSummary = new AlertSummaryResponse();
        alertSummary.setCriticalAlerts(1);
        response.setAlertSummary(alertSummary);
        DeviceMediaSummaryResponse latestMedia = new DeviceMediaSummaryResponse();
        latestMedia.setMediaType("IMAGE");
        response.setLatestMedia(latestMedia);

        when(dashboardQueryService.getZoneOverview(zoneId)).thenReturn(response);

        mockMvc.perform(get("/iot/farm-zones/{zoneId}/overview", zoneId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zoneId").value(zoneId.toString()))
            .andExpect(jsonPath("$.openAlerts").value(3))
            .andExpect(jsonPath("$.alertSummary.criticalAlerts").value(1))
            .andExpect(jsonPath("$.latestMedia.mediaType").value("IMAGE"));
    }

    @Test
    void getFarmOverview_returnsDashboardPayload() throws Exception {
        UUID farmPlotId = UUID.randomUUID();
        DashboardOverviewResponse response = new DashboardOverviewResponse();
        response.setFarmPlotId(farmPlotId);
        response.setTotalDevices(8L);

        when(dashboardQueryService.getFarmOverview(farmPlotId)).thenReturn(response);

        mockMvc.perform(get("/iot/dashboard/overview").param("farmPlotId", farmPlotId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.farmPlotId").value(farmPlotId.toString()))
            .andExpect(jsonPath("$.totalDevices").value(8));
    }

    @Test
    void getDeviceDetail_returnsDevicePayload() throws Exception {
        UUID deviceId = UUID.randomUUID();
        DeviceDetailResponse response = new DeviceDetailResponse();
        response.setDeviceId(deviceId);
        response.setDeviceCode("IOT-001");
        AlertSummaryResponse alertSummary = new AlertSummaryResponse();
        alertSummary.setOpenAlerts(2);
        response.setAlertSummary(alertSummary);
        DeviceConfigSnapshotResponse config = new DeviceConfigSnapshotResponse();
        config.setConfigVersion(4);
        response.setConfig(config);
        DeviceMediaSummaryResponse latestMedia = new DeviceMediaSummaryResponse();
        latestMedia.setTriggerType("ALERT");
        response.setLatestMedia(latestMedia);

        when(dashboardQueryService.getDeviceDetail(deviceId)).thenReturn(response);

        mockMvc.perform(get("/iot/devices/{deviceId}/detail", deviceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceId").value(deviceId.toString()))
            .andExpect(jsonPath("$.deviceCode").value("IOT-001"))
            .andExpect(jsonPath("$.alertSummary.openAlerts").value(2))
            .andExpect(jsonPath("$.config.configVersion").value(4))
            .andExpect(jsonPath("$.latestMedia.triggerType").value("ALERT"));
    }
}
