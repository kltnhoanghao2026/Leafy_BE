package com.leafy.iotmetricscollectorservice.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leafy.iotmetricscollectorservice.dto.dashboard.LatestReadingItemResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryExceptionHandler;
import com.leafy.iotmetricscollectorservice.service.DeviceAccessService;
import com.leafy.iotmetricscollectorservice.service.TelemetryQueryService;
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
class TelemetryQueryControllerTest {

    @Mock
    private TelemetryQueryService telemetryQueryService;

    @Mock
    private DeviceAccessService deviceAccessService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TelemetryQueryController(telemetryQueryService, deviceAccessService))
            .setControllerAdvice(new TelemetryQueryExceptionHandler())
            .build();
    }

    @Test
    void getLatestReadingsByDevice_returnsApiResponsePayload() throws Exception {
        UUID deviceId = UUID.randomUUID();
        LatestReadingItemResponse item = new LatestReadingItemResponse();
        item.setSensorCode("soilTemp");
        item.setSensorName("Soil Temperature");
        item.setUnit("C");

        when(telemetryQueryService.getLatestReadingsByDevice(deviceId)).thenReturn(List.of(item));

        mockMvc.perform(get("/iot/devices/{deviceId}/latest-readings", deviceId).header(DeviceController.USER_ID_HEADER, "user-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sensorCode").value("soilTemp"))
            .andExpect(jsonPath("$[0].sensorName").value("Soil Temperature"));
    }

    @Test
    void getDeviceSensorChart_rejectsUnsupportedRangeBeforeCallingService() throws Exception {
        mockMvc.perform(
            get("/iot/devices/{deviceId}/charts", UUID.randomUUID())
                .header(DeviceController.USER_ID_HEADER, "user-1")
                .param("sensorCode", "soilTemp")
                .param("range", "2W")
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(4603));

        verifyNoInteractions(telemetryQueryService);
    }
}
