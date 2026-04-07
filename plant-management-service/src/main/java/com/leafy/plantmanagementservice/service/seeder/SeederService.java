package com.leafy.plantmanagementservice.service.seeder;

import com.leafy.plantmanagementservice.dto.response.seeder.PlantSeederResponse;

public interface SeederService {

    PlantSeederResponse reseed(Integer speciesCount, Integer plantCount, Integer eventsPerPlant);
}
