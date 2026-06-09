package com.leafy.profileservice.dto.response.sync;

import com.leafy.profileservice.model.enums.SyncTaskStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SyncStartResponse {

    String taskId;

    SyncTaskStatus status;
}
