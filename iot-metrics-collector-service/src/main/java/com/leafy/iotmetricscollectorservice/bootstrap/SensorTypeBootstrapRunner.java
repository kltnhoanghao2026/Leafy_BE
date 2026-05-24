package com.leafy.iotmetricscollectorservice.bootstrap;

import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.repository.SensorTypeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.bootstrap",
    name = "seed-sensor-types",
    havingValue = "true",
    matchIfMissing = true
)
public class SensorTypeBootstrapRunner implements ApplicationRunner {

    private static final List<RequiredSensorType> REQUIRED_SENSOR_TYPES = List.of(
        new RequiredSensorType(
            "AIR_TEMP",
            "Air Temperature",
            "°C",
            -20.0,
            80.0,
            "Air temperature measured by DHT11 sensor."
        ),
        new RequiredSensorType(
            "AIR_HUMIDITY",
            "Air Humidity",
            "%",
            0.0,
            100.0,
            "Relative air humidity measured by DHT11 sensor."
        ),
        new RequiredSensorType(
            "SOIL_MOISTURE",
            "Soil Moisture",
            "%",
            0.0,
            100.0,
            "Normalized soil moisture percentage from soil moisture sensor."
        ),
        new RequiredSensorType(
            "LIGHT_INTENSITY",
            "Light Intensity",
            "scale",
            0.0,
            1000.0,
            "Normalized light intensity scale from LDR sensor. This is not lux."
        )
    );

    private final SensorTypeRepository sensorTypeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Ensuring required IoT sensor types exist...");

        int created = 0;
        int existing = 0;

        for (RequiredSensorType seed : REQUIRED_SENSOR_TYPES) {
            if (seedIfMissing(seed)) {
                created++;
            } else {
                existing++;
            }
        }

        log.info("Required IoT sensor type bootstrap completed. created={}, existing={}", created, existing);
    }

    private boolean seedIfMissing(RequiredSensorType seed) {
        if (sensorTypeRepository.findByCode(seed.code()).isPresent()) {
            log.info("Required sensor type already exists: code={}", seed.code());
            return false;
        }

        SensorType sensorType = new SensorType();
        sensorType.setCode(seed.code());
        sensorType.setName(seed.name());
        sensorType.setUnit(seed.unit());
        sensorType.setMinDefault(seed.minDefault());
        sensorType.setMaxDefault(seed.maxDefault());
        sensorType.setDescription(seed.description());

        sensorTypeRepository.save(sensorType);
        log.info("Seeded required sensor type: code={}", seed.code());
        return true;
    }

    private record RequiredSensorType(
        String code,
        String name,
        String unit,
        Double minDefault,
        Double maxDefault,
        String description
    ) {}
}
