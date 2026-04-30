package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.model.enums.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

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
     */
    List<PlantEvent> findProfileCalendarEvents(
            List<String> farmPlotIds,
            List<String> farmZoneIds,
            List<String> plantIds,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
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
}
