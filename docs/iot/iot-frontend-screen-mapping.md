# IoT Frontend Screen Mapping

This mapping is based on the current collector and test-data service APIs. Endpoints shown with `X-User-Id` require the current user UUID in the request header.

## 1. Dashboard Page

Required calls:

| Purpose | API |
| --- | --- |
| Farm counters | `GET /iot/dashboard/overview?farmPlotId={farmPlotId}` |
| Optional alert center preview | `GET /iot/alert-events?status=OPEN&page=0&size=5&sortBy=openedAt&sortDir=desc` |

Display fields:

- `totalDevices`, `onlineDevices`, `offlineDevices`, `totalZones`, `openAlerts`, `lastUpdatedAt`
- Alert preview fields: `severity`, `message`, `openedAt`, `deviceId`, `zoneId`

Refresh:

- Farm overview: every 30 to 60 seconds while visible.
- Alert preview: every 15 to 30 seconds during demo/live simulation.

Known limitation:

- `GET /iot/dashboard/overview` is farm-plot scoped by query param and does not require `X-User-Id` in the current controller.

## 2. Zone Overview Page

Required calls:

| Purpose | API |
| --- | --- |
| Zone summary | `GET /iot/farm-zones/{zoneId}/overview` |
| Zone sensor chart | `GET /iot/farm-zones/{zoneId}/charts?sensorCode={sensorCode}&range={range}` |

Display fields:

- `openAlerts`, `lastUpdatedAt`, `alertSummary`
- `latestReadings[]`: `sensorCode`, `sensorName`, `unit`, `value`, `readingTime`, `qualityStatus`
- `latestMedia`: `mediaEventId`, `fileId`, `mediaType`, `triggerType`, `capturedAt` when present
- Chart `points[]`: `bucketStart`, `bucketEnd`, `avgValue`, `minValue`, `maxValue`, `sampleCount`

User actions:

- Range selector: call chart endpoint again with `range=H24`, `D3`, `D7`, `D30`, or `D90`.
- Sensor selector: call chart endpoint again with selected `sensorCode`.

Known limitation:

- `latestMedia` may be null because current image metadata handling is minimal.

## 3. Device Detail Page

Required calls:

| Purpose | API |
| --- | --- |
| Device aggregate detail | `GET /iot/devices/{deviceId}/detail` |
| Full config state | `GET /iot/devices/{deviceId}/config` |
| Latest readings, if more frequent refresh is needed | `GET /iot/devices/{deviceId}/latest-readings` |
| Device chart | `GET /iot/devices/{deviceId}/charts?sensorCode={sensorCode}&range={range}` |

Actions:

| User action | API |
| --- | --- |
| Save config form | `PUT /iot/devices/{deviceId}/config` |
| Push config to device | `POST /iot/devices/{deviceId}/config/push` |

Important fields:

- Device state: `status`, `provisioningStatus`, `isActive`, `lastSeenAt`, `firmwareVersion`
- Config state: `configVersion`, `samplingIntervalSec`, `publishIntervalSec`, `offlineTimeoutSec`, `alertEnabled`, `appliedAt`, `lastPushStatus`, `lastAckAt`, `lastPushError`
- Alert summary: `openAlerts`, `highSeverityAlerts`, `criticalAlerts`, `latestAlertAt`

UI validation for config:

- All config fields are required on `PUT`.
- `samplingIntervalSec > 0`
- `publishIntervalSec > 0`
- `offlineTimeoutSec > 0`
- `publishIntervalSec >= samplingIntervalSec`
- `offlineTimeoutSec > publishIntervalSec`
- `alertEnabled` must be boolean.

Refresh:

- Latest readings: every 15 to 30 seconds while visible.
- Config after push: refresh config shortly after push, then poll every 5 to 10 seconds for up to 60 seconds if waiting for `ACKED` or `FAILED`.
- Charts: load on initial page and on explicit range/sensor changes.

## 4. Alert Center Page

Required calls:

| Purpose | API |
| --- | --- |
| Alert list | `GET /iot/alert-events` |
| Alert detail | `GET /iot/alert-events/{alertEventId}` |

Supported filters:

- `zoneId`
- `deviceId`
- `status`: `OPEN`, `ACKNOWLEDGED`, `RESOLVED`, `CLOSED`
- `severity`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `from`, `to`: ISO date-time, with `from < to`
- Pagination: `page`, `size`
- Sorting: `sortBy=openedAt|severity|status`, `sortDir=asc|desc`

