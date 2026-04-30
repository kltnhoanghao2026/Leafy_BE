# Leafy IoT Device Protocol Specification v1

## 1. Purpose And Scope

This specification defines the physical IoT device side of the current Leafy collector contract. It is intended for ESP32 and ESP32-CAM firmware that talks to the existing `iot-metrics-collector-service`.

This is a v1 compatibility baseline. It documents what the backend currently accepts. Future improvements such as per-device MQTT credentials, signed payloads, richer image processing, or configurable product namespaces are recommendations only unless explicitly added to the backend.

## 2. Device Identity Model

Each physical module needs a stable identity that matches the backend device record.

| Field | Meaning | Firmware source |
| --- | --- | --- |
| `deviceUid` | Stable runtime identifier used in MQTT topics and HTTP fallback ingest paths. Must match the provisioned backend record. | Factory config, QR label, or local NVS. |
| `deviceCode` | Human-facing code used during provision/claim. Should be printed on the device or box. | Factory config or local NVS. |
| `deviceType` | Hardware family, for example `ESP32` or `ESP32-CAM`. | Build-time default or local NVS. |
| `firmwareVersion` | Firmware build version published in telemetry. | Build-time constant. |
| Local `configVersion` | Last applied backend runtime config version. | NVS/flash. |

The current runtime identity model is `deviceUid` plus topic path. There is no backend-enforced MQTT credential binding yet.

## 3. Onboarding Flow

Backend onboarding is separate from local Wi-Fi setup.

Backend-supported flow:

1. Provision the device record.
   - API: `POST /iot/devices/provision`
   - Body: `deviceUid`, `deviceCode`, `deviceName`, `deviceType`
   - Result: `provisioningStatus=PROVISIONED`, `status=OFFLINE`, `isActive=true`

2. Generate a claim code.
   - API: `POST /iot/devices/{deviceId}/claim-code`
   - TTL: 15 minutes

3. Claim and bind the device.
   - API: `POST /iot/devices/claim`
   - Direct calls require `X-User-Id`
   - Body: `deviceUid`, `claimCode`, `farmPlotId`, `zoneId`
   - Result: device gets `ownerUser`, `farmPlot`, `zone`, and `provisioningStatus=CLAIMED`

The device firmware does not currently perform this backend claim flow. The firmware should expose `deviceUid`, `deviceCode`, and basic diagnostics locally so a web/mobile/backend portal can complete provision and claim.

## 4. MQTT Topic Contract

Current backend default namespace:

```text
coffee/prod
```

The current collector subscribes to:

```text
coffee/prod/devices/{deviceUid}/telemetry
coffee/prod/devices/{deviceUid}/status
coffee/prod/devices/{deviceUid}/ack
coffee/prod/devices/{deviceUid}/image/meta
```

The backend config push publishes to:

```text
coffee/{env}/devices/{deviceUid}/config/set
```

In current local/dev config, `{env}` is effectively `prod` and product namespace is `coffee`.

Firmware topic rules:

- Use `coffee/prod` by default for v1 compatibility.
- Keep product namespace and env configurable in local firmware config, but do not assume backend supports arbitrary values without backend config/code changes.
- Use the provisioned `deviceUid` exactly in the topic path.
- Subscribe to `coffee/prod/devices/{deviceUid}/config/set`.
- Publish config ACKs to `coffee/prod/devices/{deviceUid}/ack`.

## 5. Telemetry Payload Schema

Topic:

```text
coffee/prod/devices/{deviceUid}/telemetry
```

Payload fields:

| Field | Required | Type | Notes |
| --- | --- | --- | --- |
| `ts` | Recommended | ISO-8601 string | Device event time. If device time is not synced, firmware may omit and backend will use server time. |
| `firmwareVersion` | Recommended | string | Build/version identifier. Backend stores this on the device when provided. |
| `battery` | Optional | integer | Battery percentage or equivalent normalized value. Backend accepts but mostly does not persist/expose yet. |
| `rssi` | Optional | integer | Wi-Fi RSSI in dBm. Backend accepts but mostly does not persist/expose yet. |
| `metrics` | Required for useful ingest | object | Map of backend sensor type code to numeric value. Empty metrics are ignored. |

