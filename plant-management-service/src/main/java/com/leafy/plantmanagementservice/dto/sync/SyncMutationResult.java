package com.leafy.plantmanagementservice.dto.sync;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SyncMutationResult {
    String mutationId;
    boolean applied;
    String errorCode;
    String errorMessage;

    /** If applied and server created a new recordId */
    String newServerId;
}
