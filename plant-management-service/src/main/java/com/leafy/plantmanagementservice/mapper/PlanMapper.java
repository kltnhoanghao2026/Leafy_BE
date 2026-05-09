package com.leafy.plantmanagementservice.mapper;

import com.leafy.plantmanagementservice.dto.request.plan.PlanCreateRequest;
import com.leafy.plantmanagementservice.dto.response.plan.PlanResponse;
import com.leafy.plantmanagementservice.model.Plan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PlanMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "plantEventIds", ignore = true)
    @Mapping(target = "status", ignore = true)
    Plan toEntity(PlanCreateRequest request);

    PlanResponse toResponse(Plan plan);

    List<PlanResponse> toResponseList(List<Plan> plans);
}
