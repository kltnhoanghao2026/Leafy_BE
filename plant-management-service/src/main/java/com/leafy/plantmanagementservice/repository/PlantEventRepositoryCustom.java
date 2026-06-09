package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.plantmanagementservice.model.enums.TargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface PlantEventRepositoryCustom {

    /**
     * Admin multi-criteria filter for plant events.
     *
     * @param eventType  optional – filter by event type
     * @param planned    optional – true = scheduled, false = immediate/detected, null = all
     * @param farmPlotId optional – filter by farm plot ID
     * @param farmZoneId optional – filter by farm zone ID
     * @param pageable   pagination / sorting
     */
    Page<PlantEvent> findAllByFilters(
            EventType eventType,
            Boolean planned,
            String farmPlotId,
            String farmZoneId,
            Pageable pageable
    );

    /**
     * Find calendar events within a date range that belong to the current user.
     * Matches events where:
     * <ul>
     *   <li>{@code farmPlotId IN farmPlotIds} — events attached directly to a plot</li>
     *   <li>{@code farmZoneId IN farmZoneIds} — events attached directly to a zone</li>
     *   <li>{@code plantId IN plantIds}       — events attached to plants in those plots/zones</li>
     * </ul>
     *
     * @param targetType optional – if provided, additionally filters by TargetType
     */
    List<PlantEvent> findProfileCalendarEvents(
            List<String> farmPlotIds,
            List<String> farmZoneIds,
            List<String> plantIds,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            TargetType targetType
    );
    /**
     * Calendar events where farmPlotId OR farmZoneId matches (both filters provided).
     */
    List<PlantEvent> findByPlotOrZoneAndDateRange(
            String farmPlotId,
            String farmZoneId,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    );

    /**
     * Calendar events for a single plant, handling null calculatedEndDate
     * (treats such events as single-day events on their calculatedStartDate).
     */
    List<PlantEvent> findByPlantIdAndDateRange(
            String plantId,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    );

    /**
     * Calendar events for a farm plot, handling null calculatedEndDate.
     */
    List<PlantEvent> findByFarmPlotIdAndDateRange(
            String farmPlotId,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    );

    /**
     * Calendar events for a farm zone, handling null calculatedEndDate.
     */
    List<PlantEvent> findByFarmZoneIdAndDateRange(
            String farmZoneId,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    );

    /**
     * Calendar events linked to a specific PlanApply instance (by planApplyId) that overlap the date range.
     */
    List<PlantEvent> findByPlanApplyIdAndDateRange(
            String planApplyId,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    );

    // ── Stats aggregation methods ─────────────────────────────────────────────

    /**
     * Count events grouped by {@code eventType} for a user's profile scope.
     */
    Map<String, Long> countByEventTypeForProfile(
            List<String> farmPlotIds,
            List<String> farmZoneIds,
            List<String> plantIds
    );

    /**
     * Count events grouped by {@code eventType} for a user's profile scope,
     * scoped to a specific date range.
     */
    Map<String, Long> countByEventTypeForProfile(
            List<String> farmPlotIds,
            List<String> farmZoneIds,
            List<String> plantIds,
            LocalDate startDate,
            LocalDate endDate
    );

    /**
     * Count events matching profile scope + optional date + optional completion filter.
     *
     * @param farmPlotIds  user's farm plot IDs
     * @param farmZoneIds  user's farm zone IDs
     * @param plantIds     user's plant IDs
     * @param startDate    if non-null, events must overlap on or after this date
     * @param endDate      if non-null, events must overlap on or before this date
     * @param completed    if non-null, filter by completion status
     * @param overdue      if true, filter for events whose calculatedEndDate < today AND completed = false
     */
    long countProfileEventsFiltered(
            List<String> farmPlotIds,
            List<String> farmZoneIds,
            List<String> plantIds,
            LocalDate startDate,
            LocalDate endDate,
            Boolean completed,
            boolean overdue
    );

    /**
     * Fetch the N most-recently-created events for the user's profile scope.
     */
    List<PlantEvent> findRecentProfileEvents(
            List<String> farmPlotIds,
            List<String> farmZoneIds,
            List<String> plantIds,
            int limit
    );
}
