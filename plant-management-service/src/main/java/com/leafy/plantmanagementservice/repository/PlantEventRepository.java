package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.plantmanagementservice.model.enums.TargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PlantEventRepository extends MongoRepository<PlantEvent, String>, PlantEventRepositoryCustom {

    Page<PlantEvent> findByPlantId(String plantId, Pageable pageable);

    Page<PlantEvent> findByPlantIdAndEventType(String plantId, EventType eventType, Pageable pageable);

    Page<PlantEvent> findByPlantIdAndPlanned(String plantId, boolean planned, Pageable pageable);

    Page<PlantEvent> findByPlanApplyId(String planApplyId, Pageable pageable);

    Page<PlantEvent> findByFarmPlotId(String farmPlotId, Pageable pageable);

    Page<PlantEvent> findByFarmZoneId(String farmZoneId, Pageable pageable);

    Page<PlantEvent> findByEventType(EventType eventType, Pageable pageable);

    List<PlantEvent> findByFarmPlotIdAndCalculatedStartDateLessThanEqualAndCalculatedEndDateGreaterThanEqual(
            String farmPlotId, LocalDate rangeEnd, LocalDate rangeStart);

    List<PlantEvent> findByFarmZoneIdAndCalculatedStartDateLessThanEqualAndCalculatedEndDateGreaterThanEqual(
            String farmZoneId, LocalDate rangeEnd, LocalDate rangeStart);

    List<PlantEvent> findByPlantIdAndCalculatedStartDateLessThanEqualAndCalculatedEndDateGreaterThanEqual(
            String plantId, LocalDate rangeEnd, LocalDate rangeStart);

    List<PlantEvent> findByFarmPlotIdInAndCalculatedStartDateLessThanEqualAndCalculatedEndDateGreaterThanEqual(
            List<String> farmPlotIds, LocalDate rangeEnd, LocalDate rangeStart);

    List<PlantEvent> findByParentPlantEventId(String parentPlantEventId);

    /** Find all events for a PlanApply that are NOT completed. */
    List<PlantEvent> findByPlanApplyIdAndCompletedFalse(String planApplyId);

    List<PlantEvent> findByPlanApplyIdAndEventType(String planApplyId, EventType eventType);

    /**
     * Finds all parent-level events (FARM or FARM_ZONE scope) for a given plan apply.
     * Used by applyPlantScope to resolve parent plant events when applying a plan to a single plant.
     *
     * @param planApplyId the plan apply ID
     * @param targetType the parent scope (FARM or FARM_ZONE)
     * @return list of parent plant events ordered by calculatedStartDate
     */
    List<PlantEvent> findByPlanApplyIdAndTargetTypeOrderByCalculatedStartDate(String planApplyId, TargetType targetType);

    /**
     * Counts incomplete (completed = false) PlantEvents for a PlanApply.
     * Used to determine how many events still need to be completed.
     */
    @Query(value = "{ 'planApplyId': ?0, 'completed': false }", count = true)
    long countIncompleteEventsByPlanApplyId(String planApplyId);
}
