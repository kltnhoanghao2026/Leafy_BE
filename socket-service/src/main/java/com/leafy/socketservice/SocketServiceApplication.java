package com.leafy.socketservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.leafy.socketservice", "com.leafy.common"})
public class SocketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocketServiceApplication.class, args);
    }

}
