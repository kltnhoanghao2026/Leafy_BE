package com.leafy.plantmanagementservice.service.plant;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.plantmanagementservice.dto.request.plant.PlantCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plant.PlantUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plant.PlantResponse;
import com.leafy.plantmanagementservice.mapper.PlantMapper;
import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.repository.PlantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlantServiceImpl implements PlantService {

    private final PlantRepository plantRepository;
    private final PlantMapper plantMapper;

    @Override
    @Transactional
    public PlantResponse createPlant(PlantCreateRequest request) {
        log.info("Creating new plant: {}", request.getPlantNumber());
        Plant plant = plantMapper.toEntity(request);
        Plant savedPlant = plantRepository.save(plant);
        return plantMapper.toResponse(savedPlant);
    }

    @Override
    @Transactional
    public PlantResponse updatePlant(String plantId, PlantUpdateRequest request) {
        log.info("Updating plant: {}", plantId);
        Plant plant = getPlantEntityById(plantId);
        plantMapper.updateEntityFromRequest(request, plant);
        Plant updatedPlant = plantRepository.save(plant);
        return plantMapper.toResponse(updatedPlant);
    }

    @Override
    public PlantResponse getPlantById(String plantId) {
        log.info("Fetching plant by id: {}", plantId);
        Plant plant = getPlantEntityById(plantId);
        return plantMapper.toResponse(plant);
    }

    @Override
    public Plant getPlantEntityById(String plantId) {
        return plantRepository.findById(plantId)
                .orElseThrow(() -> new AppException(ErrorCode.PLANT_NOT_FOUND));
    }

    @Override
    public Page<PlantResponse> getAllPlants(Pageable pageable) {
        log.info("Fetching all plants with pagination");
        return plantRepository.findAll(pageable)
                .map(plantMapper::toResponse);
    }

    @Override
    public Page<PlantResponse> getPlantsBySpeciesId(String speciesId, Pageable pageable) {
        log.info("Fetching plants by species id: {}", speciesId);
        return plantRepository.findBySpeciesId(speciesId, pageable)
                .map(plantMapper::toResponse);
    }

    @Override
    public Page<PlantResponse> getPlantsByFarmPlotId(String farmPlotId, Pageable pageable) {
        log.info("Fetching plants by farm plot id: {}", farmPlotId);
        return plantRepository.findByFarmPlotId(farmPlotId, pageable)
                .map(plantMapper::toResponse);
    }

    @Override
    @Transactional
    public void deletePlant(String plantId) {
        log.info("Deleting plant: {}", plantId);
        if (!plantRepository.existsById(plantId)) {
            throw new AppException(ErrorCode.PLANT_NOT_FOUND);
        }
        plantRepository.deleteById(plantId);
    }
}
