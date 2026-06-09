package com.leafy.plantmanagementservice.model;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * An individual sub-task embedded inside a {@link PlantEvent}.
 * Stored as an inline array in the parent document (no separate collection).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EventTask {

    /** Short label describing what needs to be done. */
    String title;

    /** Optional longer explanation, dosage instructions, or notes for this task. */
    String description;

    /** Display order within the parent event's task list (0-based). */
    Integer order;

    /** Estimated cost or resource needed for this specific task (free-text). */
    String estimatedCost;

    /** Whether this specific sub-task has been completed. */
    boolean completed;
}