Example:

```json
{
  "ts": "2026-04-19T03:00:00Z",
  "firmwareVersion": "leafy-esp32-0.1.0",
  "battery": 87,
  "rssi": -61,
  "metrics": {
    "AIR_TEMP": 29.4,
    "AIR_HUMIDITY": 71.2,
    "SOIL_MOISTURE": 42.8,
    "LIGHT_INTENSITY": 680
  }
}
```

Backend ingest requirements:

- The device must exist.
- The device must be active.
- The device must be claimed.
- The device must be bound to a zone.
- Each metric key must exist in `sensor_types.code`.

## 6. Status Payload Schema

Topic:

```text
coffee/prod/devices/{deviceUid}/status
```

Payload fields:

| Field | Required | Type | Notes |
| --- | --- | --- | --- |
| `ts` | Recommended | ISO-8601 string | Device event time. If omitted, backend uses server time. |
| `online` | Required | boolean | `true` sets device `ONLINE`; `false` sets `OFFLINE`. |
| `ip` | Optional | string | Local Wi-Fi IP. Accepted but mostly not persisted/exposed yet. |
| `wifiSsid` | Optional | string | Connected SSID. Accepted but mostly not persisted/exposed yet. |
| `rssi` | Optional | integer | Wi-Fi RSSI in dBm. |
| `uptimeSec` | Optional | integer | Device uptime in seconds. |

Example:

```json
{
  "ts": "2026-04-19T03:00:00Z",
  "online": true,
  "ip": "192.168.1.42",
  "wifiSsid": "farm-wifi",
  "rssi": -58,
  "uptimeSec": 3600
}
```

Telemetry does not currently mark the device online. Firmware must publish status heartbeats.

## 7. Config ACK Payload Schema

Topic:

```text
coffee/prod/devices/{deviceUid}/ack
```

Payload fields:

| Field | Required | Type | Notes |
| --- | --- | --- | --- |
| `type` | Required | string | Accepted values: `config`, `config-applied`, `config_ack`. |
| `configVersion` | Required | integer | Must match the backend's current config version. |
| `success` | Required | boolean | `true` means config was applied and persisted; `false` means apply failed. |
| `ts` | Recommended | ISO-8601 string | Device event time. |
| `error` | Optional | string/null | Failure reason when `success=false`. |

Success example:

```json
{
  "type": "config",
  "configVersion": 2,
  "success": true,
  "ts": "2026-04-19T03:00:05Z",
  "error": null
}
```

Failure example:

```json
{
  "type": "config",
  "configVersion": 2,
  "success": false,
  "ts": "2026-04-19T03:00:05Z",
  "error": "publishIntervalSec must be >= samplingIntervalSec"
}
```

## 8. Image/Meta Payload Schema

Topic recognized by backend:

```text
coffee/prod/devices/{deviceUid}/image/meta
```

Current backend behavior: the collector recognizes this topic and logs the payload, but does not yet store or process it as a complete media pipeline.

For v1 firmware, camera/image is a placeholder. Do not make image capture a dependency for basic telemetry operation.

Future-compatible shape recommendation only:

```json
{
  "ts": "2026-04-19T03:00:00Z",
  "mediaType": "IMAGE",
  "triggerType": "MANUAL",
  "fileId": "optional-file-service-id",
  "sizeBytes": 123456
}
```

## 9. Sensor Mapping Contract

Firmware must publish metric keys that already exist in backend `sensor_types.code`.

| Physical sensor concept | Backend metric code | Unit | Suggested precision | Notes |
| --- | --- | --- | --- | --- |
| Air temperature | `AIR_TEMP` | Celsius (`C`) | 0.1 C | Calibrate sensor offset per hardware batch if needed. |
| Air humidity | `AIR_HUMIDITY` | Percent (`%`) | 0.1% | Clamp to `0..100` after validation. |
| Soil moisture | `SOIL_MOISTURE` | Percent (`%`) | 0.1% | Normalize ADC/raw capacitive value using dry/wet calibration. |
| Light intensity | `LIGHT_INTENSITY` | normalized brightness scale | 0.1 units | Current LDR firmware reports a calibrated 0..1000 normalized brightness value, not true lux. The backend seed data may label this as `lux`; treat it as normalized until a lux-capable circuit is calibrated. |

