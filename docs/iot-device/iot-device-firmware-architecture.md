# Leafy ESP32 Firmware Architecture And State Machine

## Purpose

This document defines the initial ESP32/ESP32-CAM firmware architecture for the current Leafy IoT collector contract. It favors small modules with explicit boundaries so the prototype can grow without becoming a single sketch file.

## Module Architecture

### `config_store`

Responsibilities:

- Store/load Wi-Fi config.
- Store/load MQTT endpoint and namespace settings.
- Store/load runtime config: `samplingIntervalSec`, `publishIntervalSec`, `offlineTimeoutSec`, `alertEnabled`, `configVersion`.
- Store calibration values such as soil dry/wet ADC bounds.
- Use ESP32 NVS/flash through `Preferences`.

Boundary:

- Exposes typed config structs.
- Does not connect to Wi-Fi, MQTT, or parse runtime config messages.

### `wifi_manager`

Responsibilities:

- Connect to Wi-Fi from stored config.
- Reconnect with backoff.
- Expose connection state, RSSI, IP, SSID.
- Enter local setup mode when Wi-Fi config is missing or a user reset is triggered.

Boundary:

- Does not store config directly except through `config_store`.
- Does not publish status; `status_service` reads its state.

### `mqtt_manager`

Responsibilities:

- Connect to MQTT broker.
- Reconnect with backoff.
- Subscribe to `coffee/prod/devices/{deviceUid}/config/set`.
- Publish telemetry, status, config ACK, and later image/meta.
- Build topic strings from local device config.

Boundary:

- Does not sample sensors.
- Does not validate backend runtime config.
- Forwards config payloads to `config_service`.

### `sensor_manager`

Responsibilities:

- Initialize sensors.
- Read raw values.
- Validate readings.
- Normalize calibrated values.
- Map physical sensors to backend metric codes:
  - `AIR_TEMP`
  - `AIR_HUMIDITY`
  - `SOIL_MOISTURE`
  - `LIGHT_INTENSITY`

Boundary:

- Emits typed readings with backend metric codes.
- Does not serialize MQTT payloads.

### `telemetry_service`

Responsibilities:

- Schedule sensor sampling using `samplingIntervalSec`.
- Build backend-compatible telemetry JSON.
- Publish according to `publishIntervalSec`.
- Include firmware version, RSSI, battery if available.

Boundary:

- Depends on `sensor_manager`, `mqtt_manager`, and active runtime config.
- Does not own Wi-Fi or MQTT reconnection.

### `status_service`

Responsibilities:

- Publish status heartbeat.
- Represent online state for the backend.
- Include runtime metadata when available: IP, SSID, RSSI, uptime.

Boundary:

- Does not decide MQTT connection lifecycle.
- Publishes only through `mqtt_manager`.

### `config_service`

Responsibilities:

- Parse incoming config payload from MQTT.
- Validate config fields and version.
- Apply config to active services.
- Persist accepted config through `config_store`.
- Send success/failure config ACK.

Boundary:

- Does not subscribe to MQTT directly.
- Does not invent backend config fields.

### `camera_service`

Responsibilities:

- Placeholder only in v1.
- Later: initialize ESP32-CAM, capture images, coordinate upload/storage, publish image/meta.

Boundary:

- Must not block telemetry/status in v1.
- No camera pipeline is required for the first prototype.

### `device_runtime`

Responsibilities:

- Own the top-level runtime state machine.
- Coordinate startup and recovery.
- Call module loops/tasks.
- Keep the firmware behavior explicit and testable.

### Utility Modules

- `logger`: consistent serial logging.
- `json_utils`: safe JSON parse/serialize helpers.
- `time_utils`: ISO timestamp and uptime helpers.
- `retry_utils`: reconnect backoff helpers.

## Interface Boundaries

The firmware should keep these dependencies one-way:

- `device_runtime` coordinates all modules.
- `config_store` is used by runtime/config/wifi setup but does not depend on other modules.
- `mqtt_manager` publishes strings and receives raw config JSON.
- `config_service` owns config validation and ACK result.
- `telemetry_service` owns payload construction from readings.
- `status_service` owns heartbeat payload construction.
- `sensor_manager` owns hardware reads and metric mapping.

## Startup Sequence

1. Boot firmware and start serial logging.
2. Initialize `config_store`.
3. Load local device identity and Wi-Fi/MQTT config.
4. If Wi-Fi config is missing, enter local setup mode.
5. Connect Wi-Fi with backoff.
6. Initialize sensors.
7. Connect MQTT with backoff.
8. Subscribe to config topic.
9. Publish an online status.
10. Enter running loop:
    - keep Wi-Fi alive
    - keep MQTT alive
    - publish status heartbeat
    - sample sensors
    - publish telemetry
    - process config messages

## Runtime Loop/Task Model

The initial scaffold uses an Arduino-style cooperative loop. Later, tasks can be split using FreeRTOS if required.

