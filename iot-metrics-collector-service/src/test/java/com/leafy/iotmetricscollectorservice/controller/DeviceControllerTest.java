package com.leafy.iotmetricscollectorservice.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leafy.iotmetricscollectorservice.dto.device.DeviceConfigResponse;
import com.leafy.iotmetricscollectorservice.dto.device.DeviceResponse;
import com.leafy.iotmetricscollectorservice.dto.device.GenerateClaimCodeResponse;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryExceptionHandler;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceStatus;
import com.leafy.iotmetricscollectorservice.model.enums.ProvisioningStatus;
import com.leafy.iotmetricscollectorservice.service.DeviceConfigService;
import com.leafy.iotmetricscollectorservice.service.DeviceConfigPushService;
import com.leafy.iotmetricscollectorservice.service.DeviceAccessService;
import com.leafy.iotmetricscollectorservice.service.DeviceMediaService;
import com.leafy.iotmetricscollectorservice.service.DeviceService;
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
class DeviceControllerTest {

    @Mock
    private DeviceService deviceService;

    @Mock
    private DeviceConfigService deviceConfigService;

    @Mock
    private DeviceConfigPushService deviceConfigPushService;

    @Mock
    private DeviceMediaService deviceMediaService;

    @Mock
    private DeviceAccessService deviceAccessService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DeviceController(deviceService, deviceConfigService, deviceConfigPushService, deviceMediaService, deviceAccessService))
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

        when(deviceService.generateClaimCode("user-1", deviceId)).thenReturn(response);

        mockMvc.perform(post("/iot/devices/{deviceId}/claim-code", deviceId).header(DeviceController.USER_ID_HEADER, "user-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceId").value(deviceId.toString()))
            .andExpect(jsonPath("$.claimCode").value("ABCD1234"));
    }

    @Test
    void claimDevice_returnsClaimedPayload() throws Exception {
        String userId = UUID.randomUUID().toString();
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
            .andExpect(jsonPath("$.ownerUserId").value(userId))
            .andExpect(jsonPath("$.provisioningStatus").value("CLAIMED"));
    }

    @Test
    void getMyDevices_returnsOwnerDevices() throws Exception {
        String userId = UUID.randomUUID().toString();
        DeviceResponse response = new DeviceResponse();
        response.setId(UUID.randomUUID());
        response.setDeviceUid("device-001");
        PagedResponse<DeviceResponse> pagedResponse = new PagedResponse<>(
            java.util.List.of(response),
            0,
            20,
            1,
            1,
            false,
            false
        );

        when(deviceService.getDevicesByOwner(
            userId,
            0,
            20,
            "createdAt",
            "desc",
            null,
            null,
            null,
            null,
            null
        )).thenReturn(pagedResponse);

        mockMvc.perform(get("/iot/devices/me").header(DeviceController.USER_ID_HEADER, userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].deviceUid").value("device-001"))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void getMyDevices_passesFiltersAndPagination() throws Exception {
        String userId = UUID.randomUUID().toString();
        String zoneId = UUID.randomUUID().toString();
        String farmPlotId = UUID.randomUUID().toString();
        PagedResponse<DeviceResponse> pagedResponse = new PagedResponse<>(
            java.util.List.of(),
            1,
            10,
            0,
            0,
            false,
            true
        );

        when(deviceService.getDevicesByOwner(
            userId,
            1,
            10,
            "lastSeenAt",
            "asc",
            DeviceStatus.ONLINE,
            ProvisioningStatus.CLAIMED,
            zoneId,
            farmPlotId,
            "node"
        )).thenReturn(pagedResponse);

        mockMvc.perform(
                get("/iot/devices/me")
                    .header(DeviceController.USER_ID_HEADER, userId)
                    .param("page", "1")
                    .param("size", "10")
                    .param("sortBy", "lastSeenAt")
                    .param("sortDir", "asc")
                    .param("status", "ONLINE")
                    .param("provisioningStatus", "CLAIMED")
                    .param("zoneId", zoneId)
                    .param("farmPlotId", farmPlotId)
                    .param("keyword", "node")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.hasPrevious").value(true));
    }

    @Test
    void getMyDevices_returnsBusinessErrorWhenSortFieldInvalid() throws Exception {
        String userId = UUID.randomUUID().toString();
        when(deviceService.getDevicesByOwner(
            userId,
            0,
            20,
            "serialNumber",
            "desc",
            null,
            null,
            null,
            null,
            null
        )).thenThrow(TelemetryQueryException.invalidDeviceSortField("serialNumber"));

        mockMvc.perform(
                get("/iot/devices/me")
                    .header(DeviceController.USER_ID_HEADER, userId)
                    .param("sortBy", "serialNumber")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(4628));
    }

    @Test
    void updateDevice_requiresUserHeader() throws Exception {
        mockMvc.perform(
                patch("/iot/devices/{deviceId}", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "deviceName": "Greenhouse Camera"
                        }
                        """)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateDevice_returnsUpdatedDevicePayload() throws Exception {
        String userId = UUID.randomUUID().toString();
        UUID deviceId = UUID.randomUUID();
        String farmPlotId = UUID.randomUUID().toString();
        String zoneId = UUID.randomUUID().toString();
        DeviceResponse response = new DeviceResponse();
        response.setId(deviceId);
        response.setDeviceUid("device-001");
        response.setDeviceCode("IOT-001");
        response.setDeviceName("Greenhouse Camera");
        response.setOwnerUserId(userId);
        response.setFarmPlotId(farmPlotId);
        response.setZoneId(zoneId);
        response.setIsActive(false);
        response.setProvisioningStatus("CLAIMED");

        when(deviceService.updateDevice(
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq(deviceId),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(response);

        mockMvc.perform(
                patch("/iot/devices/{deviceId}", deviceId)
                    .header(DeviceController.USER_ID_HEADER, userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "deviceName": "Greenhouse Camera",
                          "farmPlotId": "%s",
                          "zoneId": "%s",
                          "active": false
                        }
                        """.formatted(farmPlotId, zoneId))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(deviceId.toString()))
            .andExpect(jsonPath("$.deviceName").value("Greenhouse Camera"))
            .andExpect(jsonPath("$.farmPlotId").value(farmPlotId))
            .andExpect(jsonPath("$.zoneId").value(zoneId))
            .andExpect(jsonPath("$.isActive").value(false))
            .andExpect(jsonPath("$.provisioningStatus").value("CLAIMED"));
    }

    @Test
    void updateDevice_returnsForbiddenForNonOwner() throws Exception {
        String userId = UUID.randomUUID().toString();
        UUID deviceId = UUID.randomUUID();
        when(deviceService.updateDevice(
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq(deviceId),
            org.mockito.ArgumentMatchers.any()
        )).thenThrow(TelemetryQueryException.deviceAccessDenied(deviceId));

        mockMvc.perform(
                patch("/iot/devices/{deviceId}", deviceId)
                    .header(DeviceController.USER_ID_HEADER, userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "deviceName": "Greenhouse Camera"
                        }
                        """)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(4636));
    }

    @Test
    void releaseDevice_returnsReleasedDevicePayload() throws Exception {
        String userId = UUID.randomUUID().toString();
        UUID deviceId = UUID.randomUUID();
        DeviceResponse response = new DeviceResponse();
        response.setId(deviceId);
        response.setDeviceUid("device-001");
        response.setDeviceCode("IOT-001");
        response.setDeviceName("Greenhouse Camera");
        response.setOwnerUserId(null);
        response.setFarmPlotId(null);
        response.setZoneId(null);
        response.setIsActive(true);
        response.setProvisioningStatus("PROVISIONED");

        when(deviceService.releaseDevice(userId, deviceId)).thenReturn(response);

        mockMvc.perform(
                post("/iot/devices/{deviceId}/release", deviceId)
                    .header(DeviceController.USER_ID_HEADER, userId)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(deviceId.toString()))
            .andExpect(jsonPath("$.ownerUserId").isEmpty())
            .andExpect(jsonPath("$.farmPlotId").isEmpty())
            .andExpect(jsonPath("$.zoneId").isEmpty())
            .andExpect(jsonPath("$.isActive").value(true))
            .andExpect(jsonPath("$.provisioningStatus").value("PROVISIONED"));
    }

    @Test
    void releaseDevice_returnsForbiddenForNonOwner() throws Exception {
        String userId = UUID.randomUUID().toString();
        UUID deviceId = UUID.randomUUID();
        when(deviceService.releaseDevice(userId, deviceId))
            .thenThrow(TelemetryQueryException.deviceAccessDenied(deviceId));

        mockMvc.perform(
                post("/iot/devices/{deviceId}/release", deviceId)
                    .header(DeviceController.USER_ID_HEADER, userId)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(4636));
    }

    @Test
    void claimDevice_returnsBusinessErrorWhenCodeInvalid() throws Exception {
        String userId = UUID.randomUUID().toString();
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

        mockMvc.perform(get("/iot/devices/{deviceId}/config", deviceId).header(DeviceController.USER_ID_HEADER, "user-1"))
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
                    .header(DeviceController.USER_ID_HEADER, "user-1")
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

        mockMvc.perform(post("/iot/devices/{deviceId}/config/push", deviceId).header(DeviceController.USER_ID_HEADER, "user-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceId").value(deviceId.toString()))
            .andExpect(jsonPath("$.lastPushStatus").value("SENT"));
    }
}