Invalid, missing, or physically impossible readings should be omitted from `metrics` rather than published as arbitrary fallback values.

## 10. Config Contract

Config push topic:

```text
coffee/prod/devices/{deviceUid}/config/set
```

Backend config fields:

| Field | Type | Default | Firmware meaning |
| --- | --- | --- | --- |
| `samplingIntervalSec` | integer | `60` | Sensor sampling cadence. |
| `publishIntervalSec` | integer | `300` | Telemetry publish cadence. Must be >= sampling interval. |
| `offlineTimeoutSec` | integer | `900` | Device-side offline/liveness reference. Backend stores/pushes it but currently may not auto-mark stale devices offline. |
| `alertEnabled` | boolean | `true` | Whether device should participate in alert-oriented behavior. Backend alert evaluation is still server-side. |
| `configVersion` | integer | `1` | Monotonic backend config version. |

Firmware rules:

1. Parse JSON only from the device's own `config/set` topic.
2. Validate required fields and intervals.
3. Ignore stale config versions lower than the stored local version and publish a failed or ignored ACK only if needed for diagnostics.
4. If config version equals local version, treat as idempotent; ACK success if the stored config matches.
5. If config version is newer and valid, apply it in memory, persist to NVS, then ACK success.
6. If validation or persistence fails, keep the previous config and ACK failure with `error`.
7. After power loss, load the persisted config and local `configVersion` before connecting to MQTT.

Minimum validation:

- `samplingIntervalSec > 0`
- `publishIntervalSec > 0`
- `offlineTimeoutSec > 0`
- `publishIntervalSec >= samplingIntervalSec`
- `offlineTimeoutSec > publishIntervalSec`
- `alertEnabled` is present
- `configVersion >= 1`

## 11. Runtime Behavior Contract

Recommended prototype cadence:

- Status heartbeat: every 30 seconds while MQTT is connected.
- Telemetry sample: `samplingIntervalSec`.
- Telemetry publish: `publishIntervalSec`.
- Default config if no pushed config exists: sampling 60s, publish 300s, offline timeout 900s, alerts enabled, version 1.

Wi-Fi loss:

- Stop publishing immediately.
- Reconnect with backoff.
- Keep sampling only if local buffering is implemented and memory is available.
- Publish an `online=true` status after reconnect.

MQTT loss:

- Keep Wi-Fi connected.
- Reconnect with backoff.
- Resubscribe to `config/set` after reconnect.
- Publish an `online=true` status after reconnect.

Buffering:

- v1 prototype may use a small in-memory latest-sample buffer only.
- Do not claim durable telemetry delivery until flash-backed buffering is implemented.
- Avoid flash writes for every reading.

Before claim or zone binding:

- The backend rejects telemetry until the device is active, claimed, and bound to a zone.
- Firmware cannot reliably know claim/zone state from MQTT today.
- Local portal should show device identity so the user can complete backend claim.
- Device may publish status/telemetry, but operators should expect ingest errors until backend claim/bind is complete.

## 12. Security Limitations

Current backend limitations:

- Runtime MQTT trust is weak.
- Practical runtime identity is `deviceUid` plus topic shape.
- The broker/collector does not currently bind per-device credentials to a specific `deviceUid`.
- `claimTokenHash` exists in the backend model but is not part of the runtime MQTT trust path.

Future recommendations:

- Per-device MQTT username/password or client certificate.
- Broker ACLs restricting each device to its own topic prefix.
- Payload signing or HMAC using a device secret.
- Backend-side device credential rotation and revocation.
- Provisioning API authorization for admin/internal callers only.

These are not v1-compatible assumptions until backend support exists.
