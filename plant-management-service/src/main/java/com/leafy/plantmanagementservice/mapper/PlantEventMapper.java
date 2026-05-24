package com.leafy.plantmanagementservice.mapper;

import com.leafy.plantmanagementservice.dto.request.plantevent.EventTaskRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plantevent.EventTaskResponse;
import com.leafy.plantmanagementservice.dto.response.plantevent.PlantEventResponse;
import com.leafy.plantmanagementservice.model.EventTask;
import com.leafy.plantmanagementservice.model.PlantEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PlantEventMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "completed", ignore = true)
    PlantEvent toEntity(PlantEventCreateRequest request);

    @Mapping(target = "children", ignore = true)
    @Mapping(target = "isLastIncompleteEventForApply", ignore = true)
    PlantEventResponse toResponse(PlantEvent event);

    List<PlantEventResponse> toResponseList(List<PlantEvent> events);

    EventTask toTask(EventTaskRequest request);

    EventTaskResponse toTaskResponse(EventTask task);

    List<EventTask> toTaskList(List<EventTaskRequest> requests);

    List<EventTaskResponse> toTaskResponseList(List<EventTask> tasks);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "plantId", ignore = true)
    @Mapping(target = "farmPlotId", ignore = true)
    @Mapping(target = "farmZoneId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "planned", source = "isPlanned")
    @Mapping(target = "trackingGranularity", ignore = true)
    @Mapping(target = "excludedPlantIds", ignore = true)
    @Mapping(target = "excludedFarmZoneIds", ignore = true)
    @Mapping(target = "parentPlantEventId", ignore = true)
    @Mapping(target = "attachmentIds", ignore = true)
    // targetType is intentionally NOT ignored — allows scope correction via PATCH
    void updateEntityFromRequest(PlantEventUpdateRequest request, @MappingTarget PlantEvent event);

    // ── Tree-building utilities ──────────────────────────────────────────────

    /**
     * Converts a flat list of responses into a tree where parent events contain
     * their children. Events with a {@code parentPlantEventId} whose parent is
     * present in the list are nested; all others remain at the root level.
     */
    default List<PlantEventResponse> buildEventTree(List<PlantEventResponse> flatList) {
        if (flatList == null || flatList.isEmpty()) {
            return flatList;
        }
        Map<String, PlantEventResponse> byId = new LinkedHashMap<>();
        for (PlantEventResponse r : flatList) {
            if (r.getChildren() == null) {
                r.setChildren(new ArrayList<>());
            }
            byId.put(r.getId(), r);
        }
        List<PlantEventResponse> roots = new ArrayList<>();
        for (PlantEventResponse r : flatList) {
            String parentId = r.getParentPlantEventId();
            if (parentId != null && byId.containsKey(parentId)) {
                byId.get(parentId).getChildren().add(r);
            } else {
                roots.add(r);
            }
        }
        return roots;
    }

    /**
     * Maps entities to responses and assembles the parent→child tree.
     */
    default List<PlantEventResponse> toNestedResponseList(List<PlantEvent> events) {
        return buildEventTree(toResponseList(events));
    }

    /**
     * Applies tree building to a Page of responses.
     * Nests child events into their parents for the current page content.
     */
    default Page<PlantEventResponse> toNestedResponsePage(Page<PlantEvent> page) {
        List<PlantEventResponse> flat = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        List<PlantEventResponse> nested = buildEventTree(new ArrayList<>(flat));
        return new PageImpl<>(nested, page.getPageable(), page.getTotalElements());
    }
}
