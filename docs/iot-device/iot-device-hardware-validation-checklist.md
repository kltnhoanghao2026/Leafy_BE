# IoT Device Hardware Validation Checklist

Use this checklist while testing a Leafy ESP32 or ESP32-CAM module on a bench. Record pass/fail notes before moving to setup portal, camera, or production security work.

## Test Metadata

| Item | Value |
| --- | --- |
| Date | |
| Tester | |
| Board | |
| Firmware version | |
| `deviceUid` | |
| MQTT broker host | |
| Backend environment | |

## 1. Pre-Checks

| Check | Pass/Fail | Notes |
| --- | --- | --- |
| DHT11 connected to configured `LEAFY_DHT_PIN` | | |
| YL-69 analog output connected to configured `LEAFY_SOIL_ADC_PIN` | | |
| YL-69 power-control wiring matches `LEAFY_SOIL_POWER_PIN`, or limitation is documented | | |
| LDR divider connected to configured `LEAFY_LDR_ADC_PIN` | | |
| Board power is stable during Wi-Fi transmit | | |
| MQTT broker reachable from same Wi-Fi network | | |
| Backend device record uses matching `deviceUid` and `deviceCode` | | |
| Backend device is claimed and bound to a zone | | |
| `sensor_types` includes all four metric codes | | |

Metric codes:

- `AIR_TEMP`
- `AIR_HUMIDITY`
- `SOIL_MOISTURE`
- `LIGHT_INTENSITY`

## 2. Flash Check

| Check | Pass/Fail | Notes |
| --- | --- | --- |
| `platformio.ini` identity values match backend record | | |
| Broker host/port are correct | | |
| Pin macros match wiring | | |
| Build succeeds with `pio run -e esp32dev` or `pio run -e esp32cam` | | |
| Upload succeeds | | |
| Serial monitor opens at `115200` | | |

Expected first log:

```text
[INFO] Leafy IoT firmware starting
```

## 3. Boot And State Check

| Check | Pass/Fail | Notes |
| --- | --- | --- |
| Runtime begins | | |
| State transitions from `BOOT` to `LOAD_LOCAL_CONFIG` | | |
| Local config loads without error | | |
| Calibration values are printed | | |
| Device does not enter setup mode when Wi-Fi credentials are configured | | |

Expected logs:

```text
[INFO] Runtime begin
[INFO] State BOOT -> LOAD_LOCAL_CONFIG
[INFO] Loaded local config: deviceUid=..., wifiConfigured=true, mqtt=...
[INFO] Sensor calibration active: soilDryRaw=..., soilWetRaw=..., lightDarkRaw=..., lightBrightRaw=...
```

## 4. Wi-Fi Check

| Check | Pass/Fail | Notes |
| --- | --- | --- |
| Wi-Fi connection attempt is logged | | |
| Device obtains local IP | | |
| Password is not printed | | |
| Reconnect backoff logs are understandable if Wi-Fi fails | | |

Expected logs:

```text
[INFO] Connecting Wi-Fi SSID=...
[INFO] Wi-Fi connected: 192.168.x.x
```

## 4.1 Local Setup Portal Check

Run this check with Wi-Fi config absent or after clearing Wi-Fi config.

| Check | Pass/Fail | Notes |
| --- | --- | --- |
| Device enters `WIFI_SETUP_MODE` | | |
| SoftAP starts with `Leafy-Setup-<suffix>` SSID | | |
| Portal URL is logged as `http://192.168.4.1` | | |
| `GET /` shows device identity and mode | | |
| `GET /wifi` shows SSID/password form | | |
| `POST /wifi` saves Wi-Fi credentials without logging password | | |
| Device reboots after Wi-Fi save | | |
| `GET /diagnostics` shows config, calibration, and sensor readings | | |
| `POST /reset` clears Wi-Fi credentials only and reboots into setup mode | | |

Expected setup logs:

