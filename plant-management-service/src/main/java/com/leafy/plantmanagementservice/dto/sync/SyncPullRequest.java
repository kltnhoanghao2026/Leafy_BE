package com.leafy.plantmanagementservice.dto.sync;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SyncPullRequest {
    /** ISO timestamp cursor. Server returns changes after this moment (exclusive). */
    String since;

    /** Device id for observability */
    String deviceId;
}