Actions:

| User action | API | Rule |
| --- | --- | --- |
| Acknowledge | `POST /iot/alert-events/{alertEventId}/acknowledge` | Only valid from `OPEN`. |
| Resolve | `POST /iot/alert-events/{alertEventId}/resolve` | Valid from `OPEN` or `ACKNOWLEDGED`. |

Refresh:

- Poll open alerts every 10 to 20 seconds during active demos.
- Poll every 30 to 60 seconds for normal dashboard usage.
- Refresh immediately after acknowledge/resolve.

## 5. Device Onboarding Page

Collector flow:

1. Internal/admin tooling provisions device: `POST /iot/devices/provision`.
2. Internal/admin tooling generates claim code: `POST /iot/devices/{deviceId}/claim-code`.
3. User claims device: `POST /iot/devices/claim` with `X-User-Id`.
4. Device appears in user inventory: `GET /iot/devices/me` with `X-User-Id`.

Required fields:

- Provision: `deviceUid`, `deviceCode`, `deviceName`, `deviceType`
- Claim: `deviceUid`, `claimCode`, `farmPlotId`, `zoneId`

Inventory query features:

- Filter by `status`, `provisioningStatus`, `zoneId`, `farmPlotId`
- Keyword search matches `deviceName`, `deviceCode`, or `deviceUid`
- Sort by `createdAt`, `lastSeenAt`, `deviceName`, or `status`

Known limitation:

- Current collector ownership style is the simple `X-User-Id` header, not JWT principal extraction inside these controllers.

## 6. Alert Rule Management Page

Required calls:

| Purpose | API |
| --- | --- |
| List rules | `GET /iot/alert-rules` with `X-User-Id` |
| Read rule | `GET /iot/alert-rules/{ruleId}` with `X-User-Id` |
| Create rule | `POST /iot/alert-rules` with `X-User-Id` |
| Update rule | `PUT /iot/alert-rules/{ruleId}` with `X-User-Id` |
| Enable/disable | `PATCH /iot/alert-rules/{ruleId}/enabled` with `X-User-Id` |
| Delete | `DELETE /iot/alert-rules/{ruleId}` with `X-User-Id` |

List filters:

- `sensorTypeId`, `deviceId`, `zoneId`, `farmPlotId`, `enabled`
- Pagination: `page`, `size`
- Sorting: `sortBy=updatedAt|createdAt|severity|enabled`, `sortDir=asc|desc`

Recommended UI validation:

- Require `sensorTypeId`.
- Require at least one scope: `deviceId`, `zoneId`, or `farmPlotId`.
- Require at least one threshold.
- If both thresholds exist, require `minThreshold < maxThreshold`.
- Require severity from `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
- If cooldown is present, require `cooldownMinutes >= 0`.
- Default unchecked notification booleans to `false`.
- Default enabled to `true` on create.

Known limitation:

- The rule API validates `deviceId` existence but does not do remote metadata validation for `zoneId` or `farmPlotId`.

## 7. Demo/Test-Data Operator Page

This page is optional and should be visible only in local/dev/staging tooling builds.

Bootstrap actions:

| Action | API |
| --- | --- |
| Minimal bootstrap | `POST /seed/bootstrap/minimal` |
| Full bootstrap | `POST /seed/bootstrap/full` |

History actions:

| Action | API |
| --- | --- |
| Seed 7 days | `POST /seed/history/last-7d` |
| Seed 30 days | `POST /seed/history/last-30d` |

Simulation actions:

| Action | API |
| --- | --- |
| Start simulation | `POST /seed/simulation/start` |
| Stop simulation | `POST /seed/simulation/stop` |
| Read status | `GET /seed/simulation/status` |

Scenario buttons:

| Scenario | API |
| --- | --- |
| High temperature | `POST /seed/scenarios/high-temperature` |
| Low soil moisture | `POST /seed/scenarios/low-soil-moisture` |
| Config ack success | `POST /seed/scenarios/config-ack-success` |
| Config ack failure | `POST /seed/scenarios/config-ack-failure` |

Suggested operator flow:

1. Run minimal or full bootstrap.
2. Seed 7-day or 30-day history.
3. Start simulation.
4. Trigger one anomaly.
5. Inspect dashboard, device detail, charts, and alert center.

Known limitation:

- Test-data service is intentionally not production safe and will refuse `prod` profile startup.
