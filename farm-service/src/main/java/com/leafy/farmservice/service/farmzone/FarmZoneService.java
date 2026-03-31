package com.leafy.farmservice.service.farmzone;

import com.leafy.farmservice.dto.request.farmzone.CreateFarmZoneRequest;
import com.leafy.farmservice.dto.request.farmzone.UpdateFarmZoneRequest;
import com.leafy.farmservice.dto.response.farmzone.FarmZoneResponse;
import java.util.List;

public interface FarmZoneService {
    FarmZoneResponse create(String farmPlotId, CreateFarmZoneRequest request);
    List<FarmZoneResponse> getByFarmPlot(String farmPlotId);
    FarmZoneResponse getById(String id);
    FarmZoneResponse update(String id, UpdateFarmZoneRequest request);
    void softDelete(String id);
}
