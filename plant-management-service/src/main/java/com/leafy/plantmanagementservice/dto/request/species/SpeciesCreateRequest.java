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

    @NotBlank(message = "Common name is required")
    String commonName;

    String cultivarName;

    @NotNull(message = "Water frequency days is required")
    @PositiveOrZero(message = "Water frequency days must be positive or zero")
    Integer waterFrequencyDays;

    String lightRequirements;

    @PositiveOrZero(message = "Days to maturity must be positive or zero")
    Integer daysToMaturity;

    String plantingWindow;

    String plantingSeason;

    Map<String, Object> idealEnv;

    @PositiveOrZero(message = "Spacing must be positive or zero")
    Double spacing;

    @PositiveOrZero(message = "Expected yield must be positive or zero")
    Double expectedYieldKg;

    List<String> commonDiseaseIds;
}
