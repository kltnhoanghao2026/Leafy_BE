package com.leafy.plantmanagementservice.dto.response.plant;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BulkOperationResult {

    int successCount;
    int failedCount;
    List<String> failedIds;
}
