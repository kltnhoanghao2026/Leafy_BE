package com.leafy.iotmetricscollectorservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IotMetricsCollectorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotMetricsCollectorServiceApplication.class, args);
    }

}
