package com.leafy.plantmanagementservice.dto.request.farmplot;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class BulkSummaryRequest {
    private List<String> farmerProfileIds;
}
