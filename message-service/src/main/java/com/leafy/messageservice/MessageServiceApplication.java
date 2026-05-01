package com.leafy.messageservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = {"com.leafy.common", "com.leafy.messageservice"})
@EnableMongoRepositories(basePackages = {"com.leafy.messageservice.repository", "com.leafy.common.repository"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.leafy.common.dto.client", "com.leafy.messageservice.client"})
@EnableMongoAuditing
public class MessageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageServiceApplication.class, args);
    }

}
