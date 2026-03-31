package com.leafy.farmservice.service.farmzone;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.farmservice.dto.request.farmzone.CreateFarmZoneRequest;
import com.leafy.farmservice.dto.request.farmzone.UpdateFarmZoneRequest;
import com.leafy.farmservice.dto.response.farmzone.FarmZoneResponse;
import com.leafy.farmservice.mapper.FarmZoneMapper;
import com.leafy.farmservice.model.FarmZone;
import com.leafy.farmservice.repository.FarmPlotRepository;
import com.leafy.farmservice.repository.FarmZoneRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FarmZoneServiceImpl implements FarmZoneService {

    private final FarmZoneRepository farmZoneRepository;
    private final FarmPlotRepository farmPlotRepository;
    private final FarmZoneMapper farmZoneMapper;

    @Override
    public FarmZoneResponse create(String farmPlotId, CreateFarmZoneRequest request) {
        farmPlotRepository.findByIdAndActiveTrue(farmPlotId)
                .orElseThrow(() -> new AppException(ErrorCode.FARM_PLOT_NOT_FOUND));

        if (farmZoneRepository.existsByFarmPlotIdAndZoneNameAndActiveTrue(farmPlotId, request.getZoneName())) {
            throw new AppException(ErrorCode.FARM_ZONE_NAME_DUPLICATE);
        }

        FarmZone farmZone = farmZoneMapper.toEntity(request);
        farmZone.setFarmPlotId(farmPlotId);
        farmZone.setActive(true);

        return farmZoneMapper.toResponse(farmZoneRepository.save(farmZone));
    }

    @Override
    public List<FarmZoneResponse> getByFarmPlot(String farmPlotId) {
        return farmZoneMapper.toResponseList(
                farmZoneRepository.findByFarmPlotIdAndActiveTrue(farmPlotId));
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
}
