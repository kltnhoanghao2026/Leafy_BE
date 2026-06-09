package com.leafy.plantmanagementservice.model;

import com.leafy.common.model.BaseModel;
import com.leafy.plantmanagementservice.model.enums.PlanStatus;
import com.leafy.plantmanagementservice.model.enums.TrackingGranularity;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDate;
import java.util.List;

/**
 * Tracks an individual application of a {@link Plan} to a specific target scope.
 *
 * <p>Each time a user applies a plan (via {@code POST /plans/{id}/apply}), a new
 * {@code PlanApply} document is created. It owns the generated {@link PlantEvent}
 * IDs and carries its own lifecycle {@link PlanStatus}, decoupled from the parent
 * Plan template.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "plan_applies")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlanApply extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    // ── Link to parent Plan ──────────────────────────────────────────────────

    /** MongoDB ID of the parent {@link Plan} template. */
    @Indexed
    String planId;

    // ── Who applied ──────────────────────────────────────────────────────────

    /** Profile ID of the user who triggered this application. */
    @Indexed
    String appliedById;

    // ── Target scope ─────────────────────────────────────────────────────────

    @Indexed
    String plantId;

    @Indexed
    String farmPlotId;

    @Indexed
    String farmZoneId;

    // ── Application parameters ───────────────────────────────────────────────

    String planName;

    String diseaseName;

    String targetName;

    /** The reference start date for computing event dates. */
    LocalDate startDate;

    /** Tracking granularity for broad-scope applies. */
    TrackingGranularity trackingGranularity;

    /** Plant IDs explicitly excluded from this application. */
    List<String> excludedPlantIds;

    /** Farm zone IDs explicitly excluded from this application. */
    List<String> excludedFarmZoneIds;

    // ── Generated events ─────────────────────────────────────────────────────

    /**
     * MongoDB {@link PlantEvent} IDs created by this specific application.
     * Populated after the Kafka consumer processes the apply request.
     */
    List<String> plantEventIds;

    /**
     * The ID of the last event in the treatment sequence.
     * When this event is completed by the user, the frontend prompts them to
     * indicate whether the overall treatment was successful (success=true/false),
     * which is then stored via {@code completeApply}.
     */
    String lastEventId;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /** Current status of this application. Defaults to {@link PlanStatus#PENDING}. */
    @Builder.Default
    PlanStatus status = PlanStatus.PENDING;

    /**
     * Whether this application can be cancelled.
     * Defaults to {@code true} when a new apply is created.
     * Set to {@code false} after the user successfully cancels it,
     * or when the apply reaches a terminal state (COMPLETED/CANCELLED).
     */
    @Builder.Default
    Boolean canCancel = true;

    // ── Outcome ──────────────────────────────────────────────────────────────

    /**
     * Whether the treatment plan succeeded from the user's perspective.
     * Set by the user when they complete the last event of the apply.
     * {@code null} while the plan is still in progress.
     */
    Boolean success;
}
