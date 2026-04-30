#pragma once

#include <Arduino.h>

#include "app/camera_service.h"
#include "app/config_service.h"
#include "app/config_store.h"
#include "app/file_upload_service.h"
#include "app/mqtt_manager.h"
#include "app/sensor_manager.h"
#include "app/setup_portal.h"
#include "app/status_service.h"
#include "app/telemetry_service.h"
#include "app/wifi_manager.h"
#include "models/device_config.h"

namespace leafy {

enum class RuntimeState {
  BOOT,
  LOAD_LOCAL_CONFIG,
  WIFI_SETUP_MODE,
  WIFI_CONNECTING,
  MQTT_CONNECTING,
  READY,
  RUNNING,
  APPLYING_CONFIG,
  ERROR_RECOVERY
};

class DeviceRuntime {
 public:
  void begin();
  void loop();
  RuntimeState state() const;

 private:
  RuntimeState _state = RuntimeState::BOOT;
  LocalDeviceConfig _config;
  ConfigStore _configStore;
  WifiManager _wifiManager;
  MqttManager _mqttManager;
  SensorManager _sensorManager;
  TelemetryService _telemetryService;
  StatusService _statusService;
  ConfigService _configService;
  CameraService _cameraService;
  FileUploadService _fileUploadService;
  SetupPortal _setupPortal;
  bool _modulesInitialized = false;
  bool _sensorModulesInitialized = false;

  void transitionTo(RuntimeState next);
  void enterBoot();
  void enterLoadLocalConfig();
  void enterWifiSetupMode();
  void enterWifiConnecting();
  void enterMqttConnecting();
  void enterReady();
  void runCommonLoops();
  void initializeRuntimeModules();
  void initializeSensorModules();
  bool applyRuntimeConfig(const RuntimeConfig& runtime, String& errorMessage);
  void handleCameraCaptureCommand(const String& payload);
  void publishCaptureFailure(const String& requestId, const String& error);
  const char* stateName(RuntimeState state) const;
};

}  // namespace leafy
