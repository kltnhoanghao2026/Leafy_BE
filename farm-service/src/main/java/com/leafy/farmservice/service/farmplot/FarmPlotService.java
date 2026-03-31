package com.leafy.farmservice.service.farmplot;

import com.leafy.farmservice.dto.request.farmplot.CreateFarmPlotRequest;
import com.leafy.farmservice.dto.request.farmplot.UpdateFarmPlotRequest;
import com.leafy.farmservice.dto.response.farmplot.FarmPlotResponse;
import java.util.List;

public interface FarmPlotService {
    FarmPlotResponse create(CreateFarmPlotRequest request);
    List<FarmPlotResponse> getByOwner(String ownerUserId);
    FarmPlotResponse getById(String id);
    FarmPlotResponse update(String id, UpdateFarmPlotRequest request);
    void softDelete(String id);
}
