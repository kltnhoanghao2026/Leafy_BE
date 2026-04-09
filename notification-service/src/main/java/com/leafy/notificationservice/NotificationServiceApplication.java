package com.leafy.notificationservice;

import com.leafy.common.repository.OutboxEventRepository;
import com.leafy.notificationservice.repository.NotificationLogRepository;
import com.leafy.notificationservice.repository.PushTokenRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.leafy.notificationservice", "com.leafy.common"})
@EnableFeignClients
@EnableMongoRepositories(basePackageClasses = {
        OutboxEventRepository.class,
        PushTokenRepository.class,
        NotificationLogRepository.class
})
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

}
