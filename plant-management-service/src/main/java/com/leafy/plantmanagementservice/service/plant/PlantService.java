package com.leafy.plantmanagementservice.service.plant;

import com.leafy.plantmanagementservice.dto.request.plant.PlantCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plant.PlantUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plant.PlantResponse;
import com.leafy.plantmanagementservice.model.Plant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlantService {
    PlantResponse createPlant(PlantCreateRequest request);

    PlantResponse updatePlant(String plantId, PlantUpdateRequest request);

    PlantResponse getPlantById(String plantId);

    Plant getPlantEntityById(String plantId);

    Page<PlantResponse> getAllPlants(Pageable pageable);

    Page<PlantResponse> getPlantsBySpeciesId(String speciesId, Pageable pageable);

    Page<PlantResponse> getPlantsByFarmPlotId(String farmPlotId, Pageable pageable);

    void deletePlant(String plantId);
}
