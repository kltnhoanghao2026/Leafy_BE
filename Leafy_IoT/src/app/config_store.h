#pragma once

#include <Arduino.h>
#include <Preferences.h>

#include "models/device_config.h"

namespace leafy {

class ConfigStore {
 public:
  bool begin();
  bool load(LocalDeviceConfig& config);
  bool hasRequiredSetup(const LocalDeviceConfig& config) const;
  bool saveWifiConfig(const WifiConfig& wifi);
  bool saveRuntimeConfig(const RuntimeConfig& runtime);
  bool saveMqttConfig(const MqttEndpointConfig& mqtt);
  bool saveIdentity(const DeviceIdentity& identity);
  bool saveCalibrationConfig(const CalibrationConfig& calibration);
  bool clearWifiConfig();
  bool clearRuntimeConfig();
  bool clearCalibrationConfig();
  bool clearAllConfig();

 private:
  Preferences _preferences;
  bool _begun = false;

  RuntimeConfig defaultRuntimeConfig() const;
  CalibrationConfig defaultCalibrationConfig() const;
  String getStringOrDefault(const char* key, const String& fallback);
  uint32_t getUIntOrDefault(const char* key, uint32_t fallback);
  bool removeIfPresent(const char* key);
};

}  // namespace leafy
