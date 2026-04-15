package com.leafy.iottestdataservice.service;

import com.leafy.iottestdataservice.dto.HistorySeedRequest;
import com.leafy.iottestdataservice.dto.HistorySeedResponse;

public interface HistoricalTelemetrySeedService {

    HistorySeedResponse seedLast7Days(HistorySeedRequest request);

    HistorySeedResponse seedLast30Days(HistorySeedRequest request);
}
