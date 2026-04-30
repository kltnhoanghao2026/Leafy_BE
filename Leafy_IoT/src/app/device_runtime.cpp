#include "app/device_runtime.h"

#include <ArduinoJson.h>

#include "utils/logger.h"
#include "utils/time_utils.h"

namespace leafy {

void DeviceRuntime::begin() {
  Logger::info("Runtime begin");
  _state = RuntimeState::BOOT;
}

void DeviceRuntime::loop() {
  switch (_state) {
    case RuntimeState::BOOT:
      enterBoot();
      transitionTo(RuntimeState::LOAD_LOCAL_CONFIG);
      break;

    case RuntimeState::LOAD_LOCAL_CONFIG:
      enterLoadLocalConfig();
      break;

    case RuntimeState::WIFI_SETUP_MODE:
      _wifiManager.loop();
      _setupPortal.loop();
      break;

    case RuntimeState::WIFI_CONNECTING:
      _wifiManager.loop();
      if (_wifiManager.isConnected()) {
        transitionTo(RuntimeState::MQTT_CONNECTING);
        enterMqttConnecting();
      }
      break;

    case RuntimeState::MQTT_CONNECTING:
      _wifiManager.loop();
      _mqttManager.loop(_wifiManager.isConnected());
      if (!_wifiManager.isConnected()) {
        transitionTo(RuntimeState::WIFI_CONNECTING);
      } else if (_mqttManager.isConnected() && _mqttManager.isConfigSubscribed()) {
        transitionTo(RuntimeState::READY);
      }
      break;

    case RuntimeState::READY:
      enterReady();
      break;

    case RuntimeState::RUNNING:
    {
      bool mqttWasConnected = _mqttManager.isConnected();
      runCommonLoops();
      if (!_wifiManager.isConnected()) {
        transitionTo(RuntimeState::WIFI_CONNECTING);
      } else if (!_mqttManager.isConnected()) {
        transitionTo(RuntimeState::MQTT_CONNECTING);
        enterMqttConnecting();
      } else if (!mqttWasConnected && _mqttManager.isConfigSubscribed()) {
        _statusService.publishOnlineNow();
      }
      break;
    }

    case RuntimeState::APPLYING_CONFIG:
      // Config callbacks are handled synchronously by ConfigService in v1 scaffold.
      transitionTo(RuntimeState::RUNNING);
      break;

    case RuntimeState::ERROR_RECOVERY:
      // TODO: add bounded recovery/reboot policy.
      delay(1000);
      transitionTo(RuntimeState::LOAD_LOCAL_CONFIG);
      break;
  }
}

RuntimeState DeviceRuntime::state() const {
  return _state;
}

void DeviceRuntime::transitionTo(RuntimeState next) {
  if (_state == next) {
    return;
  }
  Logger::info(String("State ") + stateName(_state) + " -> " + stateName(next));
  _state = next;
}

void DeviceRuntime::enterBoot() {
  Logger::info("Booting Leafy IoT device runtime");
}

void DeviceRuntime::enterLoadLocalConfig() {
  if (!_configStore.begin() || !_configStore.load(_config)) {
    transitionTo(RuntimeState::ERROR_RECOVERY);
    return;
  }

  if (!_configStore.hasRequiredSetup(_config)) {
    Logger::warn("Required local setup is missing; Wi-Fi setup mode required");
    transitionTo(RuntimeState::WIFI_SETUP_MODE);
    enterWifiSetupMode();
    return;
  }

  initializeRuntimeModules();
  transitionTo(RuntimeState::WIFI_CONNECTING);
  enterWifiConnecting();
}

void DeviceRuntime::initializeRuntimeModules() {
  if (_modulesInitialized) {
    return;
  }

  _configService.begin(&_config, &_configStore, &_mqttManager);
  _configService.onApplyRuntimeConfig([this](const RuntimeConfig& runtime, String& errorMessage) {
    return applyRuntimeConfig(runtime, errorMessage);
  });
  _mqttManager.onConfigMessage([this](const String& payload) {
    transitionTo(RuntimeState::APPLYING_CONFIG);
    _configService.handleConfigMessage(payload);
    transitionTo(RuntimeState::RUNNING);
  });
  _mqttManager.onCameraCaptureMessage([this](const String& payload) {
    handleCameraCaptureCommand(payload);
  });

  initializeSensorModules();
  _telemetryService.begin(_config, &_sensorManager, &_mqttManager);
  _statusService.begin(&_mqttManager, &_wifiManager, DEFAULT_STATUS_HEARTBEAT_SEC);
  _modulesInitialized = true;
}

void DeviceRuntime::enterWifiSetupMode() {
  initializeSensorModules();
  _setupPortal.begin(
      &_config,
      &_configStore,
      &_wifiManager,
      &_sensorManager,
      &_mqttManager,
      [this]() { return String(stateName(_state)); });
  _wifiManager.enterSetupMode(_setupPortal.apSsid());
  _setupPortal.start();
}

void DeviceRuntime::enterWifiConnecting() {
  _wifiManager.begin(_config.wifi);
}

void DeviceRuntime::enterMqttConnecting() {
  _mqttManager.begin(_config);
}

void DeviceRuntime::enterReady() {
  if (_statusService.publishOnlineNow()) {
    transitionTo(RuntimeState::RUNNING);
  } else {
    Logger::warn("Initial online status publish failed; returning to MQTT_CONNECTING");
    transitionTo(RuntimeState::MQTT_CONNECTING);
  }
}

void DeviceRuntime::runCommonLoops() {
  uint32_t now = millis();
  _wifiManager.loop();
  _mqttManager.loop(_wifiManager.isConnected());
  _statusService.loop(now);
  _telemetryService.loop(now, _wifiManager.rssi());
}

void DeviceRuntime::initializeSensorModules() {
  if (_sensorModulesInitialized) {
    return;
  }

  _sensorManager.begin(_config.calibration);
  _cameraService.begin();
  _sensorModulesInitialized = true;
}

bool DeviceRuntime::applyRuntimeConfig(const RuntimeConfig& runtime, String& errorMessage) {
  if (!_modulesInitialized) {
    errorMessage = "runtime modules are not initialized";
    return false;
  }

  _config.runtime = runtime;
  _telemetryService.updateConfig(runtime);

  // The backend config includes offlineTimeoutSec, but the current firmware
  // has no backend-defined status heartbeat field. Keep status heartbeat fixed
  // for v1 and leave offlineTimeoutSec as persisted liveness metadata.
  Logger::info("Runtime config is active: version=" + String(runtime.configVersion) +
               ", sampleSec=" + String(runtime.samplingIntervalSec) +
               ", publishSec=" + String(runtime.publishIntervalSec) +
               ", offlineSec=" + String(runtime.offlineTimeoutSec) +
               ", alertEnabled=" + String(runtime.alertEnabled ? "true" : "false") +
               ", statusHeartbeatSec=" + String(DEFAULT_STATUS_HEARTBEAT_SEC));
  return true;
}

void DeviceRuntime::handleCameraCaptureCommand(const String& payload) {
  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, payload);
  if (err) {
    Logger::warn("Invalid camera capture command JSON");
    publishCaptureFailure("", "INVALID_COMMAND_JSON");
    return;
  }

