package com.leafy.plantmanagementservice.mapper;

import com.leafy.plantmanagementservice.dto.request.treatmentplan.TreatmentPlanCreateRequest;
import com.leafy.plantmanagementservice.dto.response.treatmentplan.TreatmentPlanResponse;
import com.leafy.plantmanagementservice.model.TreatmentPlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TreatmentPlanMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "plantEventIds", ignore = true)
    @Mapping(target = "status", ignore = true)
    TreatmentPlan toEntity(TreatmentPlanCreateRequest request);

    TreatmentPlanResponse toResponse(TreatmentPlan plan);

    List<TreatmentPlanResponse> toResponseList(List<TreatmentPlan> plans);
}