```text
[WARN] Required local setup is missing; Wi-Fi setup mode required
[INFO] Setup AP started: SSID=Leafy-Setup-..., IP=192.168.4.1
[INFO] Setup portal started: http://192.168.4.1
```

## 5. MQTT Check

| Check | Pass/Fail | Notes |
| --- | --- | --- |
| MQTT broker connection attempt is logged | | |
| MQTT connects | | |
| Config topic subscription succeeds | | |
| MQTT reconnect logs are understandable if broker is unavailable | | |

Expected logs:

```text
[INFO] Connecting MQTT broker host:1883
[INFO] MQTT connected
[INFO] Subscribed config topic coffee/prod/devices/{deviceUid}/config/set
```

## 6. Status Check

| Check | Pass/Fail | Notes |
| --- | --- | --- |
| Initial online status publish succeeds | | |
| Status heartbeat repeats at expected cadence | | |
| Collector receives status topic | | |
| Backend marks device `ONLINE` | | |

Topic:

```text
coffee/prod/devices/{deviceUid}/status
```

Expected payload fields:

- `ts`
- `online`
- `ip`
- `wifiSsid`
- `rssi`
- `uptimeSec`

## 7. Telemetry Check

| Check | Pass/Fail | Notes |
| --- | --- | --- |
| Sensor sample log appears | | |
| At least one valid metric is reported | | |
| Telemetry publish succeeds | | |
| Collector receives telemetry topic | | |
| Latest readings show values for the expected zone | | |

Topic:

```text
coffee/prod/devices/{deviceUid}/telemetry
```

Expected serial logs:

```text
[INFO] Telemetry sample captured: validMetrics=4/4
[INFO] Telemetry published: validMetrics=4, rssi=-61
```

If telemetry is not ingested, check claim status, zone binding, and metric code spelling before changing firmware.

## 8. Config Push / ACK Check

| Check | Pass/Fail | Notes |
| --- | --- | --- |
| Backend pushes config to `config/set` | | |
| Device logs config receipt | | |
| Device validates config | | |
| Device applies intervals without reboot | | |
| Device persists config to NVS | | |
| Device publishes ACK | | |
| Backend accepts ACK for matching `configVersion` | | |
| New sampling/publish intervals are visible in logs | | |

Topics:

```text
coffee/prod/devices/{deviceUid}/config/set
coffee/prod/devices/{deviceUid}/ack
```

Expected ACK behavior:

- Valid newer config: success ACK.
- Same version with same values: success ACK.
- Same version with different values: failure ACK.
- Older version: failure ACK with `stale config version`.
- Invalid intervals: failure ACK and previous active config remains effective.

## 9. Calibration Check

| Check | Pass/Fail | Notes |
| --- | --- | --- |
| `LEAFY_CALIBRATION_LOGGING=1` build was flashed for calibration pass | | |
| Dry soil raw readings recorded | | |
| Wet soil raw readings recorded | | |
| Dark light raw readings recorded | | |
| Bright light raw readings recorded | | |
| Calibration macros or NVS values updated | | |
| Normalized soil dry/wet behavior verified | | |
| Normalized light dark/bright behavior verified | | |

Expected calibration log:

```text
[INFO] Calibration sample: soilRaw=3180, soilPct=1.0, lightRaw=3440, lightNorm=20.7, soilCal=3200/1200, lightCal=3500/500
```

## 10. Pass / Fail Notes

| Area | Result | Notes |
| --- | --- | --- |
| Flash | | |
| Boot/state | | |
| Wi-Fi | | |
| MQTT | | |
| Status | | |
| Telemetry | | |
| Config apply/ACK | | |
| Calibration | | |
| Backend ingest | | |

## Exit Criteria

The bench pass is acceptable when:

- Device boots and reaches `RUNNING`.
- Wi-Fi and MQTT recover from ordinary disconnects.
- Status makes the device `ONLINE`.
- Telemetry is ingested after the device is claimed and bound.
- Config push changes telemetry intervals without reboot.
- ACK is accepted by the backend for the current config version.
- Soil and light calibration values are recorded for the physical build.
