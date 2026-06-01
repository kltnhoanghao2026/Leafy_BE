package com.leafy.plantmanagementservice.dto.sync;

import com.leafy.plantmanagementservice.dto.response.farmplot.FarmPlotResponse;
import com.leafy.plantmanagementservice.dto.response.farmzone.FarmZoneResponse;
import com.leafy.plantmanagementservice.dto.response.plant.PlantResponse;
import com.leafy.plantmanagementservice.dto.response.plantevent.PlantEventResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SyncPullResponse {
    /** New cursor to store on device */
    String serverTime;

    List<FarmPlotResponse> farmPlots;
    List<FarmZoneResponse> farmZones;
    List<PlantResponse> plants;
    List<PlantEventResponse> plantEvents;
}
