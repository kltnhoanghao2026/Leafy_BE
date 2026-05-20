package com.leafy.iottestdataservice.config;

import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "seed")
public class SeedProperties {

    private final Collector collector = new Collector();
    private final Profile profile = new Profile();
    private final Farm farm = new Farm();
    private final AuthHeaders authHeaders = new AuthHeaders();
    private final Mqtt mqtt = new Mqtt();
    private final Scenario scenario = new Scenario();
    private final Environment environment = new Environment();

    @Getter
    @Setter
    public static class Collector {
        private String baseUrl = "http://localhost:8080";
    }

    @Getter
    @Setter
    public static class Profile {
        private String baseUrl = "http://localhost:8087";
        private int pageSize = 50;
    }

    @Getter
    @Setter
    public static class Farm {
        private String baseUrl = "http://localhost:8086";
    }

    @Getter
    @Setter
    public static class AuthHeaders {
        private String userId = "iot-test-data-service";
        private String email = "iot-test-data-service@leafy.local";
        private String roles = "ADMIN";
    }

    @Getter
    @Setter
    public static class Mqtt {
        private String url = "tcp://localhost:1883";
        private String username;
        private String password;
        private String product = "coffee";
        private String namespaceEnv = "local";
        private int qos = 1;
        private Duration completionTimeout = Duration.ofSeconds(5);
    }

    @Getter
    @Setter
    public static class Scenario {
        private int defaultReadingsPerHour = 4;
        private int telemetryIntervalSeconds = 60;
        private int statusIntervalSeconds = 30;
        private boolean simulationEnabled = true;
        private boolean anomaliesEnabled = true;
        private int anomalyEveryCycles = 8;
    }

    @Getter
    @Setter
    public static class Environment {
        private List<String> allowedProfiles = List.of("local", "dev", "staging");
    }
}
