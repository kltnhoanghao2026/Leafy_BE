package com.leafy.plantmanagementservice.model.enums;

public enum TreatmentStatus {

    /** Newly created by RAG service — events not yet confirmed or applied. */
    PENDING,

    /** At least one event in the schedule is in progress. */
    ACTIVE,

    /** HEALTH_RECOVERY event has occurred — plant declared recovered. */
    COMPLETED,

    /** Plan discarded by the user before completion. */
    CANCELLED
}
