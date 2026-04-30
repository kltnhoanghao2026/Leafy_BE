#include "app/sensor_manager.h"

#include <math.h>

#include "utils/logger.h"

#ifndef LEAFY_DHT_PIN
#define LEAFY_DHT_PIN 4
#endif

#ifndef LEAFY_SOIL_ADC_PIN
#define LEAFY_SOIL_ADC_PIN 34
#endif

#ifndef LEAFY_SOIL_POWER_PIN
#define LEAFY_SOIL_POWER_PIN -1
#endif

#ifndef LEAFY_LDR_ADC_PIN
#define LEAFY_LDR_ADC_PIN 35
#endif

#ifndef LEAFY_ANALOG_MAX
#define LEAFY_ANALOG_MAX 4095
#endif

#ifndef LEAFY_CALIBRATION_LOGGING
#define LEAFY_CALIBRATION_LOGGING 0
#endif

#ifndef LEAFY_CALIBRATION_LOG_INTERVAL_SEC
#define LEAFY_CALIBRATION_LOG_INTERVAL_SEC 30
#endif

namespace leafy {

SensorManager::SensorManager() : _dht(LEAFY_DHT_PIN, DHT11) {}

bool SensorManager::begin(const CalibrationConfig& calibration) {
  _calibration = calibration;
  Logger::info("Initializing sensors: DHT11 pin=" + String(LEAFY_DHT_PIN) +
               ", soilAdcPin=" + String(LEAFY_SOIL_ADC_PIN) +
               ", soilPowerPin=" + String(LEAFY_SOIL_POWER_PIN) +
               ", ldrAdcPin=" + String(LEAFY_LDR_ADC_PIN));
  Logger::info("Sensor calibration active: soilDryRaw=" + String(_calibration.soilDryRaw) +
               ", soilWetRaw=" + String(_calibration.soilWetRaw) +
               ", lightDarkRaw=" + String(_calibration.lightDarkRaw) +
               ", lightBrightRaw=" + String(_calibration.lightBrightRaw));

  _dht.begin();

#if defined(ARDUINO_ARCH_ESP32)
  analogReadResolution(12);
  analogSetPinAttenuation(LEAFY_SOIL_ADC_PIN, ADC_11db);
  analogSetPinAttenuation(LEAFY_LDR_ADC_PIN, ADC_11db);
#endif

  pinMode(LEAFY_SOIL_ADC_PIN, INPUT);
  pinMode(LEAFY_LDR_ADC_PIN, INPUT);

#if LEAFY_SOIL_POWER_PIN >= 0
  pinMode(LEAFY_SOIL_POWER_PIN, OUTPUT);
  digitalWrite(LEAFY_SOIL_POWER_PIN, LOW);
#else
  Logger::warn("YL-69 power control pin is disabled; continuous sensor power increases corrosion risk");
#endif

  _begun = true;
  return true;
}

void SensorManager::updateCalibration(const CalibrationConfig& calibration) {
  _calibration = calibration;
  Logger::info("Sensor calibration active: soilDryRaw=" + String(_calibration.soilDryRaw) +
               ", soilWetRaw=" + String(_calibration.soilWetRaw) +
               ", lightDarkRaw=" + String(_calibration.lightDarkRaw) +
               ", lightBrightRaw=" + String(_calibration.lightBrightRaw));
}

SensorSnapshot SensorManager::readSnapshot() {
  if (!_begun) {
    Logger::warn("SensorManager read requested before begin()");
  }

  SensorSnapshot snapshot;
  snapshot.sampledAtMs = millis();

  double temperatureC = 0.0;
  double humidityPercent = 0.0;
  if (readDht(temperatureC, humidityPercent)) {
    snapshot.hasAirTemp = true;
    snapshot.airTemp = roundOneDecimal(temperatureC);
    snapshot.hasAirHumidity = true;
    snapshot.airHumidity = roundOneDecimal(humidityPercent);
  }

  double soilMoisture = 0.0;
  int soilRaw = 0;
  if (readSoilMoisture(soilMoisture, &soilRaw)) {
    snapshot.hasSoilMoisture = true;
    snapshot.soilMoisture = roundOneDecimal(soilMoisture);
    snapshot.hasSoilRaw = true;
    snapshot.soilRaw = soilRaw;
  }

  double lightIntensity = 0.0;
  int lightRaw = 0;
  if (readLightIntensity(lightIntensity, &lightRaw)) {
    snapshot.hasLightIntensity = true;
    snapshot.lightIntensity = roundOneDecimal(lightIntensity);
    snapshot.hasLightRaw = true;
    snapshot.lightRaw = lightRaw;
  }

  maybeLogCalibrationSnapshot(snapshot);
  return snapshot;
}

CalibrationRawReadings SensorManager::readCalibrationRaw() {
  CalibrationRawReadings readings;
  readings.sampledAtMs = millis();

  int soilRaw = readSoilRaw();
  if (soilRaw >= 0) {
    readings.hasSoilRaw = true;
    readings.soilRaw = soilRaw;
  }

  int lightRaw = readLightRaw();
  if (lightRaw >= 0) {
    readings.hasLightRaw = true;
    readings.lightRaw = lightRaw;
  }

  return readings;
}

std::vector<SensorReading> SensorManager::readAll() {
  SensorSnapshot snapshot = readSnapshot();

  std::vector<SensorReading> readings;
  readings.reserve(4);
  readings.push_back(makeReading("AIR_TEMP", snapshot.airTemp, snapshot.hasAirTemp));
  readings.push_back(makeReading("AIR_HUMIDITY", snapshot.airHumidity, snapshot.hasAirHumidity));
  readings.push_back(makeReading("SOIL_MOISTURE", snapshot.soilMoisture, snapshot.hasSoilMoisture));
  readings.push_back(makeReading("LIGHT_INTENSITY", snapshot.lightIntensity, snapshot.hasLightIntensity));
  return readings;
}

bool SensorManager::readDht(double& temperatureC, double& humidityPercent) {
  float humidity = _dht.readHumidity();
  float temperature = _dht.readTemperature();

  if (isnan(humidity) || isnan(temperature)) {
    Logger::warn("DHT11 read failed; omitting AIR_TEMP and AIR_HUMIDITY for this sample");
    return false;
  }

  if (humidity < 0.0f || humidity > 100.0f || temperature < -20.0f || temperature > 80.0f) {
    Logger::warn("DHT11 read out of expected range; temp=" + String(temperature) +
                 ", humidity=" + String(humidity));
    return false;
  }

  temperatureC = temperature;
  humidityPercent = humidity;
  return true;
}

bool SensorManager::readSoilMoisture(double& moisturePercent, int* rawOut) {
  int raw = readSoilRaw();
  if (rawOut != nullptr) {
    *rawOut = raw;
  }

  if (raw < 0) {
    Logger::warn("YL-69 soil moisture analog read failed");
    return false;
  }

  moisturePercent = normalizeRawToRange(raw, _calibration.soilDryRaw, _calibration.soilWetRaw, 100.0);
  Logger::debug("Soil raw=" + String(raw) + ", moisture=" + String(moisturePercent));
  return true;
}

bool SensorManager::readLightIntensity(double& normalizedLight, int* rawOut) {
  int raw = readLightRaw();
  if (rawOut != nullptr) {
    *rawOut = raw;
  }

  if (raw < 0) {
    Logger::warn("LDR analog read failed");
    return false;
  }

  // This is a normalized 0..1000 brightness scale, not calibrated lux.
  normalizedLight = normalizeRawToRange(raw, _calibration.lightDarkRaw, _calibration.lightBrightRaw, 1000.0);
  Logger::debug("Light raw=" + String(raw) + ", normalized=" + String(normalizedLight));
  return true;
}

int SensorManager::readSoilRaw() {
#if LEAFY_SOIL_POWER_PIN >= 0
  digitalWrite(LEAFY_SOIL_POWER_PIN, HIGH);
  delay(100);
#endif

  int raw = readAveragedAnalog(LEAFY_SOIL_ADC_PIN, 8, 8);

#if LEAFY_SOIL_POWER_PIN >= 0
  digitalWrite(LEAFY_SOIL_POWER_PIN, LOW);
#endif

  return raw;
}

int SensorManager::readLightRaw() {
  return readAveragedAnalog(LEAFY_LDR_ADC_PIN, 8, 4);
}

int SensorManager::readAveragedAnalog(int pin, uint8_t samples, uint16_t sampleDelayMs) {
  if (pin < 0 || samples == 0) {
    return -1;
  }

  uint32_t total = 0;
  for (uint8_t i = 0; i < samples; ++i) {
    total += analogRead(pin);
    if (sampleDelayMs > 0) {
      delay(sampleDelayMs);
    }
  }

  return static_cast<int>(total / samples);
}

void SensorManager::maybeLogCalibrationSnapshot(const SensorSnapshot& snapshot) {
#if LEAFY_CALIBRATION_LOGGING
  uint32_t now = millis();
  uint32_t intervalMs = LEAFY_CALIBRATION_LOG_INTERVAL_SEC * 1000UL;
  if (_lastCalibrationLogMs != 0 && now - _lastCalibrationLogMs < intervalMs) {
    return;
  }

  _lastCalibrationLogMs = now;
  Logger::info("Calibration sample: soilRaw=" +
               String(snapshot.hasSoilRaw ? String(snapshot.soilRaw) : String("invalid")) +
               ", soilPct=" +
               String(snapshot.hasSoilMoisture ? String(snapshot.soilMoisture, 1) : String("invalid")) +
               ", lightRaw=" +
               String(snapshot.hasLightRaw ? String(snapshot.lightRaw) : String("invalid")) +
               ", lightNorm=" +
               String(snapshot.hasLightIntensity ? String(snapshot.lightIntensity, 1) : String("invalid")) +
               ", soilCal=" + String(_calibration.soilDryRaw) + "/" + String(_calibration.soilWetRaw) +
               ", lightCal=" + String(_calibration.lightDarkRaw) + "/" + String(_calibration.lightBrightRaw));
#else
  (void)snapshot;
#endif
}

double SensorManager::normalizeRawToRange(int raw, int lowEndpoint, int highEndpoint, double outputMax) const {
  if (lowEndpoint == highEndpoint) {
    return 0.0;
  }

  double normalized = (static_cast<double>(raw) - static_cast<double>(lowEndpoint)) /
                      (static_cast<double>(highEndpoint) - static_cast<double>(lowEndpoint));
  return clampDouble(normalized * outputMax, 0.0, outputMax);
}

double SensorManager::clampDouble(double value, double minValue, double maxValue) const {
  if (value < minValue) {
    return minValue;
  }
  if (value > maxValue) {
    return maxValue;
  }
  return value;
}

double SensorManager::roundOneDecimal(double value) const {
  return round(value * 10.0) / 10.0;
}

SensorReading SensorManager::makeReading(const String& metricCode, double value, bool valid) const {
  SensorReading reading;
  reading.metricCode = metricCode;
  reading.value = value;
  reading.valid = valid;
  return reading;
}

}  // namespace leafy
