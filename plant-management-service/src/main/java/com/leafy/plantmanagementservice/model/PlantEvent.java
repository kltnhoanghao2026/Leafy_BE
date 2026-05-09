package com.leafy.plantmanagementservice.model;

import com.leafy.common.model.BaseModel;
import com.leafy.plantmanagementservice.model.enums.EventType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "plant_events")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlantEvent extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    // ── Relationships ────────────────────────────────────────────────────────
    @Indexed
    String plantId;

    @Indexed
    String farmPlotId;

    @Indexed
    String farmZoneId;

    // ── Core Event Fields ────────────────────────────────────────────────────
    EventType eventType;

    /** Short display label shown on the calendar tile. */
    String note;

    /** Full description including dosage, PPE, method, etc. */
    String description;

    /** Days from creation date (used by RAG planner; stored for reference). */
    Integer daysFromNow;

    /** Duration of the event window in days. */
    Integer durationDays;

    /** false = immediate / detected today; true = scheduled for the future. */
    boolean planned;

    // ── Calculated Dates (from planner) ──────────────────────────────────────
    LocalDate calculatedStartDate;
    LocalDate calculatedEndDate;

    // ── Chemical Application Safety Fields ───────────────────────────────────
    /**
     * Pre-Harvest Interval in days. Non-null only for TREATMENT_APPLICATION events.
     */
    Integer phiDays;

    /** Full PPE list required. Non-null only for TREATMENT_APPLICATION events. */
    String ppeRequired;

    /** MRL compliance note. Non-null only for TREATMENT_APPLICATION events. */
    String mrlNote;

    /** Estimated cost as a free-text string (e.g. "$10-$20" or "500,000 VND"). */
    String estimatedCost;

    // ── Source Tracking ───────────────────────────────────────────────────────
    /** ID of the Plan (MongoDB) that generated this event, if any. */
    String sourcePlanId;
}
