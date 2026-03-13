package com.leafy.plantmanagementservice.dto.request.species;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SpeciesCreateRequest {

    @NotBlank(message = "{validation.species.commonName.required}")
    String commonName;

    String cultivarName;

    @NotNull(message = "{validation.species.waterFrequencyDays.required}")
    @PositiveOrZero(message = "{validation.species.waterFrequencyDays.positiveOrZero}")
    Integer waterFrequencyDays;

    String lightRequirements;

    @PositiveOrZero(message = "{validation.species.daysToMaturity.positiveOrZero}")
    Integer daysToMaturity;

    String plantingWindow;

    String plantingSeason;

    Map<String, Object> idealEnv;

    @PositiveOrZero(message = "{validation.species.spacing.positiveOrZero}")
    Double spacing;

    @PositiveOrZero(message = "{validation.species.expectedYield.positiveOrZero}")
    Double expectedYieldKg;

    List<String> commonDiseaseIds;
}
