package com.leafy.iotmetricscollectorservice.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for camera schedule time calculations.
 */
@Configuration
public class CameraScheduleConfig {

    /**
     * Shared clock bean for deterministic schedule calculations and tests.
     */
    @Bean
    public Clock cameraScheduleClock() {
        return Clock.systemUTC();
    }
}
