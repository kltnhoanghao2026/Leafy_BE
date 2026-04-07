package com.leafy.plantmanagementservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "plant-management.seeder")
public class SeederProperties {

    private int speciesCount = 15;
    private int plantCount = 30;
    private int eventsPerPlant = 5;
    private long randomSeed = 20260324L;
}
