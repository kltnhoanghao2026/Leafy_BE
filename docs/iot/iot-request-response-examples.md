# IoT Request And Response Examples

Examples use local/dev URLs and placeholder UUIDs. Replace IDs with values from your environment.

Collector endpoints return raw DTOs. Collector business errors return `{ "code": number, "message": string }`.

Common `PagedResponse<T>` shape:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0,
  "hasNext": false,
  "hasPrevious": false
}
```

## Collector Service

### 1. `POST /iot/devices/provision`

Request:

```http
POST http://localhost:8091/iot/devices/provision
Content-Type: application/json
```

```json
{
  "deviceUid": "prod-demo-device-1",
  "deviceCode": "DEMO-001",
  "deviceName": "Demo Zone Sensor Hub",
  "deviceType": "ESP32"
}
```

Response:

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "deviceUid": "prod-demo-device-1",
  "deviceCode": "DEMO-001",
  "deviceName": "Demo Zone Sensor Hub",
  "deviceType": "ESP32",
  "firmwareVersion": null,
  "isActive": true,
  "status": "OFFLINE",
  "provisioningStatus": "PROVISIONED",
  "ownerUserId": null,
  "farmPlotId": null,
  "zoneId": null,
  "lastSeenAt": null
}
```

### 2. `POST /iot/devices/{deviceId}/claim-code`

Request:

```http
POST http://localhost:8091/iot/devices/11111111-1111-1111-1111-111111111111/claim-code
```

Response:

```json
{
  "deviceId": "11111111-1111-1111-1111-111111111111",
  "claimCode": "AB12CD34",
  "expiresAt": "2026-04-16T03:15:00Z"
}
```

Claim code TTL is 15 minutes.

### 3. `POST /iot/devices/claim`

Request:

```http
POST http://localhost:8091/iot/devices/claim
Content-Type: application/json
X-User-Id: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
```

```json
{
  "deviceUid": "prod-demo-device-1",
  "claimCode": "AB12CD34",
  "farmPlotId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "zoneId": "cccccccc-cccc-cccc-cccc-cccccccccccc"
}
```

