# IoT API Inventory

Source of truth inspected: controllers and DTOs in `iot-metrics-collector-service` and `iot-test-data-service`.

Local/dev defaults:

- Collector base URL: `http://localhost:8091`
- Test-data base URL: `http://localhost:8099`
- Current simple user identity header where required: `X-User-Id: <uuid>`
- Collector business errors handled by `TelemetryQueryExceptionHandler` return `{ "code": number, "message": string }`

## iot-metrics-collector-service

### Device Onboarding And Ownership

| Method | Path | Purpose | Key input | Response |
| --- | --- | --- | --- | --- |
| `POST` | `/iot/devices/provision` | Provision an unclaimed device record. | Body: `ProvisionDeviceRequest` | `DeviceResponse` |
| `POST` | `/iot/devices/{deviceId}/claim-code` | Generate a temporary claim code for a provisioned device. | Path: `deviceId` | `GenerateClaimCodeResponse` |
| `POST` | `/iot/devices/claim` | Claim/bind a device to the caller, farm plot, and zone. | Header `X-User-Id`; body: `ClaimDeviceRequest` | `DeviceResponse` |
| `GET` | `/iot/devices/me` | List caller-owned devices with pagination, sorting, filtering, and keyword search. | Header `X-User-Id`; query: `page`, `size`, `sortBy`, `sortDir`, `status`, `provisioningStatus`, `zoneId`, `farmPlotId`, `keyword` | `PagedResponse<DeviceResponse>` |
| `GET` | `/iot/devices/{deviceId}/config` | Read config for a device. Creates default config if missing. | Path: `deviceId` | `DeviceConfigResponse` |
| `PUT` | `/iot/devices/{deviceId}/config` | Update mutable config fields and increment `configVersion`. | Path: `deviceId`; body: `UpdateDeviceConfigRequest` | `DeviceConfigResponse` |
| `POST` | `/iot/devices/{deviceId}/config/push` | Publish current config to MQTT topic `coffee/{env}/devices/{deviceUid}/config/set`. | Path: `deviceId` | `DeviceConfigResponse` |

`GET /iot/devices/me` defaults:

- `page=0`
- `size=20`, `size > 100` is clamped to `100`, `size <= 0` falls back to `20`
- `sortBy=createdAt`
- `sortDir=desc`
- Allowed `sortBy`: `createdAt`, `lastSeenAt`, `deviceName`, `status`
- Allowed `sortDir`: `asc`, `desc`

### Telemetry Ingest And Monitoring

| Method | Path | Purpose | Key input | Response |
| --- | --- | --- | --- | --- |
| `POST` | `/iot/devices/{deviceUid}/telemetry` | HTTP telemetry ingest fallback. MQTT is the preferred runtime path. | Path: `deviceUid`; body: `TelemetryPayload` | `202 Accepted`, empty body |
| `POST` | `/iot/devices/{deviceUid}/status` | HTTP status ingest fallback. MQTT is the preferred runtime path. | Path: `deviceUid`; body: `StatusPayload` | `202 Accepted`, empty body |
| `GET` | `/iot/devices/{deviceId}/latest-readings` | Read latest sensor snapshots for one device. | Path: `deviceId` | `List<LatestReadingItemResponse>` |
| `GET` | `/iot/devices/{deviceId}/charts` | Read device chart data for one sensor and range. | Path: `deviceId`; query: `sensorCode`, `range` | `SensorChartResponse` |
| `GET` | `/iot/farm-zones/{zoneId}/charts` | Read zone-level chart data for one sensor and range. | Path: `zoneId`; query: `sensorCode`, `range` | `SensorChartResponse` |
| `GET` | `/iot/farm-zones/{zoneId}/overview` | Read zone overview: alerts, latest media, latest readings. | Path: `zoneId` | `ZoneOverviewResponse` |
| `GET` | `/iot/dashboard/overview` | Read farm plot dashboard overview counters. | Query: `farmPlotId` | `DashboardOverviewResponse` |
| `GET` | `/iot/devices/{deviceId}/detail` | Read device detail aggregate: device identity, alerts, config, media, latest readings. | Path: `deviceId` | `DeviceDetailResponse` |

Chart `range` values accepted by `ChartRangeType.fromValue`: `H24` or `24H`, `D3` or `3D`, `D7` or `7D`, `D30` or `30D`, `D90` or `90D`.

### Alert Events

