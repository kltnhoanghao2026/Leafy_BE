package com.leafy.plantmanagementservice.controller.internal;

import com.leafy.common.dto.ApiResponse;
import com.leafy.plantmanagementservice.dto.request.plantevent.InternalAlertPlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.response.plantevent.InternalAlertPlantEventResponse;
import com.leafy.plantmanagementservice.service.plantevent.PlantEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/plant-events")
@RequiredArgsConstructor
@Slf4j
public class InternalPlantEventController {

    private final PlantEventService plantEventService;

    @PostMapping("/alerts")
    public ResponseEntity<ApiResponse<InternalAlertPlantEventResponse>> createAlertPlantEvent(
            @Valid @RequestBody InternalAlertPlantEventCreateRequest request) {
        log.info("POST /internal/plant-events/alerts - sourceType={}, sourceId={}",
                request.getSourceType(), request.getSourceId());
        InternalAlertPlantEventResponse response = plantEventService.createAlertPlantEvent(request);
        HttpStatus status = response.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(response));
    }
}
