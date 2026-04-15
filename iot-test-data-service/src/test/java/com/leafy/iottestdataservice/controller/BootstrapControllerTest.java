package com.leafy.iottestdataservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.iottestdataservice.dto.BootstrapRequest;
import com.leafy.iottestdataservice.dto.BootstrapResponse;
import com.leafy.iottestdataservice.service.SeedBootstrapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BootstrapController.class)
class BootstrapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SeedBootstrapService seedBootstrapService;

    @Test
    void bootstrapMinimalReturnsSummary() throws Exception {
        when(seedBootstrapService.bootstrapMinimal(any())).thenReturn(
            new BootstrapResponse("minimal", 1, 1, 2, 4, 2, 2, 4, List.of())
        );

        mockMvc.perform(
                post("/seed/bootstrap/minimal")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new BootstrapRequest(false, null, null)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("minimal"))
            .andExpect(jsonPath("$.createdUsers").value(1))
            .andExpect(jsonPath("$.provisionedDevices").value(2))
            .andExpect(jsonPath("$.createdAlertRules").value(4));
    }

    @Test
    void bootstrapFullReturnsSummary() throws Exception {
        when(seedBootstrapService.bootstrapFull(any())).thenReturn(
            new BootstrapResponse("full", 3, 2, 6, 4, 6, 6, 13, List.of("none"))
        );

        mockMvc.perform(
                post("/seed/bootstrap/full")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new BootstrapRequest(false, null, null)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("full"))
            .andExpect(jsonPath("$.createdFarmPlots").value(2))
            .andExpect(jsonPath("$.claimedDevices").value(6))
            .andExpect(jsonPath("$.warnings[0]").value("none"));
    }
}