Response:

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "deviceUid": "prod-demo-device-1",
  "deviceCode": "DEMO-001",
  "deviceName": "Demo Zone Sensor Hub",
  "deviceType": "ESP32",
  "firmwareVersion": null,
  "isActive": true,
  "status": "OFFLINE",
  "provisioningStatus": "CLAIMED",
  "ownerUserId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "farmPlotId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "zoneId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
  "lastSeenAt": null
}
```

### 4. `GET /iot/devices/me`

Request:

```http
GET http://localhost:8091/iot/devices/me?page=0&size=20&sortBy=createdAt&sortDir=desc&status=ONLINE&keyword=demo
X-User-Id: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
```

Response:

```json
{
  "items": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "deviceUid": "prod-demo-device-1",
      "deviceCode": "DEMO-001",
      "deviceName": "Demo Zone Sensor Hub",
      "deviceType": "ESP32",
      "firmwareVersion": "seed-live-1.0",
      "isActive": true,
      "status": "ONLINE",
      "provisioningStatus": "CLAIMED",
      "ownerUserId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
      "farmPlotId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
      "zoneId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
      "lastSeenAt": "2026-04-16T03:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1,
  "hasNext": false,
  "hasPrevious": false
}
```

Defaults: `page=0`, `size=20`, `sortBy=createdAt`, `sortDir=desc`. Allowed sort fields: `createdAt`, `lastSeenAt`, `deviceName`, `status`.

### 5. `GET /iot/devices/{deviceId}/config`

Request:

```http
GET http://localhost:8091/iot/devices/11111111-1111-1111-1111-111111111111/config
```

Response:

```json
{
  "deviceId": "11111111-1111-1111-1111-111111111111",
  "configVersion": 1,
  "samplingIntervalSec": 60,
  "publishIntervalSec": 300,
  "offlineTimeoutSec": 900,
  "alertEnabled": true,
  "appliedAt": null,
  "lastPushStatus": null,
  "lastAckAt": null,
  "lastPushError": null
}
```

If no config exists, the service creates a default config.

### 6. `PUT /iot/devices/{deviceId}/config`

Request:

```http
PUT http://localhost:8091/iot/devices/11111111-1111-1111-1111-111111111111/config
Content-Type: application/json
```

```json
{
  "samplingIntervalSec": 60,
  "publishIntervalSec": 300,
  "offlineTimeoutSec": 900,
  "alertEnabled": true
}
```

Response:

```json
{
  "deviceId": "11111111-1111-1111-1111-111111111111",
  "configVersion": 2,
  "samplingIntervalSec": 60,
  "publishIntervalSec": 300,
  "offlineTimeoutSec": 900,
  "alertEnabled": true,
  "appliedAt": null,
  "lastPushStatus": "PENDING",
  "lastAckAt": null,
  "lastPushError": null
}
```

Validation: fields required; intervals positive; `publishIntervalSec >= samplingIntervalSec`; `offlineTimeoutSec > publishIntervalSec`.

### 7. `POST /iot/devices/{deviceId}/config/push`

Request:

```http
POST http://localhost:8091/iot/devices/11111111-1111-1111-1111-111111111111/config/push
```

Response:

```json
{
  "deviceId": "11111111-1111-1111-1111-111111111111",
  "configVersion": 2,
  "samplingIntervalSec": 60,
  "publishIntervalSec": 300,
  "offlineTimeoutSec": 900,
  "alertEnabled": true,
  "appliedAt": null,
  "lastPushStatus": "SENT",
  "lastAckAt": null,
  "lastPushError": null
}
```

### 8. `GET /iot/devices/{deviceId}/latest-readings`

Request:

```http
GET http://localhost:8091/iot/devices/11111111-1111-1111-1111-111111111111/latest-readings
```

Response:

```json
[
  {
    "sensorTypeId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
    "sensorCode": "AIR_TEMP",
    "sensorName": "Air Temperature",
    "unit": "C",
    "value": 28.4,
    "readingTime": "2026-04-16T03:00:00Z",
    "qualityStatus": "GOOD"
  }
]
```

### 9. `GET /iot/devices/{deviceId}/charts`

Request:

```http
GET http://localhost:8091/iot/devices/11111111-1111-1111-1111-111111111111/charts?sensorCode=AIR_TEMP&range=H24
```

Response:

```json
{
  "deviceId": "11111111-1111-1111-1111-111111111111",
  "zoneId": null,
  "sensorCode": "AIR_TEMP",
  "sensorName": "Air Temperature",
  "unit": "C",
  "rangeType": "H24",
  "points": [
    {
      "bucketStart": "2026-04-16T02:55:00Z",
      "bucketEnd": "2026-04-16T03:00:00Z",
      "avgValue": 28.2,
      "minValue": 27.9,
      "maxValue": 28.6,
      "sampleCount": 3
    }
  ]
}
```

Supported ranges: `H24`, `24H`, `D3`, `3D`, `D7`, `7D`, `D30`, `30D`, `D90`, `90D`.

### 10. `GET /iot/farm-zones/{zoneId}/overview`

Request:

```http
GET http://localhost:8091/iot/farm-zones/cccccccc-cccc-cccc-cccc-cccccccccccc/overview
```

Response:

```json
{
  "zoneId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
  "openAlerts": 1,
  "lastUpdatedAt": "2026-04-16T03:00:00Z",
  "alertSummary": {
    "openAlerts": 1,
    "highSeverityAlerts": 1,
    "criticalAlerts": 0,
    "latestAlertAt": "2026-04-16T02:59:00Z"
  },
  "latestMedia": null,
  "latestReadings": []
}
```

### 11. `GET /iot/dashboard/overview`

Request:

```http
GET http://localhost:8091/iot/dashboard/overview?farmPlotId=bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb
```

Response:

```json
{
  "farmPlotId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "totalDevices": 2,
  "onlineDevices": 2,
  "offlineDevices": 0,
  "totalZones": 2,
  "openAlerts": 1,
  "lastUpdatedAt": "2026-04-16T03:00:00Z"
}
```

### 12. `GET /iot/devices/{deviceId}/detail`

Request:

```http
GET http://localhost:8091/iot/devices/11111111-1111-1111-1111-111111111111/detail
```

Response:

```json
{
  "deviceId": "11111111-1111-1111-1111-111111111111",
  "deviceUid": "prod-demo-device-1",
  "deviceCode": "DEMO-001",
  "deviceName": "Demo Zone Sensor Hub",
  "deviceType": "ESP32",
  "firmwareVersion": "seed-live-1.0",
  "status": "ONLINE",
  "provisioningStatus": "CLAIMED",
  "isActive": true,
  "ownerUserId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "farmPlotId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "zoneId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
  "lastSeenAt": "2026-04-16T03:00:00Z",
  "alertSummary": {
    "openAlerts": 1,
    "highSeverityAlerts": 1,
    "criticalAlerts": 0,
    "latestAlertAt": "2026-04-16T02:59:00Z"
  },
  "config": {
    "configVersion": 2,
    "samplingIntervalSec": 60,
    "publishIntervalSec": 300,
    "offlineTimeoutSec": 900,
    "alertEnabled": true,
    "appliedAt": null
  },
  "latestMedia": null,
  "latestReadings": []
}
```

### 13. `GET /iot/alert-events`

Request:

```http
GET http://localhost:8091/iot/alert-events?status=OPEN&severity=HIGH&page=0&size=20&sortBy=openedAt&sortDir=desc
```

Response:

```json
{
  "items": [
    {
      "id": "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
      "deviceId": "11111111-1111-1111-1111-111111111111",
      "zoneId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
      "sensorTypeId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
      "alertRuleId": "ffffffff-ffff-ffff-ffff-ffffffffffff",
      "alertType": "THRESHOLD_HIGH",
      "message": "AIR_TEMP exceeded max threshold: 44.0 > 38.0",
      "severity": "HIGH",
      "status": "OPEN",
      "triggerValue": 44.0,
      "thresholdMin": null,
      "thresholdMax": 38.0,
      "openedAt": "2026-04-16T02:59:00Z",
      "acknowledgedAt": null,
      "resolvedAt": null,
      "pushSent": false
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1,
  "hasNext": false,
  "hasPrevious": false
}
```

Defaults: `page=0`, `size=20`, `sortBy=openedAt`, `sortDir=desc`. Allowed sort fields: `openedAt`, `severity`, `status`.

### 14. `POST /iot/alert-events/{alertEventId}/acknowledge`

Request:

```http
POST http://localhost:8091/iot/alert-events/eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee/acknowledge
```

Response:

```json
{
  "id": "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
  "deviceId": "11111111-1111-1111-1111-111111111111",
  "zoneId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
  "sensorTypeId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
  "alertRuleId": "ffffffff-ffff-ffff-ffff-ffffffffffff",
  "alertType": "THRESHOLD_HIGH",
  "message": "AIR_TEMP exceeded max threshold: 44.0 > 38.0",
  "severity": "HIGH",
  "status": "ACKNOWLEDGED",
  "triggerValue": 44.0,
  "thresholdMin": null,
  "thresholdMax": 38.0,
  "openedAt": "2026-04-16T02:59:00Z",
  "acknowledgedAt": "2026-04-16T03:02:00Z",
  "resolvedAt": null,
  "pushSent": false
}
```

Only `OPEN` alerts can be acknowledged.

### 15. `POST /iot/alert-events/{alertEventId}/resolve`

Request:

```http
POST http://localhost:8091/iot/alert-events/eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee/resolve
```

Response:

```json
{
  "id": "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
  "status": "RESOLVED",
  "resolvedAt": "2026-04-16T03:05:00Z",
  "severity": "HIGH",
  "message": "AIR_TEMP exceeded max threshold: 44.0 > 38.0",
  "deviceId": "11111111-1111-1111-1111-111111111111",
  "zoneId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
  "sensorTypeId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
  "alertRuleId": "ffffffff-ffff-ffff-ffff-ffffffffffff",
  "alertType": "THRESHOLD_HIGH",
  "triggerValue": 44.0,
  "thresholdMin": null,
  "thresholdMax": 38.0,
  "openedAt": "2026-04-16T02:59:00Z",
  "acknowledgedAt": "2026-04-16T03:02:00Z",
  "pushSent": false
}
```

Only `OPEN` or `ACKNOWLEDGED` alerts can be resolved.

### 16. `POST /iot/alert-rules`

Request:

```http
POST http://localhost:8091/iot/alert-rules
Content-Type: application/json
X-User-Id: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
```

```json
{
  "sensorTypeId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
  "deviceId": "11111111-1111-1111-1111-111111111111",
  "zoneId": null,
  "farmPlotId": null,
  "minThreshold": null,
  "maxThreshold": 38.0,
  "severity": "HIGH",
  "cooldownMinutes": 10,
  "notifyWeb": true,
  "notifyMobile": false,
  "enabled": true
}
```

Response:

```json
{
  "id": "ffffffff-ffff-ffff-ffff-ffffffffffff",
  "sensorTypeId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
  "deviceId": "11111111-1111-1111-1111-111111111111",
  "zoneId": null,
  "farmPlotId": null,
  "ownerUserId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "minThreshold": null,
  "maxThreshold": 38.0,
  "severity": "HIGH",
  "cooldownMinutes": 10,
  "notifyWeb": true,
  "notifyMobile": false,
  "enabled": true,
  "createdAt": "2026-04-16T03:00:00Z",
  "updatedAt": "2026-04-16T03:00:00Z"
}
```

### 17. `GET /iot/alert-rules`

Request:

```http
GET http://localhost:8091/iot/alert-rules?enabled=true&page=0&size=20&sortBy=updatedAt&sortDir=desc
X-User-Id: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
```

Response:

```json
{
  "items": [
    {
      "id": "ffffffff-ffff-ffff-ffff-ffffffffffff",
      "sensorTypeId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
      "deviceId": "11111111-1111-1111-1111-111111111111",
      "zoneId": null,
      "farmPlotId": null,
      "ownerUserId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
      "minThreshold": null,
      "maxThreshold": 38.0,
      "severity": "HIGH",
      "cooldownMinutes": 10,
      "notifyWeb": true,
      "notifyMobile": false,
      "enabled": true,
      "createdAt": "2026-04-16T03:00:00Z",
      "updatedAt": "2026-04-16T03:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1,
  "hasNext": false,
  "hasPrevious": false
}
```

Defaults: `page=0`, `size=20`, `sortBy=updatedAt`, `sortDir=desc`. Allowed sort fields: `updatedAt`, `createdAt`, `severity`, `enabled`.

### 18. `PUT /iot/alert-rules/{ruleId}`

Request:

```http
PUT http://localhost:8091/iot/alert-rules/ffffffff-ffff-ffff-ffff-ffffffffffff
Content-Type: application/json
X-User-Id: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
```

```json
{
  "sensorTypeId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
  "deviceId": "11111111-1111-1111-1111-111111111111",
  "zoneId": null,
  "farmPlotId": null,
  "minThreshold": null,
  "maxThreshold": 40.0,
  "severity": "CRITICAL",
  "cooldownMinutes": 15,
  "notifyWeb": true,
  "notifyMobile": true,
  "enabled": true
}
```

Response:

```json
{
  "id": "ffffffff-ffff-ffff-ffff-ffffffffffff",
  "sensorTypeId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
  "deviceId": "11111111-1111-1111-1111-111111111111",
  "zoneId": null,
  "farmPlotId": null,
  "ownerUserId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "minThreshold": null,
  "maxThreshold": 40.0,
  "severity": "CRITICAL",
  "cooldownMinutes": 15,
  "notifyWeb": true,
  "notifyMobile": true,
  "enabled": true,
  "createdAt": "2026-04-16T03:00:00Z",
  "updatedAt": "2026-04-16T03:10:00Z"
}
```

### 19. `PATCH /iot/alert-rules/{ruleId}/enabled`

Request:

```http
PATCH http://localhost:8091/iot/alert-rules/ffffffff-ffff-ffff-ffff-ffffffffffff/enabled
Content-Type: application/json
X-User-Id: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
```

```json
{
  "enabled": false
}
```

Response:

```json
{
  "id": "ffffffff-ffff-ffff-ffff-ffffffffffff",
  "enabled": false,
  "updatedAt": "2026-04-16T03:12:00Z",
  "sensorTypeId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
  "deviceId": "11111111-1111-1111-1111-111111111111",
  "zoneId": null,
  "farmPlotId": null,
  "ownerUserId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "minThreshold": null,
  "maxThreshold": 40.0,
  "severity": "CRITICAL",
  "cooldownMinutes": 15,
  "notifyWeb": true,
  "notifyMobile": true,
  "createdAt": "2026-04-16T03:00:00Z"
}
```

`enabled` must not be null.

## Test-Data Service

### 20. `POST /seed/bootstrap/minimal`

Request:

```http
POST http://localhost:8099/seed/bootstrap/minimal
```

Response:

```json
{
  "mode": "minimal",
  "createdUsers": 1,
  "createdFarmPlots": 1,
  "createdZones": 2,
  "createdSensorTypes": 4,
  "provisionedDevices": 2,
  "claimedDevices": 2,
  "createdAlertRules": 4,
  "warnings": []
}
```

On repeated runs, `created*`, `provisionedDevices`, `claimedDevices`, or `createdAlertRules` may be `0` when existing demo data is reused.

### 21. `POST /seed/history/last-7d`

Request:

```http
POST http://localhost:8099/seed/history/last-7d
Content-Type: application/json
```

```json
{
  "readingsPerHour": 2,
  "includeAnomalies": true
}
```

Response:

```json
{
  "devicesTargeted": 2,
  "telemetryMessagesPublished": 674,
  "statusMessagesPublished": 58,
  "anomaliesInjectedCount": 6,
  "from": "2026-04-09T03:00:00Z",
  "to": "2026-04-16T03:00:00Z",
  "warnings": []
}
```

Message counts vary with device count, window size, and `readingsPerHour`.

### 22. `POST /seed/simulation/start`

Request:

```http
POST http://localhost:8099/seed/simulation/start
Content-Type: application/json
```

```json
{
  "telemetryIntervalSeconds": 60,
  "statusIntervalSeconds": 30,
  "anomaliesEnabled": true
}
```

Response:

```json
{
  "running": true,
  "activeDeviceCount": 2,
  "telemetryIntervalSeconds": 60,
  "statusIntervalSeconds": 30,
  "anomaliesEnabled": true,
  "startedAt": "2026-04-16T03:00:00Z",
  "deviceUids": [
    "prod-minimal-device-1",
    "prod-minimal-device-2"
  ]
}
```

### 23. `GET /seed/simulation/status`

Request:

```http
GET http://localhost:8099/seed/simulation/status
```

Response when stopped:

```json
{
  "running": false,
  "activeDeviceCount": 0,
  "telemetryIntervalSeconds": 60,
  "statusIntervalSeconds": 30,
  "anomaliesEnabled": true,
  "startedAt": null,
  "deviceUids": []
}
```

### 24. `POST /seed/scenarios/high-temperature`

Request:

```http
POST http://localhost:8099/seed/scenarios/high-temperature
Content-Type: application/json
```

```json
{
  "deviceUid": "prod-minimal-device-1",
  "count": 5,
  "targetValue": 44.0
}
```

Response:

```json
{
  "scenario": "high-temperature",
  "deviceUid": "prod-minimal-device-1",
  "messagesPublished": 5,
  "targetValueUsed": 44.0,
  "startedAt": "2026-04-16T03:00:00Z",
  "warnings": []
}
```

If `count` is omitted, the service uses positive `durationMinutes`, otherwise defaults to `5` messages.

### 25. `POST /seed/scenarios/config-ack-success`

Request:

```http
POST http://localhost:8099/seed/scenarios/config-ack-success
Content-Type: application/json
```

```json
{
  "deviceUid": "prod-minimal-device-1",
  "configVersion": 2
}
```

Response:

```json
{
  "deviceUid": "prod-minimal-device-1",
  "configVersion": 2,
  "success": true,
  "topic": "coffee/prod/devices/prod-minimal-device-1/ack",
  "errorMessage": null
}
```

If `configVersion` is omitted, the test-data service fetches current device config from the collector first.
