package com.leafy.iotmetricscollectorservice.integration.plant.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InternalAlertPlantEventResponse {
    boolean created;
    String sourceType;
    String sourceId;
    PlantEventSummary plantEvent;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class PlantEventSummary {
        String id;
        String plantId;
        String farmPlotId;
        String farmZoneId;
        String eventType;
        String targetType;
        String note;
        String sourceType;
        String sourceId;
    }
}
