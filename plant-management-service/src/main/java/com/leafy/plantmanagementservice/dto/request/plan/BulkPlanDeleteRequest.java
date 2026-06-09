package com.leafy.plantmanagementservice.dto.request.plan;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BulkPlanDeleteRequest {

    @NotEmpty(message = "Plan IDs must not be empty")
    List<String> planIds;
}
