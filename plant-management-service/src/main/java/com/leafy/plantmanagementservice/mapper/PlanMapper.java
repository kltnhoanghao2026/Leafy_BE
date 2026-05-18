package com.leafy.plantmanagementservice.mapper;

import com.leafy.plantmanagementservice.dto.request.plan.EmbeddedPlanEventRequest;
import com.leafy.plantmanagementservice.dto.request.plan.PlanCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.EventTaskRequest;
import com.leafy.plantmanagementservice.dto.response.plan.EmbeddedPlanEventResponse;
import com.leafy.plantmanagementservice.dto.response.plan.PlanResponse;
import com.leafy.plantmanagementservice.dto.response.plantevent.EventTaskResponse;
import com.leafy.plantmanagementservice.model.EmbeddedPlanEvent;
import com.leafy.plantmanagementservice.model.EventTask;
import com.leafy.plantmanagementservice.model.Plan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PlanMapper {

    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "creatorId", ignore = true)
    @Mapping(target = "ownerId",   ignore = true)
    @Mapping(target = "events",    ignore = true)   // set manually in PlanServiceImpl
    Plan toEntity(PlanCreateRequest request);

    @Mapping(target = "isPublic",    expression = "java(plan.isPublic())")
    @Mapping(target = "sourceType",  expression = "java(plan.getSourceType())")
    @Mapping(target = "applyCount",  ignore = true)
    @Mapping(target = "applies",     ignore = true)
    PlanResponse toResponse(Plan plan);

    @Mapping(target = "isPublic",    expression = "java(plan.isPublic())")
    @Mapping(target = "sourceType",  expression = "java(plan.getSourceType())")
    @Mapping(target = "applyCount",  ignore = true)
    @Mapping(target = "applies",     ignore = true)
    List<PlanResponse> toResponseList(List<Plan> plans);

    // ── Embedded event mappings ───────────────────────────────────────────────

    EmbeddedPlanEvent toEmbeddedEvent(EmbeddedPlanEventRequest request);

    List<EmbeddedPlanEvent> toEmbeddedEventList(List<EmbeddedPlanEventRequest> requests);

    EmbeddedPlanEventResponse toEmbeddedEventResponse(EmbeddedPlanEvent event);

    List<EmbeddedPlanEventResponse> toEmbeddedEventResponseList(List<EmbeddedPlanEvent> events);

    // ── EventTask sub-mappings ────────────────────────────────────────────────

    EventTask toTask(EventTaskRequest request);

    EventTaskResponse toTaskResponse(EventTask task);
}
