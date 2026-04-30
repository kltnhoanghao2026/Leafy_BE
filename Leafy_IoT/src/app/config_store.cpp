#include "app/config_store.h"

#include "utils/logger.h"

#ifndef LEAFY_FIRMWARE_VERSION
#define LEAFY_FIRMWARE_VERSION "leafy-esp32-dev"
#endif

#ifndef LEAFY_DEFAULT_DEVICE_UID
#define LEAFY_DEFAULT_DEVICE_UID ""
#endif

#ifndef LEAFY_DEFAULT_DEVICE_CODE
#define LEAFY_DEFAULT_DEVICE_CODE ""
#endif

#ifndef LEAFY_DEFAULT_MQTT_HOST
#define LEAFY_DEFAULT_MQTT_HOST "192.168.1.10"
#endif

#ifndef LEAFY_DEFAULT_WIFI_SSID
#define LEAFY_DEFAULT_WIFI_SSID ""
#endif

#ifndef LEAFY_DEFAULT_WIFI_PASSWORD
#define LEAFY_DEFAULT_WIFI_PASSWORD ""
#endif

#ifndef LEAFY_SOIL_DRY_RAW
#define LEAFY_SOIL_DRY_RAW 3200
#endif

#ifndef LEAFY_SOIL_WET_RAW
#define LEAFY_SOIL_WET_RAW 1200
#endif

#ifndef LEAFY_LIGHT_DARK_RAW
#define LEAFY_LIGHT_DARK_RAW 3500
#endif

#ifndef LEAFY_LIGHT_BRIGHT_RAW
#define LEAFY_LIGHT_BRIGHT_RAW 500
#endif

