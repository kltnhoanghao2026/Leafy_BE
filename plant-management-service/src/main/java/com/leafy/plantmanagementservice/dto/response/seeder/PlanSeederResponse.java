package com.leafy.plantmanagementservice.dto.response.seeder;

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
public class PlanSeederResponse {
    long deletedPlanCount;
    int seededPlanCount;
    int sourceEventCount;
}
