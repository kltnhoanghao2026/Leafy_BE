package com.leafy.iotmetricscollectorservice.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.repository.SensorTypeRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensorTypeBootstrapRunnerTest {

    private static final Set<String> REQUIRED_CODES = Set.of(
        "AIR_TEMP",
        "AIR_HUMIDITY",
        "SOIL_MOISTURE",
        "LIGHT_INTENSITY"
    );

    @Mock
    private SensorTypeRepository sensorTypeRepository;

    private Map<String, SensorType> sensorTypes;
    private SensorTypeBootstrapRunner runner;

    @BeforeEach
    void setUp() {
        sensorTypes = new LinkedHashMap<>();
        runner = new SensorTypeBootstrapRunner(sensorTypeRepository);

        when(sensorTypeRepository.findByCode(any(String.class)))
            .thenAnswer(invocation -> Optional.ofNullable(sensorTypes.get(invocation.getArgument(0))));
        lenient().when(sensorTypeRepository.save(any(SensorType.class)))
            .thenAnswer(invocation -> {
                SensorType sensorType = invocation.getArgument(0);
                sensorTypes.put(sensorType.getCode(), sensorType);
                return sensorType;
            });
    }

    @Test
    void run_seedsMissingSensorTypes() throws Exception {
        runner.run(null);

        assertEquals(REQUIRED_CODES, sensorTypes.keySet());
        assertSensorType("AIR_TEMP", "Air Temperature", "°C", -20.0, 80.0,
            "Air temperature measured by DHT11 sensor.");
        assertSensorType("AIR_HUMIDITY", "Air Humidity", "%", 0.0, 100.0,
            "Relative air humidity measured by DHT11 sensor.");
        assertSensorType("SOIL_MOISTURE", "Soil Moisture", "%", 0.0, 100.0,
            "Normalized soil moisture percentage from soil moisture sensor.");
        assertSensorType("LIGHT_INTENSITY", "Light Intensity", "scale", 0.0, 1000.0,
            "Normalized light intensity scale from LDR sensor. This is not lux.");
    }

    @Test
    void run_doesNotOverwriteExistingSensorType() throws Exception {
        SensorType existing = new SensorType();
        existing.setCode("AIR_TEMP");
        existing.setName("Custom Temp");
        existing.setUnit("custom");
        existing.setMinDefault(1.0);
        existing.setMaxDefault(2.0);
        existing.setDescription("Custom description");
        sensorTypes.put(existing.getCode(), existing);

        runner.run(null);

        SensorType airTemp = sensorTypes.get("AIR_TEMP");
        assertEquals("Custom Temp", airTemp.getName());
        assertEquals("custom", airTemp.getUnit());
        assertEquals(1.0, airTemp.getMinDefault());
        assertEquals(2.0, airTemp.getMaxDefault());
        assertEquals("Custom description", airTemp.getDescription());
    }

    @Test
    void run_isIdempotentAcrossMultipleRuns() throws Exception {
        runner.run(null);
        runner.run(null);

        assertEquals(4, sensorTypes.size());
        assertTrue(sensorTypes.keySet().containsAll(REQUIRED_CODES));
    }

    @Test
    void run_skipsSaveWhenAllRequiredSensorTypesExist() throws Exception {
        REQUIRED_CODES.forEach(code -> {
            SensorType sensorType = new SensorType();
            sensorType.setCode(code);
            sensorType.setName("Existing " + code);
            sensorTypes.put(code, sensorType);
        });

        runner.run(null);

        verify(sensorTypeRepository, never()).save(any(SensorType.class));
    }

    private void assertSensorType(
        String code,
        String name,
        String unit,
        Double minDefault,
        Double maxDefault,
        String description
    ) {
        SensorType sensorType = sensorTypes.get(code);
        assertEquals(name, sensorType.getName());
        assertEquals(unit, sensorType.getUnit());
        assertEquals(minDefault, sensorType.getMinDefault());
        assertEquals(maxDefault, sensorType.getMaxDefault());
        assertEquals(description, sensorType.getDescription());
    }
}
