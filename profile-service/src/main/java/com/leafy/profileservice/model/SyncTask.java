package com.leafy.profileservice.model;

import com.leafy.profileservice.model.enums.SyncTaskStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document("sync_task")
public class SyncTask {

    @Id
    String taskId;

    long totalCount;

    long processedCount;

    String lastPosition;

    SyncTaskStatus status;

    String errorMessage;

    LocalDateTime startedAt;

    LocalDateTime updatedAt;

    LocalDateTime completedAt;
}