  String requestId = doc["requestId"] | "";
  if (requestId.length() == 0) {
    publishCaptureFailure("", "MISSING_REQUEST_ID");
    return;
  }

  String resolution = doc["resolution"] | "VGA";
  String quality = doc["quality"] | "MEDIUM";
  String uploadMode = doc["upload"]["mode"] | "";
  String uploadEndpoint = doc["upload"]["endpoint"] | "";
  if (uploadMode != "FILE_SERVICE_MULTIPART" || uploadEndpoint.length() == 0) {
    publishCaptureFailure(requestId, "INVALID_UPLOAD_TARGET");
    return;
  }

  CameraService::Frame frame;
  String error;
  if (!_cameraService.capture(resolution, quality, frame, error)) {
    publishCaptureFailure(requestId, error);
    return;
  }

  String fileId;
  bool uploaded = _fileUploadService.uploadMultipart(uploadEndpoint, frame, fileId, error);
  size_t sizeBytes = frame.size;
  int width = frame.width;
  int height = frame.height;
  _cameraService.release(frame);

  if (!uploaded) {
    publishCaptureFailure(requestId, error);
    return;
  }

  JsonDocument result;
  result["requestId"] = requestId;
  result["success"] = true;
  result["ts"] = TimeUtils::nowIso8601();
  result["fileId"] = fileId;
  result["contentType"] = "image/jpeg";
  result["sizeBytes"] = sizeBytes;
  result["width"] = width;
  result["height"] = height;
  result["error"] = nullptr;

  String resultPayload;
  serializeJson(result, resultPayload);
  if (!_mqttManager.publishImageMeta(resultPayload)) {
    Logger::warn("Failed to publish image/meta success for requestId=" + requestId);
  }
}

void DeviceRuntime::publishCaptureFailure(const String& requestId, const String& error) {
  JsonDocument result;
  result["requestId"] = requestId;
  result["success"] = false;
  result["ts"] = TimeUtils::nowIso8601();
  result["error"] = error.length() > 0 ? error : "CAMERA_CAPTURE_FAILED";

  String resultPayload;
  serializeJson(result, resultPayload);
  if (!_mqttManager.publishImageMeta(resultPayload)) {
    Logger::warn("Failed to publish image/meta failure for requestId=" + requestId);
  }
}

const char* DeviceRuntime::stateName(RuntimeState state) const {
  switch (state) {
    case RuntimeState::BOOT:
      return "BOOT";
    case RuntimeState::LOAD_LOCAL_CONFIG:
      return "LOAD_LOCAL_CONFIG";
    case RuntimeState::WIFI_SETUP_MODE:
      return "WIFI_SETUP_MODE";
    case RuntimeState::WIFI_CONNECTING:
      return "WIFI_CONNECTING";
    case RuntimeState::MQTT_CONNECTING:
      return "MQTT_CONNECTING";
    case RuntimeState::READY:
      return "READY";
    case RuntimeState::RUNNING:
      return "RUNNING";
    case RuntimeState::APPLYING_CONFIG:
      return "APPLYING_CONFIG";
    case RuntimeState::ERROR_RECOVERY:
      return "ERROR_RECOVERY";
  }
  return "UNKNOWN";
}

}  // namespace leafy
