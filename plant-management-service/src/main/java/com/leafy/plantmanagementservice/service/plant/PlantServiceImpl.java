package com.leafy.plantmanagementservice.service.plant;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.security.UserPrincipal;
import com.leafy.plantmanagementservice.client.FarmServiceClient;
import com.leafy.plantmanagementservice.client.dto.ExternalApiResponse;
import com.leafy.plantmanagementservice.client.dto.FarmPlotSummary;
import com.leafy.plantmanagementservice.dto.request.plant.PlantCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plant.PlantUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plant.PlantResponse;
import com.leafy.plantmanagementservice.mapper.PlantMapper;
import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.model.enums.PlantStatus;
import com.leafy.plantmanagementservice.repository.PlantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlantServiceImpl implements PlantService {

    private final PlantRepository plantRepository;
    private final PlantMapper plantMapper;
    private final FarmServiceClient farmServiceClient;

    @Override
    @Transactional
    public PlantResponse createPlant(PlantCreateRequest request) {
        Plant plant = plantMapper.toEntity(request);
        plant.setPlantNumber(generatePlantNumber());
        log.info("Creating new plant with auto-generated number: {}", plant.getPlantNumber());
        Plant savedPlant = plantRepository.save(plant);
        return plantMapper.toResponse(savedPlant);
    }

    private String generatePlantNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        return "PLT-" + date + "-" + suffix;
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
    public Page<PlantResponse> getAllPlants(String search, String farmPlotId, String farmZoneId, String speciesId, PlantStatus status, Pageable pageable) {
        log.info("Fetching all plants with filters: search={}, farmPlotId={}, farmZoneId={}, speciesId={}, status={}", search, farmPlotId, farmZoneId, speciesId, status);
        return plantRepository.findPlantsByFilters(search, farmPlotId, farmZoneId, speciesId, status, pageable)
                .map(plantMapper::toResponse);
    }

    @Override
    public Page<PlantResponse> getMyPlants(String search, String farmPlotId, String farmZoneId, String speciesId, PlantStatus status, Pageable pageable) {
        UserPrincipal currentUser = com.leafy.common.utils.ServiceSecurityUtils.getCurrentUser();
        String profileId = currentUser.getProfileId();
        String userId    = currentUser.getUserId();

        // Guard: profileId must be present. It is embedded in the JWT by the auth-service
        // when the access token is generated. An empty profileId means the token was issued
        // before the profile was created, or the JWT is stale. The user must re-login.
        if (!org.springframework.util.StringUtils.hasText(profileId)) {
            log.warn("getMyPlants: profileId is blank for userId={}. " +
                     "The JWT may be stale (issued before profile creation). " +
                     "Ask the user to log out and log back in.", userId);
            return org.springframework.data.domain.Page.empty(pageable);
        }

        // ── Resolve the user's farm plots ─────────────────────────────────────
        List<String> userFarmPlotIds = Collections.emptyList();
        try {
            ExternalApiResponse<List<FarmPlotSummary>> response =
                    farmServiceClient.getPlotsByOwner(profileId);
            if (response != null && response.getData() != null) {
                userFarmPlotIds = response.getData().stream()
                        .filter(p -> p.getId() != null && !p.getId().isBlank())
                        .map(FarmPlotSummary::getId)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Could not resolve farm plots for user={}: {}", userId, e.getMessage());
        }

        // ── Resolve all farm zone IDs within those plots ───────────────────────
        List<String> userFarmZoneIds = new java.util.ArrayList<>();
        for (String plotId : userFarmPlotIds) {
            try {
                ExternalApiResponse<List<com.leafy.plantmanagementservice.client.dto.FarmZoneSummary>> zoneResponse =
                        farmServiceClient.getZonesByPlot(plotId);
                if (zoneResponse != null && zoneResponse.getData() != null) {
                    zoneResponse.getData().stream()
                            .filter(z -> z.getId() != null && !z.getId().isBlank())
                            .map(com.leafy.plantmanagementservice.client.dto.FarmZoneSummary::getId)
                            .forEach(userFarmZoneIds::add);
                }
            } catch (Exception e) {
                log.warn("Could not resolve zones for plot={}: {}", plotId, e.getMessage());
            }
        }

        log.info("getMyPlants: userId={}, profileId={}, plots={}, zones={}, search={}, farmPlotId={}, farmZoneId={}, speciesId={}, status={}",
                userId, profileId, userFarmPlotIds.size(), userFarmZoneIds.size(),
                search, farmPlotId, farmZoneId, speciesId, status);

        return plantRepository.findMyPlants(userFarmPlotIds, userFarmZoneIds, search, farmPlotId, farmZoneId, speciesId, status, pageable)
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
