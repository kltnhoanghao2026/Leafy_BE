package com.leafy.plantmanagementservice.dto.request.plan;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CompleteApplyRequest {

    @NotNull(message = "success is required")
    Boolean success;
}
