package com.leafy.plantmanagementservice.service.farmplot;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.plantmanagementservice.dto.request.farmplot.CreateFarmPlotRequest;
import com.leafy.plantmanagementservice.dto.request.farmplot.UpdateFarmPlotRequest;
import com.leafy.plantmanagementservice.dto.response.farmplot.ConsultingFarmerSummaryResponse;
import com.leafy.plantmanagementservice.dto.response.farmplot.FarmPlotResponse;
import com.leafy.plantmanagementservice.utils.ConsultingAccessHelper;
import com.leafy.plantmanagementservice.mapper.FarmPlotMapper;
import com.leafy.plantmanagementservice.model.FarmPlot;
import com.leafy.plantmanagementservice.model.enums.ConsultingDataType;
import com.leafy.plantmanagementservice.model.enums.FarmPlotStatus;
import com.leafy.plantmanagementservice.repository.FarmPlotRepository;
import com.leafy.plantmanagementservice.repository.FarmZoneRepository;
import com.leafy.plantmanagementservice.repository.PlantRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FarmPlotServiceImpl implements FarmPlotService {

    FarmPlotRepository farmPlotRepository;
    FarmZoneRepository farmZoneRepository;
    PlantRepository plantRepository;
    FarmPlotMapper farmPlotMapper;
    ConsultingAccessHelper consultingAccessHelper;

    @Override
    public FarmPlotResponse create(CreateFarmPlotRequest request) {
        FarmPlot farmPlot = farmPlotMapper.toEntity(request);
        farmPlot.setCode(generateUniqueFarmPlotCode());
        farmPlot.setActive(true);

        return farmPlotMapper.toResponse(farmPlotRepository.save(farmPlot));
    }

    @Override
    public List<FarmPlotResponse> getByOwner(String ownerProfileId) {
        return farmPlotMapper.toResponseList(
                farmPlotRepository.findByOwnerProfileIdAndActiveTrue(ownerProfileId));
    }

    @Override
    public List<FarmPlotResponse> getByOwnerAsConsulting(String farmerProfileId, String expertProfileId) {
        consultingAccessHelper.requireConsultingAccess(expertProfileId, farmerProfileId, ConsultingDataType.FARM_PLOTS);
        return farmPlotMapper.toResponseList(
                farmPlotRepository.findByOwnerProfileIdAndActiveTrue(farmerProfileId));
    }

    @Override
    public FarmPlotResponse getByIdAsConsulting(String farmPlotId, String expertProfileId) {
        FarmPlot farmPlot = getActiveFarmPlot(farmPlotId);
        consultingAccessHelper.requireConsultingAccess(expertProfileId, farmPlot.getOwnerProfileId(), ConsultingDataType.FARM_PLOTS);
        return farmPlotMapper.toResponse(farmPlot);
    }

    @Override
    public Map<String, ConsultingFarmerSummaryResponse> getBulkConsultingSummary(
            List<String> farmerProfileIds, String expertProfileId) {
        // Validate all farmers in a single call to ConsultingInternalController
        consultingAccessHelper.requireBulkConsultingAccess(expertProfileId, farmerProfileIds);

        // Query 1: fetch all plots for all farmers at once
        List<FarmPlot> allPlots = farmPlotRepository.findByOwnerProfileIdInAndActiveTrue(farmerProfileIds);

        // plotCount per farmer
        Map<String, Long> plotCountByFarmer = allPlots.stream()
                .collect(Collectors.groupingBy(FarmPlot::getOwnerProfileId, Collectors.counting()));

        // Query 2: fetch all zones for those plots at once
        List<String> allPlotIds = allPlots.stream().map(FarmPlot::getId).collect(Collectors.toList());
        Map<String, Long> zoneCountByFarmer;
        if (allPlotIds.isEmpty()) {
            zoneCountByFarmer = java.util.Collections.emptyMap();
        } else {
            // Build a reverse map: plotId -> ownerProfileId
            Map<String, String> ownerByPlotId = allPlots.stream()
                    .collect(Collectors.toMap(FarmPlot::getId, FarmPlot::getOwnerProfileId));
            zoneCountByFarmer = farmZoneRepository.findByFarmPlotIdInAndActiveTrue(allPlotIds)
                    .stream()
                    .collect(Collectors.groupingBy(
                            z -> ownerByPlotId.getOrDefault(z.getFarmPlotId(), ""),
                            Collectors.counting()));
        }

        // Query 3: fetch all plants for all farmers at once
        Map<String, Long> plantCountByFarmer = plantRepository.findByOwnerProfileIdIn(farmerProfileIds)
                .stream()
                .collect(Collectors.groupingBy(
                        p -> p.getOwnerProfileId() != null ? p.getOwnerProfileId() : "",
                        Collectors.counting()));

        // Assemble result map
        Map<String, ConsultingFarmerSummaryResponse> result = new java.util.HashMap<>();
        for (String farmerId : farmerProfileIds) {
            result.put(farmerId, ConsultingFarmerSummaryResponse.builder()
                    .plotCount(plotCountByFarmer.getOrDefault(farmerId, 0L))
                    .zoneCount(zoneCountByFarmer.getOrDefault(farmerId, 0L))
                    .plantCount(plantCountByFarmer.getOrDefault(farmerId, 0L))
                    .build());
        }
        return result;
    }

    @Override
    public List<FarmPlotResponse> getAllActive() {
        return farmPlotMapper.toResponseList(farmPlotRepository.findAllByActiveTrue());
    }

    @Override
    public Page<FarmPlotResponse> getFilteredPlots(String searchTerm, FarmPlotStatus status, String provinceCode, Double minAreaM2, Double maxAreaM2, Pageable pageable) {
        return farmPlotRepository.findPlotsFiltered(searchTerm, status, provinceCode, minAreaM2, maxAreaM2, pageable)
                .map(farmPlotMapper::toResponse);
    }

    @Override
    public FarmPlotResponse getById(String id) {
        return farmPlotMapper.toResponse(getActiveFarmPlot(id));
    }

    @Override
    public FarmPlotResponse update(String id, UpdateFarmPlotRequest request) {
        FarmPlot farmPlot = getActiveFarmPlot(id);

        farmPlotMapper.updateEntityFromRequest(request, farmPlot);

        return farmPlotMapper.toResponse(farmPlotRepository.save(farmPlot));
    }

    @Override
    public void softDelete(String id) {
        FarmPlot farmPlot = getActiveFarmPlot(id);
        farmPlot.setActive(false);
        farmPlotRepository.save(farmPlot);
    }

    private FarmPlot getActiveFarmPlot(String id) {
        return farmPlotRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new AppException(ErrorCode.FARM_PLOT_NOT_FOUND));
    }

    private String generateUniqueFarmPlotCode() {
        String code;
        do {
            code = "FP-" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 8)
                    .toUpperCase(Locale.ROOT);
        } while (farmPlotRepository.existsByCode(code));

        return code;
    }
}
