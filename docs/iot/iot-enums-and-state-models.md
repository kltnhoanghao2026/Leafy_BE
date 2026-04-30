# IoT Enums And State Models

This document reflects enum classes and service transitions in `iot-metrics-collector-service`.

## Device Status

Enum: `DeviceStatus`

| Value | Meaning | Where it appears |
| --- | --- | --- |
| `ONLINE` | Device most recently reported online status. | `DeviceResponse.status`, `DeviceDetailResponse.status`, `GET /iot/devices/me?status=` |
| `OFFLINE` | Device is provisioned/claimed but currently offline. Provisioned devices start as `OFFLINE`. | Device list/detail and status ingest |
| `MAINTENANCE` | Reserved device status value. | API-visible as a filter/response value if stored |
| `ERROR` | Reserved device status value for device fault conditions. | API-visible as a filter/response value if stored |

Status ingest sets `ONLINE` when `StatusPayload.online` is true and `OFFLINE` otherwise. Telemetry ingest updates `lastSeenAt` and firmware version, but does not set `ONLINE` by itself.

## Provisioning Status

Enum: `ProvisioningStatus`

| Value | Meaning | Where it appears |
| --- | --- | --- |
| `PENDING` | Reserved initial state. Current provision API writes `PROVISIONED`. | Device list/detail filters and responses |
| `PROVISIONED` | Device exists and can receive a claim code. | `POST /iot/devices/provision` response |
| `CLAIMED` | Device is bound to an owner user, farm plot, and zone. Required for config update/push and telemetry evaluation. | Claim response, device list/detail |
| `DISABLED` | Device is disabled and cannot be claimed or configured. | Validation and API-visible state |

Claim flow:

1. `POST /iot/devices/provision` creates a device with `provisioningStatus=PROVISIONED`, `status=OFFLINE`, `isActive=true`.
2. `POST /iot/devices/{deviceId}/claim-code` creates or refreshes a `PENDING` claim code with a 15-minute TTL.
3. `POST /iot/devices/claim` validates the code and moves the device to `CLAIMED`.

## Claim Status

Enum: `ClaimStatus`

| Value | Meaning |
| --- | --- |
| `PENDING` | Claim code is active and can be used if not expired. |
| `CLAIMED` | Claim code has been used. |
| `EXPIRED` | Reserved status value. The service currently checks expiry by comparing `expiresAt` to current time. |
| `REVOKED` | Reserved status value. |

## Device Config Push Status

Enum: `DeviceConfigPushStatus`

| Value | Meaning | Where it appears |
| --- | --- | --- |
| `PENDING` | Config was updated locally and has not yet been pushed or acknowledged. | `DeviceConfigResponse.lastPushStatus` |
| `SENT` | Config was published to MQTT successfully. | `DeviceConfigResponse.lastPushStatus` |
| `ACKED` | Device sent a successful config ack with matching `configVersion`. | `DeviceConfigResponse.lastPushStatus` |
| `FAILED` | MQTT push failed or device sent an unsuccessful ack. | `DeviceConfigResponse.lastPushStatus` |

Config lifecycle:

1. `GET /iot/devices/{deviceId}/config` creates default config if missing: version `1`, `samplingIntervalSec=60`, `publishIntervalSec=300`, `offlineTimeoutSec=900`, `alertEnabled=true`.
2. `PUT /iot/devices/{deviceId}/config` validates intervals, increments `configVersion`, sets `lastPushStatus=PENDING`, clears `appliedAt`, `lastAckAt`, and `lastPushError`.
3. `POST /iot/devices/{deviceId}/config/push` publishes MQTT config to `coffee/{env}/devices/{deviceUid}/config/set` and sets `lastPushStatus=SENT`. On publish failure it sets `FAILED` and writes `lastPushError`.
4. MQTT ack on `coffee/{env}/devices/{deviceUid}/ack` updates config only when payload `type` is `config`, `config-applied`, or `config_ack`, the device exists, config exists, and `configVersion` matches.
5. Successful ack sets `lastPushStatus=ACKED`, `lastAckAt=ack time`, `appliedAt=ack time`, and clears `lastPushError`.
6. Failed ack sets `lastPushStatus=FAILED`, `lastAckAt=ack time`, and `lastPushError=payload.error`.

