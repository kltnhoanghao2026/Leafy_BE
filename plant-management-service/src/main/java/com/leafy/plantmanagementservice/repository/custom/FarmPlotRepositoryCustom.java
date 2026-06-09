package com.leafy.plantmanagementservice.repository.custom;

import com.leafy.plantmanagementservice.model.FarmPlot;
import com.leafy.plantmanagementservice.model.enums.FarmPlotStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FarmPlotRepositoryCustom {

    Page<FarmPlot> findPlotsFiltered(
            String searchTerm,
            FarmPlotStatus status,
            String provinceCode,
            Double minAreaM2,
            Double maxAreaM2,
            Pageable pageable);
}
