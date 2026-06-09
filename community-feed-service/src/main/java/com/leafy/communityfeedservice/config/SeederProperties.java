package com.leafy.communityfeedservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "community-feed.seeder")
public class SeederProperties {

    private int postCount = 100;
    private int commentCount = 400;
    private int voteCount = 700;
    private int profilePageSize = 200;
    private int profileMaxPages = 3;
    private int planPageSize = 100;
    private int planMaxPages = 5;
    private long randomSeed = 20260324L;
}

