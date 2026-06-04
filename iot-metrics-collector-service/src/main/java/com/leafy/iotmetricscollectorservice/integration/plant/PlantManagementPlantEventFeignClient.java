package com.leafy.iotmetricscollectorservice.integration.plant;

import com.leafy.common.dto.ApiResponse;
import com.leafy.iotmetricscollectorservice.integration.plant.dto.AlertPlantEventCreateRequest;
import com.leafy.iotmetricscollectorservice.integration.plant.dto.InternalAlertPlantEventResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "plant-management-service", path = "/internal/plant-events")
public interface PlantManagementPlantEventFeignClient {

    @PostMapping("/alerts")
    ApiResponse<InternalAlertPlantEventResponse> createAlertPlantEvent(
        @RequestBody AlertPlantEventCreateRequest request
    );
}
