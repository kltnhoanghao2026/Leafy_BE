package com.leafy.plantmanagementservice.dto.request.plan;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlanApplyRequest {
    
    @NotNull(message = "Start date is required")
    LocalDate startDate;

    String plantId;
    
    String farmPlotId;
    
    String farmZoneId;
}
