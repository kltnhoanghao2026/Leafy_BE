package com.leafy.plantmanagementservice.dto.sync;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SyncIdMapping {
    String localId;
    String serverId;
    String entityType;
}
