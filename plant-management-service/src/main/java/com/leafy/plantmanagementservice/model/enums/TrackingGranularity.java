package com.leafy.plantmanagementservice.model.enums;

/**
 * Granularity used to fan out a broad-scope {@code PlantEvent} into individual
 * progress entries.
 *
 * <ul>
 *   <li>{@link #NONE} — no progress tracking is generated (used for plant-scope events).</li>
 *   <li>{@link #ZONE} — one progress entry per non-excluded farm zone within the parent event's farm plot.</li>
 *   <li>{@link #PLANT} — one progress entry per non-excluded plant within the parent event's farm plot or zone.</li>
 * </ul>
 */
public enum TrackingGranularity {
    NONE,
    ZONE,
    PLANT
}
