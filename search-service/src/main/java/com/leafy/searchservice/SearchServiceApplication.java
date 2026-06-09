package com.leafy.searchservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@ComponentScan(basePackages = {"com.leafy.searchservice", "com.leafy.common"})
@EnableFeignClients
@SpringBootApplication
@EnableMongoRepositories(basePackages = {"com.leafy.searchservice.repository", "com.leafy.common.repository"})
@EnableMongoAuditing
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }

}
