package com.leafy.iottestdataservice.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "seed")
public class SeedProperties {

    private final Collector collector = new Collector();
    private final Mqtt mqtt = new Mqtt();
    private final Scenario scenario = new Scenario();
    private final Environment environment = new Environment();
    private final Defaults defaults = new Defaults();

    @Getter
    @Setter
    public static class Collector {
        private String baseUrl = "http://localhost:8080";
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

    @Getter
    @Setter
    public static class Defaults {
        private List<UUID> userIds = defaultIds("seed-user-", 3);
        private List<UUID> farmPlotIds = defaultIds("seed-plot-", 3);
        private List<UUID> zoneIds = defaultIds("seed-zone-", 8);

        private static List<UUID> defaultIds(String prefix, int count) {
            List<UUID> values = new ArrayList<>(count);
            for (int index = 1; index <= count; index++) {
                values.add(UUID.nameUUIDFromBytes((prefix + index).getBytes(StandardCharsets.UTF_8)));
            }
            return values;
        }
    }
}