Config fields:

- `appliedAt`: when a matching successful device ack was processed.
- `lastAckAt`: when the last matching ack was processed, success or failure.
- `lastPushError`: publish exception message or failed ack error message.

## Alert Status

Enum used by `AlertEvent`: `AlertStatus`

| Value | Meaning | Transitions |
| --- | --- | --- |
| `OPEN` | Alert was generated and needs attention. | Can move to `ACKNOWLEDGED` or `RESOLVED`. |
| `ACKNOWLEDGED` | User/team has acknowledged the alert. | Can move to `RESOLVED`. |
| `RESOLVED` | Alert has been resolved. | No lifecycle endpoint moves it further. |
| `CLOSED` | Reserved status value in enum. | Not currently set by lifecycle endpoints. |

Lifecycle endpoint rules:

- `POST /iot/alert-events/{alertEventId}/acknowledge` only accepts `OPEN`.
- `POST /iot/alert-events/{alertEventId}/resolve` accepts `OPEN` or `ACKNOWLEDGED`.

`AlertEventStatus` also exists with values `OPEN`, `ACKNOWLEDGED`, `RESOLVED`, `IGNORED`, but the current `AlertEvent` entity and public alert APIs use `AlertStatus`.

## Alert Severity

Enum: `AlertSeverity`

| Value | Meaning | Where it appears |
| --- | --- | --- |
| `LOW` | Low severity alert/rule. | Alert events, alert rules |
| `MEDIUM` | Medium severity alert/rule. | Alert events, alert rules |
| `HIGH` | High severity alert/rule. | Alert events, alert rules |
| `CRITICAL` | Critical severity alert/rule. | Alert events, alert rules |

Alert rule create/update accepts case-insensitive text and normalizes through `AlertSeverity.valueOf(severity.trim().toUpperCase())`.

## Alert Generation Model

Alert evaluation runs during telemetry ingest. For each reading:

- Only enabled rules for the same `sensorTypeId` are considered.
- A rule applies when its optional device, zone, and farm plot scopes match the reading/device.
- `minThreshold` violation creates `alertType=THRESHOLD_LOW`.
- `maxThreshold` violation creates `alertType=THRESHOLD_HIGH`.
- New alerts start with `status=OPEN`, `pushSent=false`, and severity copied from the rule.
- `cooldownMinutes > 0` suppresses duplicate active alerts for the same rule, device, and sensor when an `OPEN` or `ACKNOWLEDGED` alert exists within the cooldown window.

## Chart Range Type

Enum: `ChartRangeType`

| Public range | Aliases | Lookback | Aggregate source |
| --- | --- | --- | --- |
| `H24` | `H24`, `24H` | 24 hours | `AGG_5M` |
| `D3` | `D3`, `3D` | 3 days | `AGG_5M` |
| `D7` | `D7`, `7D` | 7 days | `AGG_1H` |
| `D30` | `D30`, `30D` | 30 days | `AGG_1H` |
| `D90` | `D90`, `90D` | 90 days | `AGG_1D` |

Aggregate tables/classes:

- `AGG_5M`: `SensorReadingAgg5m`
- `AGG_1H`: `SensorReadingAgg1h`
- `AGG_1D`: `SensorReadingAgg1d`

The aggregate scheduler rebuilds 5-minute windows every 1 minute, 1-hour windows every 5 minutes, and 1-day windows every 1 hour.

## Reading Quality

Enum: `ReadingQualityStatus`

| Value | Meaning | Where it appears |
| --- | --- | --- |
| `GOOD` | Current telemetry ingest assigns this to accepted readings. | `LatestReadingItemResponse.qualityStatus` |
| `SUSPECT` | Reserved quality state. | API-visible if stored |
| `INVALID` | Reserved quality state. | API-visible if stored |
| `MISSING` | Reserved quality state. | API-visible if stored |

## Media And Trigger Enums

`DeviceMediaSummaryResponse` may expose:

- `MediaType`: `IMAGE`, `VIDEO`
- `TriggerType`: `MANUAL`, `ALERT`, `SCHEDULED`, `MOTION`

Current MQTT handler recognizes `coffee/{env}/devices/{deviceUid}/image/meta` and logs it, but full media ingestion is minimal in the current implementation.
