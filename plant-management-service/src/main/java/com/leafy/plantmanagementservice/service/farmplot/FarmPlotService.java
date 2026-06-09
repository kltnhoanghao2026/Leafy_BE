package com.leafy.plantmanagementservice.service.farmplot;

import com.leafy.plantmanagementservice.dto.request.farmplot.CreateFarmPlotRequest;
import com.leafy.plantmanagementservice.dto.request.farmplot.UpdateFarmPlotRequest;
import com.leafy.plantmanagementservice.dto.response.farmplot.ConsultingFarmerSummaryResponse;
import com.leafy.plantmanagementservice.dto.response.farmplot.FarmPlotResponse;
import com.leafy.plantmanagementservice.model.enums.FarmPlotStatus;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FarmPlotService {
    FarmPlotResponse create(CreateFarmPlotRequest request);
    List<FarmPlotResponse> getByOwner(String ownerProfileId);
    List<FarmPlotResponse> getByOwnerAsConsulting(String farmerProfileId, String expertProfileId);
    FarmPlotResponse getByIdAsConsulting(String farmPlotId, String expertProfileId);
    Map<String, ConsultingFarmerSummaryResponse> getBulkConsultingSummary(List<String> farmerProfileIds, String expertProfileId);
    List<FarmPlotResponse> getAllActive();
    Page<FarmPlotResponse> getFilteredPlots(String searchTerm, FarmPlotStatus status, String provinceCode, Double minAreaM2, Double maxAreaM2, Pageable pageable);
    FarmPlotResponse getById(String id);
    FarmPlotResponse update(String id, UpdateFarmPlotRequest request);
    void softDelete(String id);
}
