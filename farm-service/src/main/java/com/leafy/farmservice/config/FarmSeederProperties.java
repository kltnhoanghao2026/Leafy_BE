package com.leafy.farmservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "farm.seeder")
public class FarmSeederProperties {

    private int plotsPerProfile = 2;
    private int zonesPerPlot = 3;
    private long randomSeed = 20260324L;
    private int profilePageSize = 200;
    private int profileMaxPages = 3;
}
