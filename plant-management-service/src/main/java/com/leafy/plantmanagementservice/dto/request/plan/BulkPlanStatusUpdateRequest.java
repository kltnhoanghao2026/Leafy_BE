package com.leafy.plantmanagementservice.dto.request.plan;

import com.leafy.plantmanagementservice.model.enums.PlanStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BulkPlanStatusUpdateRequest {

    @NotEmpty(message = "Plan IDs must not be empty")
    List<String> planIds;

    @NotNull(message = "Status is required")
    PlanStatus status;
}
