package com.leafy.plantmanagementservice.service.farmzone;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.plantmanagementservice.dto.request.farmzone.CreateFarmZoneRequest;
import com.leafy.plantmanagementservice.dto.request.farmzone.UpdateFarmZoneRequest;
import com.leafy.plantmanagementservice.dto.response.farmzone.FarmZoneResponse;
import com.leafy.plantmanagementservice.model.FarmPlot;
import com.leafy.plantmanagementservice.utils.ConsultingAccessHelper;
import com.leafy.plantmanagementservice.mapper.FarmZoneMapper;
import com.leafy.plantmanagementservice.model.FarmZone;
import com.leafy.plantmanagementservice.model.enums.ConsultingDataType;
import com.leafy.plantmanagementservice.model.enums.FarmZoneStatus;
import com.leafy.plantmanagementservice.repository.FarmPlotRepository;
import com.leafy.plantmanagementservice.repository.FarmZoneRepository;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FarmZoneServiceImpl implements FarmZoneService {

    FarmZoneRepository farmZoneRepository;
    FarmPlotRepository farmPlotRepository;
    FarmZoneMapper farmZoneMapper;
    ConsultingAccessHelper consultingAccessHelper;

    @Override
    public FarmZoneResponse create(String farmPlotId, CreateFarmZoneRequest request) {
        FarmPlot farmPlot = farmPlotRepository.findByIdAndActiveTrue(farmPlotId)
                .orElseThrow(() -> new AppException(ErrorCode.FARM_PLOT_NOT_FOUND));

        if (farmZoneRepository.existsByFarmPlotIdAndZoneNameAndActiveTrue(farmPlotId, request.getZoneName())) {
            throw new AppException(ErrorCode.FARM_ZONE_NAME_DUPLICATE);
        }

        FarmZone farmZone = farmZoneMapper.toEntity(request);
        farmZone.setFarmPlotId(farmPlotId);
        farmZone.setOwnerProfileId(farmPlot.getOwnerProfileId());
        farmZone.setActive(true);

        return populateOwnerProfileId(farmZoneMapper.toResponse(farmZoneRepository.save(farmZone)));
    }

    @Override
    public List<FarmZoneResponse> getByFarmPlot(String farmPlotId) {
        return farmZoneMapper.toResponseList(
                farmZoneRepository.findByFarmPlotIdAndActiveTrue(farmPlotId))
                .stream()
                .map(this::populateOwnerProfileId)
                .toList();
    }

    @Override
    public List<FarmZoneResponse> getByFarmPlotAsConsulting(String farmPlotId, String expertProfileId) {
        FarmPlot farmPlot = farmPlotRepository.findByIdAndActiveTrue(farmPlotId)
                .orElseThrow(() -> new AppException(ErrorCode.FARM_PLOT_NOT_FOUND));
        consultingAccessHelper.requireConsultingAccess(expertProfileId, farmPlot.getOwnerProfileId(), ConsultingDataType.FARM_PLOTS);
        return farmZoneMapper.toResponseList(
                farmZoneRepository.findByFarmPlotIdAndActiveTrue(farmPlotId))
                .stream()
                .map(this::populateOwnerProfileId)
                .toList();
    }

    @Override
    public List<FarmZoneResponse> getAllActive() {
        return farmZoneMapper.toResponseList(farmZoneRepository.findAllByActiveTrue())
                .stream()
                .map(this::populateOwnerProfileId)
                .toList();
    }

    @Override
    public Page<FarmZoneResponse> getFilteredZones(String searchTerm, FarmZoneStatus status, String cropType, String soilType, Double minAreaM2, Double maxAreaM2, Pageable pageable) {
        return farmZoneRepository.findZonesFiltered(searchTerm, status, cropType, soilType, minAreaM2, maxAreaM2, pageable)
                .map(farmZoneMapper::toResponse);
    }

    @Override
    public FarmZoneResponse getById(String id) {
        return farmZoneMapper.toResponse(getActiveFarmZone(id));
    }

    @Override
    public FarmZoneResponse update(String id, UpdateFarmZoneRequest request) {
        FarmZone farmZone = getActiveFarmZone(id);

        if (request.getZoneName() != null
                && !request.getZoneName().equals(farmZone.getZoneName())
                && farmZoneRepository.existsByFarmPlotIdAndZoneNameAndActiveTrue(
                        farmZone.getFarmPlotId(), request.getZoneName())) {
            throw new AppException(ErrorCode.FARM_ZONE_NAME_DUPLICATE);
        }

        farmZoneMapper.updateEntityFromRequest(request, farmZone);

        return farmZoneMapper.toResponse(farmZoneRepository.save(farmZone));
    }

    @Override
    public void softDelete(String id) {
        FarmZone farmZone = getActiveFarmZone(id);
        farmZone.setActive(false);
        farmZoneRepository.save(farmZone);
    }

    private FarmZone getActiveFarmZone(String id) {
        return farmZoneRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new AppException(ErrorCode.FARM_ZONE_NOT_FOUND));
    }

    @Override
    public List<FarmZoneResponse> getByOwnerProfileId(String ownerProfileId) {
        List<FarmPlot> plots = farmPlotRepository.findByOwnerProfileIdAndActiveTrue(ownerProfileId);
        if (plots.isEmpty()) {
            return List.of();
        }
        List<String> plotIds = plots.stream().map(FarmPlot::getId).toList();
        return farmZoneRepository.findByFarmPlotIdInAndActiveTrue(plotIds)
                .stream()
                .map(farmZoneMapper::toResponse)
                .map(this::populateOwnerProfileId)
                .toList();
    }

    private FarmZoneResponse populateOwnerProfileId(FarmZoneResponse response) {
        if (response.getOwnerProfileId() != null) {
            return response;
        }
        if (response.getFarmPlotId() != null) {
            farmPlotRepository.findByIdAndActiveTrue(response.getFarmPlotId())
                    .ifPresent(plot -> response.setOwnerProfileId(plot.getOwnerProfileId()));
        }
        return response;
    }
}
