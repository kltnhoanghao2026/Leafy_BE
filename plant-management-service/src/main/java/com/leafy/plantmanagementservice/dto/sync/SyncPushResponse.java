package com.leafy.plantmanagementservice.dto.sync;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SyncPushResponse {
    /** New mappings produced while applying CREATE mutations (UUID -> ObjectId). */
    List<SyncIdMapping> idMappings;

    /** Server time to use for subsequent pull cursor. */
    String serverTime;

    /** Per-mutation results (optional for debugging). */
    List<SyncMutationResult> results;
}
