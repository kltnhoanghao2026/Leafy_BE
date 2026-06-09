package com.leafy.fileservice.config;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
public class S3Config {

    @Value("${aws.region}")
    String region;

    @Value("${aws.s3.bucket}")
    String bucketName;

    @Bean
    public S3AsyncClient s3AsyncClient(){
        return S3AsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder()
                        .build())
                .build();
    }

}
