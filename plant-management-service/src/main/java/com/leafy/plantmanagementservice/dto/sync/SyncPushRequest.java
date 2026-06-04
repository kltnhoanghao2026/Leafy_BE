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
public class SyncPushRequest {
    /** Client device id (optional but recommended for observability). */
    String deviceId;

    /** Monotonic client timestamp/nonce (optional). */
    String clientSyncId;

    /** Mutations to apply on server. */
    List<SyncMutation> mutations;

    /** Already known localId -> serverId mappings on client. */
    List<SyncIdMapping> knownIdMappings;
}
