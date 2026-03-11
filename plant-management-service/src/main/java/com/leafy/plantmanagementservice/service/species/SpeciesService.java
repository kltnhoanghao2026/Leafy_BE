package com.leafy.plantmanagementservice.service.species;

import com.leafy.plantmanagementservice.dto.request.species.SpeciesCreateRequest;
import com.leafy.plantmanagementservice.dto.request.species.SpeciesUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.species.SpeciesResponse;
import com.leafy.plantmanagementservice.model.Species;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SpeciesService {
    SpeciesResponse createSpecies(SpeciesCreateRequest request);

    SpeciesResponse updateSpecies(String speciesId, SpeciesUpdateRequest request);

    SpeciesResponse getSpeciesById(String speciesId);

    Species getSpeciesEntityById(String speciesId);

    Page<SpeciesResponse> getAllSpecies(Pageable pageable);

    void deleteSpecies(String speciesId);
}
