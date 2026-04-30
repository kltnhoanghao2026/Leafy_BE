package com.leafy.iottestdataservice.scenario;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TelemetryScenarioGenerator {

    public Map<String, Double> baselineMetrics(String deviceUid, Instant timestamp) {
        double deviceOffset = Math.abs(deviceUid.hashCode() % 1000) / 1000.0;
        double hour = timestamp.atZone(ZoneOffset.UTC).getHour();
        double dayWave = Math.sin((hour / 24d) * (Math.PI * 2d));
        double lightWave = Math.max(0d, Math.sin(((hour - 6d) / 12d) * Math.PI));
        double temp = 27d + (5d * dayWave) + (deviceOffset * 1.5d);
        double humidity = 72d - ((temp - 27d) * 1.5d) + (deviceOffset * 4d);
        double soilTrend = 58d - ((timestamp.getEpochSecond() / 3600d) % 24d) * 0.4d + (deviceOffset * 2d);
        double light = lightWave * 950d;

        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("AIR_TEMP", round(temp));
        metrics.put("AIR_HUMIDITY", round(clamp(humidity, 40d, 95d)));
        metrics.put("SOIL_MOISTURE", round(clamp(soilTrend, 20d, 80d)));
        metrics.put("LIGHT_INTENSITY", round(light));
        return metrics;
    }

    public Map<String, Double> highTemperature(String deviceUid, Instant timestamp, Double targetValue) {
        Map<String, Double> metrics = baselineMetrics(deviceUid, timestamp);
        metrics.put("AIR_TEMP", round(targetValue != null ? targetValue : 44d));
        metrics.put("AIR_HUMIDITY", round(clamp(metrics.get("AIR_HUMIDITY") - 12d, 20d, 95d)));
        return metrics;
    }

    public Map<String, Double> lowSoilMoisture(String deviceUid, Instant timestamp, Double targetValue) {
        Map<String, Double> metrics = baselineMetrics(deviceUid, timestamp);
        metrics.put("SOIL_MOISTURE", round(targetValue != null ? targetValue : 18d));
        return metrics;
    }

    public Map<String, Double> highHumidity(String deviceUid, Instant timestamp, Double targetValue) {
        Map<String, Double> metrics = baselineMetrics(deviceUid, timestamp);
        metrics.put("AIR_HUMIDITY", round(targetValue != null ? targetValue : 92d));
        return metrics;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