Recommended v1 loop order:

1. `device_runtime.loop()`
2. Wi-Fi reconnect handling.
3. MQTT reconnect and `client.loop()`.
4. Config message callbacks.
5. Status heartbeat scheduler.
6. Telemetry sampling/publishing scheduler.
7. Optional diagnostics.

Avoid long blocking sensor reads or camera operations in the main loop.

## Error Handling Approach

- Prefer retry with bounded exponential backoff for Wi-Fi and MQTT.
- Keep the last known good runtime config if a new config fails validation.
- ACK config failures with the backend-compatible ACK payload.
- Omit invalid sensor readings instead of publishing fake values.
- Keep local setup mode available through a physical reset/reconfigure trigger.
- Avoid frequent flash writes; persist only configuration and calibration changes.

## Device State Machine

### `BOOT`

- Entry: MCU reset or power-on.
- Work: initialize serial logging and basic hardware.
- Exit: transition to `LOAD_LOCAL_CONFIG`.
- Retry: none; hard failures go to `ERROR_RECOVERY`.

### `LOAD_LOCAL_CONFIG`

- Entry: after `BOOT`.
- Work: initialize NVS, load identity, Wi-Fi, MQTT, runtime config, calibration.
- Exit:
  - to `WIFI_SETUP_MODE` if Wi-Fi or required identity is missing.
  - to `WIFI_CONNECTING` if local config is usable.
- Retry: one reload attempt; persistent failure goes to `WIFI_SETUP_MODE` or `ERROR_RECOVERY`.

### `WIFI_SETUP_MODE`

- Entry: no Wi-Fi config, invalid identity, or user reset/reconfigure trigger.
- Work: start SoftAP and local portal, show `deviceUid`, `deviceCode`, firmware version, diagnostics, and Wi-Fi form.
- Exit:
  - to `WIFI_CONNECTING` after valid config is saved.
  - remain in setup mode on invalid input or save failure.
- Retry: user-driven.

### `WIFI_CONNECTING`

- Entry: usable Wi-Fi config exists.
- Work: connect STA mode to configured SSID.
- Exit:
  - to `MQTT_CONNECTING` on Wi-Fi connected.
  - to `WIFI_SETUP_MODE` after repeated credential failures or user trigger.
  - to `ERROR_RECOVERY` for unexpected Wi-Fi stack failures.
- Retry: exponential backoff, capped. Recommended first delays: 1s, 2s, 4s, 8s, 15s, 30s.

### `MQTT_CONNECTING`

- Entry: Wi-Fi connected.
- Work: connect to broker, subscribe to config topic.
- Exit:
  - to `READY` after MQTT connection and subscription.
  - to `WIFI_CONNECTING` if Wi-Fi drops.
  - to `ERROR_RECOVERY` after repeated broker failures.
- Retry: exponential backoff, capped. Keep Wi-Fi connected while retrying.

### `READY`

- Entry: Wi-Fi and MQTT are connected and config subscription is active.
- Work: publish online status, initialize service schedulers.
- Exit: to `RUNNING`.
- Retry: if status publish fails because MQTT dropped, go to `MQTT_CONNECTING`.

### `RUNNING`

- Entry: normal runtime.
- Work: publish heartbeats, sample sensors, publish telemetry, process config callbacks.
- Exit:
  - to `APPLYING_CONFIG` on valid config message callback.
  - to `WIFI_CONNECTING` when Wi-Fi disconnects.
  - to `MQTT_CONNECTING` when MQTT disconnects.
  - to `ERROR_RECOVERY` on unrecoverable module failure.
- Retry: reconnect modules with backoff; do not reboot for ordinary network loss.

### `APPLYING_CONFIG`

- Entry: incoming config payload received on `config/set`.
- Work: parse, validate, apply, persist, ACK success or failure.
- Exit:
  - to `RUNNING` after ACK attempt.
  - to `ERROR_RECOVERY` only if active config becomes unusable, which should be rare because previous config is retained.
- Retry: do not repeatedly apply the same invalid payload. Backend retries are future behavior.

### `ERROR_RECOVERY`

- Entry: unrecoverable local state, persistent config storage failure, or repeated critical module failure.
- Work: publish best-effort offline status if MQTT is still available, reset transient modules, optionally reboot after a delay.
- Exit:
  - to `LOAD_LOCAL_CONFIG` after local recovery/reboot.
  - to `WIFI_SETUP_MODE` if config is invalid.
- Retry: conservative backoff to avoid boot loops.

## Future Camera/Image Expansion Notes

Camera support should be added after Wi-Fi, MQTT, status, telemetry, and config ACK are stable.

Future work:

- Define actual backend media upload path.
- Decide whether image bytes go through file-service HTTP upload, object storage, or MQTT chunks.
- Publish `image/meta` only after upload succeeds and backend has a stable payload contract.
- Keep image capture in a separate task to avoid blocking telemetry/status.
