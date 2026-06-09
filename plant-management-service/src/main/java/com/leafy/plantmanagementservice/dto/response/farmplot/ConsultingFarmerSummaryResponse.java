package com.leafy.plantmanagementservice.dto.response.farmplot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConsultingFarmerSummaryResponse {
    private long plotCount;
    private long zoneCount;
    private long plantCount;
}
