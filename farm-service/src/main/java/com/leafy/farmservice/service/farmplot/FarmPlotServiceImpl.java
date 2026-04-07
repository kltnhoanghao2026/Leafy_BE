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
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FarmPlotServiceImpl implements FarmPlotService {

    FarmPlotRepository farmPlotRepository;
    FarmPlotMapper farmPlotMapper;

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
    public List<FarmPlotResponse> getAllActive() {
        return farmPlotMapper.toResponseList(farmPlotRepository.findAllByActiveTrue());
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
