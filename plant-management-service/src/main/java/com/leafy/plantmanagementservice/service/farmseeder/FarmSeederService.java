package com.leafy.plantmanagementservice.service.farmseeder;

import com.leafy.plantmanagementservice.dto.response.seeder.FarmSeederResponse;

public interface FarmSeederService {

    FarmSeederResponse reseed(Integer plotsPerProfile, Integer zonesPerPlot);
}
