package com.leafy.iotmetricscollectorservice.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leafy.iotmetricscollectorservice.dto.device.DeviceConfigResponse;
import com.leafy.iotmetricscollectorservice.dto.device.DeviceResponse;
import com.leafy.iotmetricscollectorservice.dto.device.GenerateClaimCodeResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryExceptionHandler;
import com.leafy.iotmetricscollectorservice.service.DeviceConfigService;
import com.leafy.iotmetricscollectorservice.service.DeviceConfigPushService;
import com.leafy.iotmetricscollectorservice.service.DeviceService;
import java.time.Instant;
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
class DeviceControllerTest {

    @Mock
    private DeviceService deviceService;

    @Mock
    private DeviceConfigService deviceConfigService;

    @Mock
    private DeviceConfigPushService deviceConfigPushService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DeviceController(deviceService, deviceConfigService, deviceConfigPushService))
            .setControllerAdvice(new TelemetryQueryExceptionHandler())
            .build();
    }

    @Test
    void provisionDevice_returnsProvisionedPayload() throws Exception {
        DeviceResponse response = new DeviceResponse();
        response.setId(UUID.randomUUID());
        response.setDeviceUid("device-001");
        response.setDeviceCode("IOT-001");
        response.setProvisioningStatus("PROVISIONED");

        when(deviceService.provisionDevice(org.mockito.ArgumentMatchers.any())).thenReturn(response);

        mockMvc.perform(
                post("/iot/devices/provision")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "deviceUid": "device-001",
                          "deviceCode": "IOT-001",
                          "deviceName": "Node Sensor",
                          "deviceType": "NODE"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceUid").value("device-001"))
            .andExpect(jsonPath("$.deviceCode").value("IOT-001"))
            .andExpect(jsonPath("$.provisioningStatus").value("PROVISIONED"));
    }

    @Test
    void generateClaimCode_returnsClaimPayload() throws Exception {
        UUID deviceId = UUID.randomUUID();
        GenerateClaimCodeResponse response = new GenerateClaimCodeResponse();
        response.setDeviceId(deviceId);
        response.setClaimCode("ABCD1234");
        response.setExpiresAt(Instant.parse("2026-04-10T04:00:00Z"));

        when(deviceService.generateClaimCode(deviceId)).thenReturn(response);

        mockMvc.perform(post("/iot/devices/{deviceId}/claim-code", deviceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceId").value(deviceId.toString()))
            .andExpect(jsonPath("$.claimCode").value("ABCD1234"));
    }

    @Test
    void claimDevice_returnsClaimedPayload() throws Exception {
        UUID userId = UUID.randomUUID();
        DeviceResponse response = new DeviceResponse();
        response.setId(UUID.randomUUID());
        response.setDeviceUid("device-001");
        response.setOwnerUserId(userId);
        response.setProvisioningStatus("CLAIMED");

        when(deviceService.claimDevice(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.any()))
            .thenReturn(response);

        mockMvc.perform(
                post("/iot/devices/claim")
                    .header(DeviceController.USER_ID_HEADER, userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "deviceUid": "device-001",
                          "claimCode": "ABCD1234",
                          "farmPlotId": "%s",
                          "zoneId": "%s"
                        }
                        """.formatted(UUID.randomUUID(), UUID.randomUUID()))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceUid").value("device-001"))
            .andExpect(jsonPath("$.ownerUserId").value(userId.toString()))
            .andExpect(jsonPath("$.provisioningStatus").value("CLAIMED"));
    }

    @Test
    void getMyDevices_returnsOwnerDevices() throws Exception {
        UUID userId = UUID.randomUUID();
        DeviceResponse response = new DeviceResponse();
        response.setId(UUID.randomUUID());
        response.setDeviceUid("device-001");

        when(deviceService.getDevicesByOwner(userId)).thenReturn(List.of(response));

        mockMvc.perform(get("/iot/devices/me").header(DeviceController.USER_ID_HEADER, userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].deviceUid").value("device-001"));
    }

    @Test
    void claimDevice_returnsBusinessErrorWhenCodeInvalid() throws Exception {
        UUID userId = UUID.randomUUID();
        when(deviceService.claimDevice(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.any()))
            .thenThrow(TelemetryQueryException.invalidClaimCode("device-001"));

        mockMvc.perform(
                post("/iot/devices/claim")
                    .header(DeviceController.USER_ID_HEADER, userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "deviceUid": "device-001",
                          "claimCode": "WRONG123"
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(4611));
    }

    @Test
    void getDeviceConfig_returnsConfigPayload() throws Exception {
        UUID deviceId = UUID.randomUUID();
        DeviceConfigResponse response = new DeviceConfigResponse();
        response.setDeviceId(deviceId);
        response.setConfigVersion(4);
        response.setSamplingIntervalSec(60);

        when(deviceConfigService.getDeviceConfig(deviceId)).thenReturn(response);

        mockMvc.perform(get("/iot/devices/{deviceId}/config", deviceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceId").value(deviceId.toString()))
            .andExpect(jsonPath("$.configVersion").value(4))
            .andExpect(jsonPath("$.samplingIntervalSec").value(60));
    }

    @Test
    void updateDeviceConfig_returnsUpdatedPayload() throws Exception {
        UUID deviceId = UUID.randomUUID();
        DeviceConfigResponse response = new DeviceConfigResponse();
        response.setDeviceId(deviceId);
        response.setConfigVersion(5);
        response.setSamplingIntervalSec(120);
        response.setPublishIntervalSec(300);
        response.setOfflineTimeoutSec(1200);
        response.setAlertEnabled(false);

        when(deviceConfigService.updateDeviceConfig(org.mockito.ArgumentMatchers.eq(deviceId), org.mockito.ArgumentMatchers.any()))
            .thenReturn(response);

        mockMvc.perform(
                put("/iot/devices/{deviceId}/config", deviceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "samplingIntervalSec": 120,
                          "publishIntervalSec": 300,
                          "offlineTimeoutSec": 1200,
                          "alertEnabled": false
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configVersion").value(5))
            .andExpect(jsonPath("$.alertEnabled").value(false));
    }

    @Test
    void pushDeviceConfig_returnsPushPayload() throws Exception {
        UUID deviceId = UUID.randomUUID();
        DeviceConfigResponse response = new DeviceConfigResponse();
        response.setDeviceId(deviceId);
        response.setConfigVersion(5);
        response.setLastPushStatus("SENT");

        when(deviceConfigPushService.pushConfig(deviceId)).thenReturn(response);

        mockMvc.perform(post("/iot/devices/{deviceId}/config/push", deviceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceId").value(deviceId.toString()))
            .andExpect(jsonPath("$.lastPushStatus").value("SENT"));
    }
}
