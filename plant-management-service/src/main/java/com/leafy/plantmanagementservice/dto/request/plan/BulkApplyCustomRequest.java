package com.leafy.plantmanagementservice.dto.request.plan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Wrapper for POST /plans/applies/bulk-custom.
 * Each item contains its own planId + schedule configuration.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BulkApplyCustomRequest {

    @NotEmpty(message = "items must not be empty")
    @Valid
    List<PlanApplyItemRequest> items;
}
