package com.leafy.plantmanagementservice.mapper;

import com.leafy.plantmanagementservice.dto.response.plan.PlanApplyResponse;
import com.leafy.plantmanagementservice.model.PlanApply;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PlanApplyMapper {

    PlanApplyResponse toResponse(PlanApply planApply);

    List<PlanApplyResponse> toResponseList(List<PlanApply> planApplies);
}
