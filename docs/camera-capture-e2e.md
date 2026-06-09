# Camera Capture E2E Test Framework

This document describes the Phase C5 camera capture test harness. It validates the camera path without rebuilding ESP32 firmware and without touching telemetry, config ACK, or alert flows.

## Scope

Covered paths:

- Manual capture: frontend or API command -> collector `DeviceMediaEvent` -> MQTT `camera/capture` command -> simulated firmware `image/meta` -> collector media update.
- Scheduled capture: collector schedule `run-now` -> MQTT `camera/capture` command with `triggerType=SCHEDULED` -> simulated firmware `image/meta` -> schedule `lastRunAt` / `nextRunAt` and media update.
- Failure metadata: simulated upload failure -> collector marks the media event `FAILED`.
- Idempotency: repeated `image/meta` for the same `requestId` updates one existing media event and does not create duplicate media rows.
- Test Data Service smoke: C3 manual and scheduled camera simulation endpoints return correctly shaped responses and publish the camera MQTT topics.

Out of scope for the automated script:

- ESP32 firmware retry timing. Firmware cannot be rebuilt in Phase C5, so retry validation remains a serial-log check on hardware or a firmware simulation run.
- Browser automation against a live frontend. Frontend camera display is covered by React tests for `IotCameraSchedulesPage`; Device Detail can be manually checked against the media API results from this script.

## Prerequisites

- Collector running, default: `http://localhost:8091`.
- IoT Test Data Service running, default: `http://localhost:8099`.
- MQTT broker running, default: `localhost:1883`.
- `mosquitto_pub` and `mosquitto_sub` available in `PATH`.
- A claimed/active test device such as `leafy-prototype-001`, with its `deviceId` available.
- Collector subscribed to:
  - `coffee/prod/devices/+/image/meta`
  - outbound command namespace `coffee/prod/devices/{deviceUid}/camera/capture`

## Run the Script

From `Leafy_BE`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\camera-capture-e2e.ps1 `
  -CollectorBaseUrl http://localhost:8091 `
  -TestDataBaseUrl http://localhost:8099 `
  -DeviceId "00000000-0000-0000-0000-000000000000" `
  -DeviceUid "leafy-prototype-001" `
  -MqttHost localhost `
  -MqttPort 1883 `
  -MqttEnv prod `
  -IncludeFailure
```

If `DeviceId` is omitted, the script tries to resolve it from `GET /iot/devices/me` using `X-User-Id: iot-e2e-test`. Passing `DeviceId` is more reliable for local environments.

## What the Script Validates

Manual capture:

1. Subscribes once to `coffee/{env}/devices/{deviceUid}/camera/capture`.
2. Calls `POST /iot/devices/{deviceId}/camera/capture`.
3. Asserts MQTT command contains:
   - `requestId`
   - `deviceUid`
   - `triggerType=MANUAL`
   - `resolution`
   - `quality`
4. Publishes simulated firmware metadata to `coffee/{env}/devices/{deviceUid}/image/meta`.
5. Polls `GET /iot/devices/{deviceId}/media` until the matching event is `UPLOADED`.
6. Publishes the same metadata again and verifies only one media event exists for the same `requestId`.

Scheduled capture:

1. Creates a schedule with `POST /iot/camera-schedules`.
2. Subscribes once to `coffee/{env}/devices/{deviceUid}/camera/capture`.
3. Calls `POST /iot/camera-schedules/{scheduleId}/run-now`.
4. Asserts MQTT command contains `triggerType=SCHEDULED`.
5. Publishes simulated firmware metadata to `image/meta`.
6. Polls media until `UPLOADED`.
7. Verifies the schedule has non-null `lastRunAt` and `nextRunAt`.

Failure path:

1. Creates a manual capture request.
2. Publishes `image/meta` with `status=FAILURE`, `success=false`, and `errorMessage=SIMULATED_UPLOAD_FAILED`.
3. Polls media until `FAILED`.

Test Data Service smoke:

1. Calls `POST /seed/scenarios/camera-capture-manual`.
2. Calls `POST /seed/scenarios/camera-capture-scheduled?run-now=true`.
3. Verifies response `triggerType`, capture count, and metadata shape.

## Expected Logs

Collector:

```text
Published camera capture command topic=coffee/prod/devices/leafy-prototype-001/camera/capture
Handling image/meta deviceUid=leafy-prototype-001 requestId=...
Schedule acquired by collectorInstanceId=...
Scheduled camera capture requested. scheduleId=..., deviceUid=leafy-prototype-001
```

Firmware or simulated firmware:

```text
Queued SCHEDULED camera capture requestId=...
Capture start requestId=...
Upload start requestId=...
Capture metadata published requestId=... status=SUCCESS fileId=...
```

Frontend checks:

- Device Detail page should show the newest media event with status `UPLOADED` or `FAILED`, timestamp, dimensions, file id backed thumbnail, and error text for failures.
- Admin `/admin/iot-camera-schedules` should show `lastRunAt`, `nextRunAt`, last capture status, thumbnail, dimensions, and localized labels.

## Automated Frontend Coverage

Run:

```powershell
cd ..\Leafy_FE
npm test -- --run src/features/admin/iot-camera-schedules/IotCameraSchedulesPage.test.tsx
```

The test covers:

- Schedule list rendering.
- Run Now mutation and refresh.
- Last capture thumbnail through file-service presigned URL.
- Localized labels through existing i18n helpers.

## Notes

- The script does not upload real image bytes to file-service. It mocks the firmware result by publishing `fileId`, `contentType`, `sizeBytes`, `width`, and `height` to `image/meta`.
- For a real file-service smoke test, upload a small JPEG to `POST /files/upload`, take the returned file id, then pass it in the simulated metadata.
- Firmware retry remains validated by ESP32 logs because Phase C5 does not rebuild or execute firmware.
