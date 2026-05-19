package com.leafy.plantmanagementservice.service.plant;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.security.UserPrincipal;
import com.leafy.plantmanagementservice.dto.response.farmplot.FarmPlotResponse;
import com.leafy.plantmanagementservice.dto.response.farmzone.FarmZoneResponse;
import com.leafy.plantmanagementservice.dto.response.plant.BulkOperationResult;
import com.leafy.plantmanagementservice.utils.ConsultingAccessHelper;
import com.leafy.plantmanagementservice.service.farmplot.FarmPlotService;
import com.leafy.plantmanagementservice.service.farmzone.FarmZoneService;
import com.leafy.plantmanagementservice.dto.request.plant.PlantCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plant.PlantUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plant.PlantResponse;
import com.leafy.plantmanagementservice.mapper.PlantMapper;
import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.model.enums.ConsultingDataType;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.leafy.plantmanagementservice.service.species.SpeciesService;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlantServiceImpl implements PlantService {

    private final PlantRepository plantRepository;
    private final PlantMapper plantMapper;
    private final FarmPlotService farmPlotService;
    private final FarmZoneService farmZoneService;
    private final ConsultingAccessHelper consultingAccessHelper;
    private final SpeciesService speciesService;

    @Override
    @Transactional
    public PlantResponse createPlant(PlantCreateRequest request) {
        // Validate speciesId exists
        speciesService.getSpeciesEntityById(request.getSpeciesId());

        Plant plant = plantMapper.toEntity(request);
        plant.setPlantNumber(generatePlantNumber());
        FarmPlotResponse farmPlot = farmPlotService.getById(request.getFarmPlotId());
        plant.setOwnerProfileId(farmPlot.getOwnerProfileId());
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

        if (request.getSpeciesId() != null) {
            speciesService.getSpeciesEntityById(request.getSpeciesId());
        }

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
            userFarmPlotIds = farmPlotService.getByOwner(profileId).stream()
                    .filter(p -> p.getId() != null && !p.getId().isBlank())
                    .map(FarmPlotResponse::getId)
                    .toList();
        } catch (Exception e) {
            log.warn("Could not resolve farm plots for user={}: {}", userId, e.getMessage());
        }

        // ── Resolve all farm zone IDs within those plots ───────────────────────
        List<String> userFarmZoneIds = new java.util.ArrayList<>();
        for (String plotId : userFarmPlotIds) {
            try {
                farmZoneService.getByFarmPlot(plotId).stream()
                        .filter(z -> z.getId() != null && !z.getId().isBlank())
                        .map(FarmZoneResponse::getId)
                        .forEach(userFarmZoneIds::add);
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
    public Page<PlantResponse> getConsultingPlants(String expertProfileId, String farmerProfileId, Pageable pageable) {
        log.info("Expert {} fetching consulting plants for farmer {}", expertProfileId, farmerProfileId);
        consultingAccessHelper.requireConsultingAccess(expertProfileId, farmerProfileId, ConsultingDataType.PLANTS);
        return plantRepository.findByOwnerProfileId(farmerProfileId, pageable)
                .map(plantMapper::toResponse);
    }

    @Override
    public PlantResponse getConsultingPlantById(String plantId, String expertProfileId) {
        log.info("Expert {} fetching consulting plant {}", expertProfileId, plantId);
        Plant plant = getPlantEntityById(plantId);
        consultingAccessHelper.requireConsultingAccess(expertProfileId, plant.getOwnerProfileId(), ConsultingDataType.PLANTS);
        return plantMapper.toResponse(plant);
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

    @Override
    @Transactional
    public BulkOperationResult bulkUpdateStatus(List<String> plantIds, PlantStatus status) {
        String currentProfileId = com.leafy.common.utils.ServiceSecurityUtils.getCurrentProfileId();
        log.info("Bulk status update: profileId={}, count={}, status={}", currentProfileId, plantIds.size(), status);

        List<String> failedIds = new ArrayList<>();
        int successCount = 0;

        for (String plantId : plantIds) {
            try {
                Plant plant = plantRepository.findById(plantId).orElse(null);
                if (plant == null) {
                    log.warn("bulkUpdateStatus: plant not found plantId={}", plantId);
                    failedIds.add(plantId);
                    continue;
                }
                if (!currentProfileId.equals(plant.getOwnerProfileId())) {
                    log.warn("bulkUpdateStatus: ownership mismatch plantId={}, owner={}, requester={}", plantId, plant.getOwnerProfileId(), currentProfileId);
                    failedIds.add(plantId);
                    continue;
                }
                plant.setPlantStatus(status);
                plantRepository.save(plant);
                successCount++;
            } catch (Exception e) {
                log.warn("bulkUpdateStatus: error processing plantId={}: {}", plantId, e.getMessage());
                failedIds.add(plantId);
            }
        }

        return BulkOperationResult.builder()
                .successCount(successCount)
                .failedCount(failedIds.size())
                .failedIds(failedIds)
                .build();
    }

    @Override
    @Transactional
    public BulkOperationResult bulkDelete(List<String> plantIds) {
        log.info("Bulk delete: count={}", plantIds.size());

        List<String> failedIds = new ArrayList<>();
        int successCount = 0;

        for (String plantId : plantIds) {
            try {
                if (!plantRepository.existsById(plantId)) {
                    log.warn("bulkDelete: plant not found plantId={}", plantId);
                    failedIds.add(plantId);
                    continue;
                }
                plantRepository.deleteById(plantId);
                successCount++;
            } catch (Exception e) {
                log.warn("bulkDelete: error deleting plantId={}: {}", plantId, e.getMessage());
                failedIds.add(plantId);
            }
        }

        return BulkOperationResult.builder()
                .successCount(successCount)
                .failedCount(failedIds.size())
                .failedIds(failedIds)
                .build();
    }
}
