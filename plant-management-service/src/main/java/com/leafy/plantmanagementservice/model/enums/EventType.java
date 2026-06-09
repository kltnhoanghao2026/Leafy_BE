package com.leafy.plantmanagementservice.model.enums;

public enum EventType {
    // --- Routine Care ---
    IRRIGATION, // Drip, sprinkler, or manual watering (measured)
    NUTRITION, // NPK, organic compost, foliar spray, lime
    WEED_CONTROL, // Manual weeding or herbicide application
    PRUNING, // Structural (stumping) or sanitary (dead branch removal)

    // --- Health & Medical ---
    SCOUTING, // Routine field check (may find nothing)
    DISEASE_DETECTED, // Visual confirmation of a symptom (Rust, Berry Borer, etc.),
    TREATMENT_APPLICATION, // Curative action: spray fungicide, release beneficial insects
    QUARANTINE, // Isolate plant (nursery / pot) to prevent spread
    HEALTH_RECOVERY, // End of treatment cycle — plant declared recovered

    // --- Growth & Lifecycle ---
    PHENOLOGY, // Growth stage notes: Flowering, Pinhead, Fruit Filling, Ripening
    REPOT, // Move from nursery bag to larger pot or field
    HARVEST, // Cherry picking — used for totalYieldKg tracking

    // --- Automation & Alerts ---
    ALERT_TRIGGERED // Automated sensor threshold crossed: soil moisture, temperature, light, etc.
}
