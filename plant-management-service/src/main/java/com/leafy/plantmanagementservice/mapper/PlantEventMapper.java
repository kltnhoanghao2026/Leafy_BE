package com.leafy.plantmanagementservice.mapper;

import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plantevent.PlantEventResponse;
import com.leafy.plantmanagementservice.model.PlantEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PlantEventMapper {

    @Mapping(target = "id", ignore = true)
    PlantEvent toEntity(PlantEventCreateRequest request);

    PlantEventResponse toResponse(PlantEvent event);

    List<PlantEventResponse> toResponseList(List<PlantEvent> events);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "plantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "planned", source = "isPlanned")
    void updateEntityFromRequest(PlantEventUpdateRequest request, @MappingTarget PlantEvent event);
}
