package com.leafy.farmservice.service.farmplot;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.farmservice.dto.request.farmplot.CreateFarmPlotRequest;
import com.leafy.farmservice.dto.request.farmplot.UpdateFarmPlotRequest;
import com.leafy.farmservice.dto.response.farmplot.FarmPlotResponse;
import com.leafy.farmservice.mapper.FarmPlotMapper;
import com.leafy.farmservice.model.FarmPlot;
import com.leafy.farmservice.repository.FarmPlotRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FarmPlotServiceImpl implements FarmPlotService {

    private final FarmPlotRepository farmPlotRepository;
    private final FarmPlotMapper farmPlotMapper;

    @Override
    public FarmPlotResponse create(CreateFarmPlotRequest request) {
        if (request.getCode() != null && !request.getCode().isBlank()
                && farmPlotRepository.existsByCodeAndActiveTrue(request.getCode())) {
            throw new AppException(ErrorCode.FARM_PLOT_CODE_DUPLICATE);
        }

        FarmPlot farmPlot = farmPlotMapper.toEntity(request);
        farmPlot.setActive(true);

        return farmPlotMapper.toResponse(farmPlotRepository.save(farmPlot));
    }

    @Override
    public List<FarmPlotResponse> getByOwner(String ownerUserId) {
        return farmPlotMapper.toResponseList(
                farmPlotRepository.findByOwnerUserIdAndActiveTrue(ownerUserId));
    }

    @Override
    public FarmPlotResponse getById(String id) {
        return farmPlotMapper.toResponse(getActiveFarmPlot(id));
    }

    @Override
    public FarmPlotResponse update(String id, UpdateFarmPlotRequest request) {
        FarmPlot farmPlot = getActiveFarmPlot(id);

        if (request.getCode() != null && !request.getCode().isBlank()
                && !request.getCode().equals(farmPlot.getCode())
                && farmPlotRepository.existsByCodeAndActiveTrue(request.getCode())) {
            throw new AppException(ErrorCode.FARM_PLOT_CODE_DUPLICATE);
        }

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
}
