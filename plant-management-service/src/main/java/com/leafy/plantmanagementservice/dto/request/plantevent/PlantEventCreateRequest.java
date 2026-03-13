package com.leafy.plantmanagementservice.dto.request.plantevent;

import com.leafy.plantmanagementservice.model.enums.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlantEventCreateRequest {

    @NotBlank(message = "{validation.plantId.required}")
    String plantId;

    @NotNull(message = "{validation.event.type.required}")
    EventType eventType;

    @NotBlank(message = "{validation.event.note.required}")
    String note;

    String description;

    @PositiveOrZero(message = "{validation.event.daysFromNow.positiveOrZero}")
    Integer daysFromNow;

    @PositiveOrZero(message = "{validation.event.durationDays.positiveOrZero}")
    Integer durationDays;

    boolean isPlanned;

    LocalDate calculatedStartDate;
    LocalDate calculatedEndDate;

    // Chemical safety fields (required only when eventType = TREATMENT_APPLICATION)
    Integer phiDays;
    String ppeRequired;
    String mrlNote;
    String estimatedCost;

    /**
     * Optional: link to the RAG-generated TreatmentPlan that produced this event.
     */
    String sourcePlanId;
}
