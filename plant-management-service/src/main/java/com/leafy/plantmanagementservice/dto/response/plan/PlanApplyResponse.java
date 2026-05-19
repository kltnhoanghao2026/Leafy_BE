package com.leafy.plantmanagementservice.dto.response.plan;

import com.leafy.plantmanagementservice.model.enums.PlanStatus;
import com.leafy.plantmanagementservice.model.enums.TrackingGranularity;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlanApplyResponse {

    String id;

    // ── Link to parent Plan ──────────────────────────────────────────────────
    String planId;
    String planName;
    String diseaseName;

    // ── Who applied ──────────────────────────────────────────────────────────
    String appliedById;

    // ── Target scope ─────────────────────────────────────────────────────────
    String plantId;
    String farmPlotId;
    String farmZoneId;
    String targetName;

    // ── Application parameters ───────────────────────────────────────────────
    LocalDate startDate;
    TrackingGranularity trackingGranularity;

    // ── Generated events ─────────────────────────────────────────────────────
    List<String> plantEventIds;

    // ── Lifecycle ────────────────────────────────────────────────────────────
    PlanStatus status;

    /** Whether this application can be cancelled by the user. */
    Boolean canCancel;

    // ── Audit fields ─────────────────────────────────────────────────────────
    LocalDateTime createdAt;
    LocalDateTime lastModifiedAt;
}
