package com.leafy.plantmanagementservice.dto.response.stats;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

/**
 * Lightweight summary of a recent plant event for the dashboard activity timeline.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecentEventSummary {

    String id;
    String eventType;
    String note;
    String targetType;
    boolean completed;
    LocalDate calculatedStartDate;
    String createdAt;
}
