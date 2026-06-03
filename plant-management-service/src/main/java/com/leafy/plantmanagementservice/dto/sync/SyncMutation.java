package com.leafy.plantmanagementservice.dto.sync;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SyncMutation {
    String id;

    /** e.g. plants, plant_events, farm_plots, farm_zones */
    String tableName;

    /** client recordId (may be a UUID for newly created docs) */
    String recordId;

    /** CREATE | UPDATE | DELETE */
    String operation;

    /** JSON string payload (client-provided) */
    String payload;

    /** ISO timestamp from client */
    String createdAt;
}
