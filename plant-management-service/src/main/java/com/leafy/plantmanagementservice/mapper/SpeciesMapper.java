package com.leafy.plantmanagementservice.mapper;

import com.leafy.plantmanagementservice.dto.request.species.SpeciesCreateRequest;
import com.leafy.plantmanagementservice.dto.request.species.SpeciesUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.species.SpeciesResponse;
import com.leafy.plantmanagementservice.model.Species;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SpeciesMapper {

    Species toEntity(SpeciesCreateRequest request);

    SpeciesResponse toResponse(Species species);

    List<SpeciesResponse> toResponseList(List<Species> species);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    void updateEntityFromRequest(SpeciesUpdateRequest request, @MappingTarget Species species);
}
