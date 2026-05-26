# IoT Device Current-Zone Filters Phase A

## New Query Parameters

```http
GET /iot/devices/{deviceId}/latest-readings?zoneId=...
GET /iot/devices/{deviceId}/charts?sensorCode=...&range=...&zoneId=...
GET /iot/devices/{deviceId}/media?zoneId=...
```

## Behavior

| `zoneId` | Behavior |
| --- | --- |
| Omitted | Device-history legacy behavior is preserved. |
| Equals current device zone | Data is filtered by `device_id + stored zone_id`. |
| Not current device zone | Request is rejected with `IOT_DEVICE_ZONE_MISMATCH`. |
| Device has no current zone and `zoneId` is supplied | Request is rejected with `IOT_DEVICE_ZONE_REQUIRED`. |

## Data Source

| Data | Filter |
| --- | --- |
| Latest readings | `sensor_latest_readings.device_id + zone_id` |
| Device chart | aggregate `device_id + zone_id + sensor_type_id` |
| Media/diagnosis | `device_media_events.device_id + zone_id`; diagnosis follows the media event |

## Why

`DeviceDetailPage` is moving to a current-zone default. Without these filters, device-scoped endpoints can return old telemetry, aggregate points, and media/diagnosis from a previous zone after the device is relocated.

## Backward Compatibility

Existing callers that omit `zoneId` keep the previous device-history behavior:

```http
GET /iot/devices/{deviceId}/latest-readings
GET /iot/devices/{deviceId}/charts?sensorCode=...&range=...
GET /iot/devices/{deviceId}/media
```

## Next Phase

Frontend Phase B should:

- pass `device.zoneId` into device latest/chart/media hooks,
- include `zoneId` in React Query keys,
- keep config, schedules, capture, and push-config device-scoped,
- avoid falling back to device-history embedded data when rendering current-zone sections.