| Method | Path | Purpose | Key input | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/iot/alert-events` | Search alert events with optional filters, pagination, and sorting. | Query: `zoneId`, `deviceId`, `status`, `severity`, `from`, `to`, `page`, `size`, `sortBy`, `sortDir` | `PagedResponse<AlertEventItemResponse>` |
| `GET` | `/iot/alert-events/{alertEventId}` | Read alert detail. | Path: `alertEventId` | `AlertEventDetailResponse` |
| `POST` | `/iot/alert-events/{alertEventId}/acknowledge` | Move an open alert to `ACKNOWLEDGED`. | Path: `alertEventId` | `AlertEventDetailResponse` |
| `POST` | `/iot/alert-events/{alertEventId}/resolve` | Move an open or acknowledged alert to `RESOLVED`. | Path: `alertEventId` | `AlertEventDetailResponse` |

`GET /iot/alert-events` defaults:

- `page=0`
- `size=20`, `size > 100` is clamped to `100`, `size <= 0` falls back to `20`
- `sortBy=openedAt`
- `sortDir=desc`
- Allowed `sortBy`: `openedAt`, `severity`, `status`
- Allowed `sortDir`: `asc`, `desc`
- `from` and `to` use ISO date-time and must satisfy `from < to` when both are present.

### Alert Rule Management

| Method | Path | Purpose | Key input | Response |
| --- | --- | --- | --- | --- |
| `POST` | `/iot/alert-rules` | Create a caller-owned alert rule. | Header `X-User-Id`; body: `CreateAlertRuleRequest` | `AlertRuleResponse` |
| `GET` | `/iot/alert-rules` | List caller-owned alert rules with filters, pagination, and sorting. | Header `X-User-Id`; query: `sensorTypeId`, `deviceId`, `zoneId`, `farmPlotId`, `enabled`, `page`, `size`, `sortBy`, `sortDir` | `PagedResponse<AlertRuleResponse>` |
| `GET` | `/iot/alert-rules/{ruleId}` | Read one caller-owned alert rule. | Header `X-User-Id`; path: `ruleId` | `AlertRuleResponse` |
| `PUT` | `/iot/alert-rules/{ruleId}` | Fully update mutable business fields for one caller-owned rule. | Header `X-User-Id`; path: `ruleId`; body: `UpdateAlertRuleRequest` | `AlertRuleResponse` |
| `PATCH` | `/iot/alert-rules/{ruleId}/enabled` | Toggle only `enabled`. | Header `X-User-Id`; path: `ruleId`; body: `UpdateAlertRuleEnabledRequest` | `AlertRuleResponse` |
| `DELETE` | `/iot/alert-rules/{ruleId}` | Hard delete a caller-owned rule. Existing alert events have their rule reference cleared first. | Header `X-User-Id`; path: `ruleId` | `204 No Content` |

`GET /iot/alert-rules` defaults:

- `page=0`
- `size=20`, `size > 100` is clamped to `100`, `size <= 0` falls back to `20`
- `sortBy=updatedAt`
- `sortDir=desc`
- Allowed `sortBy`: `updatedAt`, `createdAt`, `severity`, `enabled`
- Allowed `sortDir`: `asc`, `desc`

Rule validation:

- `sensorTypeId` is required and must exist.
- At least one of `minThreshold` or `maxThreshold` is required.
- If both thresholds are present, `minThreshold < maxThreshold`.
- At least one of `deviceId`, `zoneId`, or `farmPlotId` is required.
- If `deviceId` is provided, it must exist.
- `severity` must be `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL`.
- `cooldownMinutes` may be null, otherwise must be `>= 0`.
- `notifyWeb` and `notifyMobile` default to `false` on create/update if omitted.
- `enabled` defaults to `true` on create/update if omitted.

## iot-test-data-service

This service is non-production tooling only. It refuses to start with the `prod` profile and is intended for `local`, `dev`, or `staging`.

### Bootstrap

| Method | Path | Purpose | Request body | Response |
| --- | --- | --- | --- | --- |
| `POST` | `/seed/bootstrap/minimal` | Seed minimal reference data, onboard 2 devices through collector APIs, and create alert rules through collector APIs. | Optional `BootstrapRequest` | `BootstrapResponse` |
| `POST` | `/seed/bootstrap/full` | Seed richer reference data, onboard 6 devices through collector APIs, and create broader alert rules. | Optional `BootstrapRequest` | `BootstrapResponse` |

`BootstrapRequest` fields are currently accepted but not used by the bootstrap service implementation: `includeHistoricalTelemetry`, `historicalDays`, `readingsPerHour`.

### Historical Data

| Method | Path | Purpose | Request body | Response |
| --- | --- | --- | --- | --- |
| `POST` | `/seed/history/last-7d` | Publish historical telemetry/status for claimed devices across a 7-day default window. | Optional `HistorySeedRequest` | `HistorySeedResponse` |
| `POST` | `/seed/history/last-30d` | Publish historical telemetry/status for claimed devices across a 30-day default window. | Optional `HistorySeedRequest` | `HistorySeedResponse` |

`HistorySeedRequest`: `userId`, `farmPlotId`, `zoneId`, `days`, `readingsPerHour`, `includeAnomalies`.

### Live Simulation

| Method | Path | Purpose | Request body | Response |
| --- | --- | --- | --- | --- |
| `POST` | `/seed/simulation/start` | Start an in-memory live MQTT telemetry/status simulation. If already running, returns current status. | Optional `SimulationStartRequest` | `SimulationStatusResponse` |
| `POST` | `/seed/simulation/stop` | Stop live simulation if running. | None | `OperationResponse` |
| `GET` | `/seed/simulation/status` | Read current simulation state. | None | `SimulationStatusResponse` |

Default intervals are configuration-driven and currently default to telemetry every `60s`, status every `30s`, anomalies enabled.

### Scenarios

| Method | Path | Purpose | Request body | Response |
| --- | --- | --- | --- | --- |
| `POST` | `/seed/scenarios/high-temperature` | Publish high-temperature telemetry samples for a device. | `ScenarioRequest` | `ScenarioTriggerResponse` |
| `POST` | `/seed/scenarios/low-soil-moisture` | Publish low-soil-moisture telemetry samples for a device. | `ScenarioRequest` | `ScenarioTriggerResponse` |
| `POST` | `/seed/scenarios/config-ack-success` | Publish a successful config ack MQTT payload. | `ConfigAckScenarioRequest` | `ConfigAckScenarioResponse` |
| `POST` | `/seed/scenarios/config-ack-failure` | Publish a failed config ack MQTT payload. | `ConfigAckScenarioRequest` | `ConfigAckScenarioResponse` |

`ScenarioRequest`: `deviceUid` required, `count`, `durationMinutes`, `targetValue`.

`ConfigAckScenarioRequest`: `deviceUid` required, optional `configVersion`, optional `errorMessage` for failure.
