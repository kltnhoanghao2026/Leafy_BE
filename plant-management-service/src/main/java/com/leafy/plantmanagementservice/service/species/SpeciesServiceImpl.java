package com.leafy.plantmanagementservice.service.species;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.plantmanagementservice.dto.request.species.SpeciesCreateRequest;
import com.leafy.plantmanagementservice.dto.request.species.SpeciesUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.species.SpeciesResponse;
import com.leafy.plantmanagementservice.mapper.SpeciesMapper;
import com.leafy.plantmanagementservice.model.Species;
import com.leafy.plantmanagementservice.repository.SpeciesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpeciesServiceImpl implements SpeciesService {

    private final SpeciesRepository speciesRepository;
    private final SpeciesMapper speciesMapper;

    @Override
    @Transactional
    public SpeciesResponse createSpecies(SpeciesCreateRequest request) {
        log.info("Creating new species: {}", request.getCommonName());
        Species species = speciesMapper.toEntity(request);
        Species savedSpecies = speciesRepository.save(species);
        return speciesMapper.toResponse(savedSpecies);
    }

    @Override
    @Transactional
    public SpeciesResponse updateSpecies(String speciesId, SpeciesUpdateRequest request) {
        log.info("Updating species: {}", speciesId);
        Species species = getSpeciesEntityById(speciesId);
        speciesMapper.updateEntityFromRequest(request, species);
        Species updatedSpecies = speciesRepository.save(species);
        return speciesMapper.toResponse(updatedSpecies);
    }

    @Override
    public SpeciesResponse getSpeciesById(String speciesId) {
        log.info("Fetching species by id: {}", speciesId);
        Species species = getSpeciesEntityById(speciesId);
        return speciesMapper.toResponse(species);
    }

    @Override
    public Species getSpeciesEntityById(String speciesId) {
        return speciesRepository.findById(speciesId)
                .orElseThrow(() -> new AppException(ErrorCode.SPECIES_NOT_FOUND));
    }

    @Override
    public Page<SpeciesResponse> getAllSpecies(Pageable pageable) {
        log.info("Fetching all species with pagination");
        return speciesRepository.findAll(pageable)
                .map(speciesMapper::toResponse);
    }

    @Override
    @Transactional
    public void deleteSpecies(String speciesId) {
        log.info("Deleting species: {}", speciesId);
        if (!speciesRepository.existsById(speciesId)) {
            throw new AppException(ErrorCode.SPECIES_NOT_FOUND);
        }
        speciesRepository.deleteById(speciesId);
    }
}