namespace leafy {

bool ConfigStore::begin() {
  if (_begun) {
    return true;
  }

  bool opened = _preferences.begin("leafy", false);
  if (!opened) {
    Logger::error("Failed to open NVS namespace 'leafy'");
    return false;
  }
  _begun = true;
  return opened;
}

bool ConfigStore::load(LocalDeviceConfig& config) {
  RuntimeConfig defaults = defaultRuntimeConfig();
  CalibrationConfig calibrationDefaults = defaultCalibrationConfig();

  config.identity.deviceUid = getStringOrDefault("deviceUid", LEAFY_DEFAULT_DEVICE_UID);
  config.identity.deviceCode = getStringOrDefault("deviceCode", LEAFY_DEFAULT_DEVICE_CODE);
  config.identity.deviceType = getStringOrDefault("deviceType", "ESP32");
  config.identity.firmwareVersion = LEAFY_FIRMWARE_VERSION;

  config.wifi.ssid = getStringOrDefault("wifiSsid", LEAFY_DEFAULT_WIFI_SSID);
  config.wifi.password = getStringOrDefault("wifiPass", LEAFY_DEFAULT_WIFI_PASSWORD);

  config.mqtt.host = getStringOrDefault("mqttHost", LEAFY_DEFAULT_MQTT_HOST);
  config.mqtt.port = static_cast<uint16_t>(getUIntOrDefault("mqttPort", config.mqtt.port));
  config.mqtt.username = getStringOrDefault("mqttUser", "");
  config.mqtt.password = getStringOrDefault("mqttPass", "");
  config.mqtt.productNamespace = getStringOrDefault("mqttProduct", "coffee");
  config.mqtt.environment = getStringOrDefault("mqttEnv", "prod");

  config.runtime.samplingIntervalSec = getUIntOrDefault("sampleSec", defaults.samplingIntervalSec);
  config.runtime.publishIntervalSec = getUIntOrDefault("publishSec", defaults.publishIntervalSec);
  config.runtime.offlineTimeoutSec = getUIntOrDefault("offlineSec", defaults.offlineTimeoutSec);
  config.runtime.alertEnabled = _preferences.getBool("alertEnabled", true);
  config.runtime.configVersion = getUIntOrDefault("cfgVersion", defaults.configVersion);
  config.calibration.soilDryRaw = static_cast<uint16_t>(getUIntOrDefault("soilDryRaw", calibrationDefaults.soilDryRaw));
  config.calibration.soilWetRaw = static_cast<uint16_t>(getUIntOrDefault("soilWetRaw", calibrationDefaults.soilWetRaw));
  config.calibration.lightDarkRaw = static_cast<uint16_t>(getUIntOrDefault("lightDarkRaw", calibrationDefaults.lightDarkRaw));
  config.calibration.lightBrightRaw = static_cast<uint16_t>(getUIntOrDefault("lightBrightRaw", calibrationDefaults.lightBrightRaw));

  Logger::info("Loaded local config: deviceUid=" + config.identity.deviceUid +
               ", wifiConfigured=" + String(config.wifi.isConfigured() ? "true" : "false") +
               ", mqtt=" + config.mqtt.host + ":" + String(config.mqtt.port) +
               ", topicPrefix=" + config.mqtt.productNamespace + "/" + config.mqtt.environment +
               ", configVersion=" + String(config.runtime.configVersion) +
               ", soilCal=" + String(config.calibration.soilDryRaw) + "/" + String(config.calibration.soilWetRaw) +
               ", lightCal=" + String(config.calibration.lightDarkRaw) + "/" + String(config.calibration.lightBrightRaw));

  return config.identity.isValid();
}

bool ConfigStore::hasRequiredSetup(const LocalDeviceConfig& config) const {
  return config.hasRequiredSetup();
}

bool ConfigStore::saveWifiConfig(const WifiConfig& wifi) {
  if (!wifi.isConfigured()) {
    Logger::warn("Refusing to save empty Wi-Fi SSID");
    return false;
  }

  bool ok = _preferences.putString("wifiSsid", wifi.ssid) > 0;
  _preferences.putString("wifiPass", wifi.password);
  Logger::info(ok ? "Saved Wi-Fi config for SSID=" + wifi.ssid : "Failed to save Wi-Fi config");
  return ok;
}

bool ConfigStore::saveRuntimeConfig(const RuntimeConfig& runtime) {
  bool wroteSample = _preferences.putUInt("sampleSec", runtime.samplingIntervalSec) > 0;
  bool wrotePublish = _preferences.putUInt("publishSec", runtime.publishIntervalSec) > 0;
  bool wroteOffline = _preferences.putUInt("offlineSec", runtime.offlineTimeoutSec) > 0;
  bool wroteAlert = _preferences.putBool("alertEnabled", runtime.alertEnabled) > 0;
  bool wroteVersion = _preferences.putUInt("cfgVersion", runtime.configVersion) > 0;

  bool verified = getUIntOrDefault("sampleSec", 0) == runtime.samplingIntervalSec &&
                  getUIntOrDefault("publishSec", 0) == runtime.publishIntervalSec &&
                  getUIntOrDefault("offlineSec", 0) == runtime.offlineTimeoutSec &&
                  _preferences.getBool("alertEnabled", !runtime.alertEnabled) == runtime.alertEnabled &&
                  getUIntOrDefault("cfgVersion", 0) == runtime.configVersion;

  bool ok = wroteSample && wrotePublish && wroteOffline && wroteAlert && wroteVersion && verified;
  Logger::info(ok ? "Saved and verified runtime config version " + String(runtime.configVersion)
                  : "Failed to save/verify runtime config");
  return ok;
}

bool ConfigStore::saveMqttConfig(const MqttEndpointConfig& mqtt) {
  if (mqtt.host.length() == 0 || mqtt.port == 0) {
    Logger::warn("Refusing to save invalid MQTT endpoint");
    return false;
  }

  bool ok = true;
  ok = ok && _preferences.putString("mqttHost", mqtt.host) > 0;
  ok = ok && _preferences.putUInt("mqttPort", mqtt.port) > 0;
  _preferences.putString("mqttUser", mqtt.username);
  _preferences.putString("mqttPass", mqtt.password);
  ok = ok && _preferences.putString("mqttProduct", mqtt.productNamespace) > 0;
  ok = ok && _preferences.putString("mqttEnv", mqtt.environment) > 0;
  Logger::info(ok ? "Saved MQTT endpoint " + mqtt.host + ":" + String(mqtt.port)
                  : "Failed to save MQTT endpoint");
  return ok;
}

bool ConfigStore::saveIdentity(const DeviceIdentity& identity) {
  bool ok = true;
  ok = ok && _preferences.putString("deviceUid", identity.deviceUid) > 0;
  ok = ok && _preferences.putString("deviceCode", identity.deviceCode) > 0;
  ok = ok && _preferences.putString("deviceType", identity.deviceType) > 0;
  Logger::info(ok ? "Saved device identity " + identity.deviceUid : "Failed to save device identity");
  return ok;
}

bool ConfigStore::saveCalibrationConfig(const CalibrationConfig& calibration) {
  bool ok = true;
  ok = ok && _preferences.putUInt("soilDryRaw", calibration.soilDryRaw) > 0;
  ok = ok && _preferences.putUInt("soilWetRaw", calibration.soilWetRaw) > 0;
  ok = ok && _preferences.putUInt("lightDarkRaw", calibration.lightDarkRaw) > 0;
  ok = ok && _preferences.putUInt("lightBrightRaw", calibration.lightBrightRaw) > 0;
  Logger::info(ok ? "Saved sensor calibration config" : "Failed to save sensor calibration config");
  return ok;
}

bool ConfigStore::clearWifiConfig() {
  bool ok = true;
  ok = ok && removeIfPresent("wifiSsid");
  ok = ok && removeIfPresent("wifiPass");
  return ok;
}

bool ConfigStore::clearRuntimeConfig() {
  bool ok = true;
  ok = ok && removeIfPresent("sampleSec");
  ok = ok && removeIfPresent("publishSec");
  ok = ok && removeIfPresent("offlineSec");
  ok = ok && removeIfPresent("alertEnabled");
  ok = ok && removeIfPresent("cfgVersion");
  return ok;
}

bool ConfigStore::clearCalibrationConfig() {
  bool ok = true;
  ok = ok && removeIfPresent("soilDryRaw");
  ok = ok && removeIfPresent("soilWetRaw");
  ok = ok && removeIfPresent("lightDarkRaw");
  ok = ok && removeIfPresent("lightBrightRaw");
  return ok;
}

bool ConfigStore::clearAllConfig() {
  Logger::warn("Clearing all local Leafy IoT config");
  return _preferences.clear();
}

RuntimeConfig ConfigStore::defaultRuntimeConfig() const {
  RuntimeConfig config;
  config.samplingIntervalSec = DEFAULT_SAMPLING_INTERVAL_SEC;
  config.publishIntervalSec = DEFAULT_PUBLISH_INTERVAL_SEC;
  config.offlineTimeoutSec = DEFAULT_OFFLINE_TIMEOUT_SEC;
  config.alertEnabled = true;
  config.configVersion = DEFAULT_CONFIG_VERSION;
  return config;
}

CalibrationConfig ConfigStore::defaultCalibrationConfig() const {
  CalibrationConfig config;
  config.soilDryRaw = LEAFY_SOIL_DRY_RAW;
  config.soilWetRaw = LEAFY_SOIL_WET_RAW;
  config.lightDarkRaw = LEAFY_LIGHT_DARK_RAW;
  config.lightBrightRaw = LEAFY_LIGHT_BRIGHT_RAW;
  return config;
}

String ConfigStore::getStringOrDefault(const char* key, const String& fallback) {
  return _preferences.getString(key, fallback);
}

uint32_t ConfigStore::getUIntOrDefault(const char* key, uint32_t fallback) {
  return _preferences.getUInt(key, fallback);
}

bool ConfigStore::removeIfPresent(const char* key) {
  if (!_preferences.isKey(key)) {
    return true;
  }
  return _preferences.remove(key);
}

}  // namespace leafy
