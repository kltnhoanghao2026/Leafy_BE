package com.leafy.plantmanagementservice.model.enums;

/**
 * The scope to which a {@code PlantEvent} or {@code EmbeddedPlanEvent} is targeted.
 *
 * <ul>
 *   <li>{@link #FARM}      — event applies to an entire farm plot.</li>
 *   <li>{@link #FARM_ZONE} — event applies to a specific zone within a farm plot.</li>
 *   <li>{@link #PLANT}     — event applies to an individual plant.</li>
 * </ul>
 */
public enum TargetType {
    FARM,
    FARM_ZONE,
    PLANT
}
