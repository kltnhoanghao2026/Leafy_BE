package com.leafy.plantmanagementservice.config;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "farm.seeder")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FarmSeederProperties {

    int plotsPerProfile = 2;
    int zonesPerPlot = 3;
    long randomSeed = 20260324L;
    int profilePageSize = 200;
    int profileMaxPages = 3;
}
