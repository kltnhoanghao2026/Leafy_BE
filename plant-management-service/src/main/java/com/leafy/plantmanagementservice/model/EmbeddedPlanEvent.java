package com.leafy.plantmanagementservice.model;

import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.plantmanagementservice.model.enums.TargetType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Embedded subdocument representing a single template event within a {@link Plan}.
 * <p>
 * These are <strong>not</strong> standalone {@link PlantEvent} documents; they exist only
 * as an embedded array inside the parent plan. When a plan is applied via {@link PlanApply},
 * the consumer copies each entry into a real {@link PlantEvent} document targeted at the
 * actual plant/plot/zone scope.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmbeddedPlanEvent {

    EventType eventType;

    /**
     * The scope this event targets when the plan is applied.
     * <ul>
     *   <li>{@link TargetType#FARM}      — entire farm plot.</li>
     *   <li>{@link TargetType#FARM_ZONE} — a specific zone within the plot.</li>
     *   <li>{@link TargetType#PLANT}     — an individual plant.</li>
     * </ul>
     */
    TargetType targetType;

    /** Short display label shown on the calendar tile. */
    String note;

    /** Full description including dosage, PPE, method, etc. */
    String description;

    /** Days from the apply start date when this event should begin. */
    Integer daysFromStart;

    /** Duration of the event window in days. */
    Integer durationDays;

    // ── Chemical Application Safety Fields ────────────────────────────────────
    Integer phiDays;
    String ppeRequired;
    String mrlNote;

    /** Estimated cost as a free-text string (e.g. "500,000 VND"). */
    String estimatedCost;

    /** Optional sub-tasks for this event. */
    List<EventTask> tasks;
}
