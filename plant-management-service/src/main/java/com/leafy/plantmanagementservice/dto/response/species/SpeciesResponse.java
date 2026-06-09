package com.leafy.plantmanagementservice.dto.response.species;

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
public class SpeciesResponse {
    String id;
    String commonName;
    String cultivarName;
    Integer waterFrequencyDays;
    String lightRequirements;
    Integer daysToMaturity;
    String plantingWindow;
    String plantingSeason;
    Map<String, Object> idealEnv;
    Double spacing;
    Double expectedYieldKg;
    List<String> commonDiseaseIds;
}
