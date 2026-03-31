package com.leafy.farmservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.leafy.farmservice", "com.leafy.common"})
@EnableMongoRepositories(basePackages = {"com.leafy.farmservice.repository", "com.leafy.common.repository"})
@EnableMongoAuditing
@EnableFeignClients
public class FarmServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FarmServiceApplication.class, args);
    }

}
