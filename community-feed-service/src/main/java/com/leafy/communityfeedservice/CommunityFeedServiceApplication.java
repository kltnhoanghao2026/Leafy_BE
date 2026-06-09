package com.leafy.communityfeedservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.leafy.communityfeedservice.client")
@ComponentScan(basePackages = {"com.leafy.communityfeedservice", "com.leafy.common"})
@EnableMongoRepositories(basePackages = {"com.leafy.communityfeedservice.repository", "com.leafy.common.repository"})
@EnableMongoAuditing
public class CommunityFeedServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunityFeedServiceApplication.class, args);
    }

}
