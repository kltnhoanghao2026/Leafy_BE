package com.leafy.iottestdataservice.client;

import com.leafy.iottestdataservice.client.dto.FarmPlotResponse;
import com.leafy.iottestdataservice.client.dto.FarmZoneResponse;
import java.util.List;

public interface FarmSeedClient {

    List<FarmPlotResponse> getFarmPlots(String ownerProfileId);

    List<FarmZoneResponse> getFarmZones(String farmPlotId);
}
