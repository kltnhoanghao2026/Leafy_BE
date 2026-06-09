package com.leafy.plantmanagementservice.repository.custom;

import com.leafy.plantmanagementservice.model.FarmZone;
import com.leafy.plantmanagementservice.model.enums.FarmZoneStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FarmZoneRepositoryCustom {

    Page<FarmZone> findZonesFiltered(
            String searchTerm,
            FarmZoneStatus status,
            String cropType,
            String soilType,
            Double minAreaM2,
            Double maxAreaM2,
            Pageable pageable);
}
