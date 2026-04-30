package com.leafy.iottestdataservice.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NonProdEnvironmentGuard implements ApplicationRunner {

    private final Environment environment;
    private final SeedProperties seedProperties;

    @Override
    public void run(ApplicationArguments args) {
        validateProfiles(List.of(environment.getActiveProfiles()));
    }

    void validateProfiles(List<String> activeProfiles) {
        if (activeProfiles.contains("prod")) {
            throw new IllegalStateException("iot-test-data-service must not run with the prod profile");
        }

        List<String> allowedProfiles = seedProperties.getEnvironment().getAllowedProfiles();
        boolean allowed = activeProfiles.isEmpty()
            || activeProfiles.stream().anyMatch(allowedProfiles::contains);

        if (!allowed) {
            throw new IllegalStateException(
                "iot-test-data-service is enabled only for profiles: " + String.join(", ", allowedProfiles)
            );
        }
    }
}
