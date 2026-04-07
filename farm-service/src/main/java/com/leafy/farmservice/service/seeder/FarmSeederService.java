package com.leafy.farmservice.service.seeder;

import com.leafy.farmservice.dto.response.seeder.FarmSeederResponse;

public interface FarmSeederService {

    FarmSeederResponse reseed(Integer plotsPerProfile, Integer zonesPerPlot);
}
