# IoT Device Relocation Data Audit

## 1. Executive Summary

When an IoT module is moved from zone A to zone B, the current codebase mixes two different concepts:

- Current assignment: `IoTDevice.farmPlot` and `IoTDevice.zone`.
- Event-time assignment: the zone captured on telemetry, media, and alert rows when the data was created.

The backend already snapshots `zone_id` for raw telemetry, aggregates, latest readings, media events, and alert events. Zone-level telemetry charts therefore should not move old zone A readings into zone B, because zone chart queries filter aggregate rows by stored `zone_id`.

The strongest confirmed issue is in the web zone metrics page: `ZoneDetailMetricsPage` finds the device currently assigned to the zone and then calls the device media API. That API returns media by `device_id`, so old image and diagnosis history from zone A can appear in zone B after the device is moved.

Latest readings are also relocation-sensitive. `sensor_latest_readings` is unique per `(device_id, sensor_type_id)`, so the latest value is a device-level cache with a stored zone snapshot. Device-level views will carry the latest value with the device. Zone overview currently queries latest readings by stored `latest.zone_id`, so it will not immediately appear in the new zone until new telemetry arrives, but there is no reset/stale policy when the device is moved.

There is no device assignment history table. Device update, claim, connect, and release mutate current assignment fields directly and do not record assignment intervals.

Affected areas:

| Area | Current finding | Relocation risk |
| --- | --- | --- |
| Zone charts | Query aggregate `zone_id` snapshots | Low in current code, unless old rows were built incorrectly |
| Device charts | Query by `device_id` | Expected to show full device history; product decision needed |
| Zone overview latest | Query `sensor_latest_readings.zone_id` | Old latest remains in old zone until new telemetry; no stale/reset policy |
| Device latest | Query by `device_id` | Carries old latest with device |
| Zone media/diagnosis on web | Web calls device media API for current zone device | High; confirmed likely source of old diagnosis in new zone |
| Backend media snapshots | `DeviceMediaEvent.zone` is set from current device zone at capture time | Good for event-time zone if zone-scoped API is used |
| Alert events | `AlertEvent.zone` is set from reading/media zone | Mostly safe for zone filters |
| Farm alert count | Repository counts by `alertEvent.device.farmPlot.id` | Risk: farm-level alert count can follow current device farm |

## 2. Current Device Relocation Flow

Relevant API/controller entry points are in `DeviceController`, with implementation in `DeviceServiceImpl`.

| API | Can change farm/zone? | Creates assignment history? | Resets latest? | Clears historical data? | Notes |
| --- | ---: | ---: | ---: | ---: | --- |
| `POST /iot/devices/connect` | Yes for new/provisioned device | No | No | No | Sets `IoTDevice.farmPlot` and `IoTDevice.zone`. Existing claimed device is idempotent only for same user + same farm/zone. |
| `POST /iot/devices/claim` | Yes | No | No | No | Sets current owner/farm/zone after claim-code validation. |
| `PATCH /iot/devices/{deviceId}` | Yes | No | No | No | Directly updates current farm/zone fields if request contains them. |
| `POST /iot/devices/{deviceId}/release` | Clears farm/zone | No | No | No | Sets owner/farm/zone to null and disables camera schedules. Latest/media/alerts/aggregates remain. |

There is no `iot_device_assignments` style table found in the collector module. Current assignment is represented only by mutable fields on `IoTDevice`.

## 3. Current Data Model

| Entity/Table | Has `device_id` | Has `zone_id` snapshot | Has `farm_plot_id` snapshot | Risk on relocation |
| --- | ---: | ---: | ---: | --- |
| `IoTDevice` | N/A | Current `zone` only | Current `farmPlot` only | High if used for historical queries |
| `SensorReadingSeries` | Yes | Yes | No | Good for zone history, but farm history must be derived elsewhere |
| `SensorLatestReading` | Yes | Yes | No | Medium; unique per device+sensor and no stale/reset policy |
| `SensorReadingAgg5m` | Yes | Yes | No | Low if built from raw reading zone snapshots |
| `SensorReadingAgg1h` | Yes | Yes | No | Low if built from lower aggregate zone snapshots |
| `SensorReadingAgg1d` | Yes | Yes | No | Low if built from lower aggregate zone snapshots |
| `DeviceMediaEvent` | Yes | Yes | No | Backend snapshot exists, but current FE zone page does not use zone media API |
| `DeviceMediaAnalysis` | Via `mediaEvent` | Via `mediaEvent` | No | Safe if queries go through media event zone; unsafe if queried by device only in zone context |
| `AlertEvent` | Yes | Yes | No | Zone-level safe; farm-level summary has current-device-farm risk |
| `DeviceConfig` | Device scoped | No | No | Configuration follows device, likely expected |
| `DeviceCameraSchedule` | Device scoped | No | No | Schedules follow device until release disables them |

`IoTDevice.zone` should be treated as current zone only. It is not a historical attribute and cannot answer "where was this data generated?" after relocation.

## 4. Telemetry Ingest Findings

Telemetry flow in `TelemetryIngestServiceImpl`:

```text
MQTT telemetry
-> load IoTDevice by deviceUid
-> require active, claimed, and zone-bound device
-> create SensorReadingSeries
-> set reading.zone = device.zone
-> save readings
-> update SensorLatestReading
-> evaluate alert rules
```

Findings:

- Raw telemetry stores a zone snapshot: `SensorReadingSeries.zone` is set from `device.getZone()` at ingest time.
- After the device is moved, new telemetry will be assigned to the new current zone.
- Old raw telemetry rows should keep the old zone, because there is no code that rewrites raw readings on device update.
- `SensorLatestReading` is updated by `AggregateLatestReadingServiceImpl` using `(device_id, sensor_type_id)` lookup and then `latest.zone = reading.zone`.
- Because latest readings are device+sensor unique, moving a device does not create a fresh latest row for the new assignment. It remains whatever the last telemetry sample wrote.
- Alert evaluation creates alert events with `alertEvent.zone = reading.zone`, so telemetry alerts snapshot the reading zone.

Important caveat: alert rule farm matching uses current device farm through the reading's device reference, not a raw `farm_plot_id` snapshot, because raw readings do not store farm plot. This is acceptable during immediate ingest, but it is not a robust historical model for re-evaluation/backfill after relocation.

## 5. Aggregate/Chart Findings

Aggregate flow in `AggregateServiceImpl`:

- 5-minute aggregates are built from `SensorReadingSeries`.
- 1-hour aggregates are built from 5-minute aggregates.
- 1-day aggregates are built from 1-hour aggregates.
- Aggregation keys include `device`, `zone`, and `sensorType`.
- Aggregate rows store `zone` from the source reading/aggregate, not from current `IoTDevice.zone`.

Query findings in `TelemetryQueryServiceImpl` and aggregate repositories:

| Endpoint | Query scope | Query source | Relocation behavior |
| --- | --- | --- | --- |
| `GET /iot/devices/{deviceId}/latest-readings` | Device | `SensorLatestReading.device_id` | Shows current latest cache for the device, regardless of zone history |
| `GET /iot/farm-zones/{zoneId}/overview` | Zone | `SensorLatestReading.zone_id` | Shows latest rows whose stored zone is the requested zone |
| `GET /iot/devices/{deviceId}/charts` | Device | aggregate `device_id` | Shows whole device chart history |
| `GET /iot/farm-zones/{zoneId}/charts` | Zone | aggregate `zone_id` | Shows data generated in that zone if aggregates were built from snapshots |

Conclusion: current backend zone chart implementation is mostly correct for event-time zone semantics. If old zone A telemetry appears in a zone B chart, the likely causes are:

- the UI is actually using a device-level chart in a zone context,
- existing historical aggregate rows were created incorrectly before snapshot logic existed,
- data was ingested after the device had already been moved to zone B,
- or a cache/UI composition issue is presenting device history as zone history.

The current code does not show a zone chart query that joins current `IoTDevice.zone`.

## 6. Media/Diagnosis Findings

Backend media flow:

- `DeviceMediaServiceImpl.requestCapture` creates `DeviceMediaEvent`.
- It sets `event.device = device` and `event.zone = device.zone` at capture request time.
- `handleImageMeta` updates the existing media event by request id and does not change its zone.
- `DeviceMediaAnalysis` is attached to `DeviceMediaEvent`; it has no independent zone/farm fields.
- `ImageDiseaseAlertServiceImpl` creates disease alert events with `alertEvent.zone = mediaEvent.zone`.

Backend query findings:

| Endpoint/API | Query | Relocation risk |
| --- | --- | --- |
| Zone latest media in `DashboardQueryServiceImpl.getZoneOverview` | `DeviceMediaEventRepository.findTopByZoneIdOrderByCapturedAtDesc(zoneId)` | Low; uses media event zone snapshot |
| Device detail latest media | `findTopByDeviceIdOrderByCapturedAtDesc(deviceId)` | Expected device history |
| Device media list | `findTop20ByDeviceIdOrderByRequestedAtDesc(deviceId)` | High if used inside a zone page |

Confirmed frontend issue:

- `ZoneDetailMetricsPage` loads devices currently in the zone with `useMyDevices({ zoneId })`.
- It chooses one current zone device as `zoneDevice`.
- It then calls `useDeviceMedia(zoneDeviceId, ...)`.
- That returns device media history by `device_id`, not media history by zone snapshot.

This is the most direct explanation for old image diagnosis from zone A appearing in zone B after the module is moved.

## 7. Alert Findings

Alert event creation:

- Telemetry alerts use `AlertEvaluationServiceImpl` and set `AlertEvent.zone` from `SensorReadingSeries.zone`.
- Disease/image alerts use `ImageDiseaseAlertServiceImpl` and set `AlertEvent.zone` from `DeviceMediaEvent.zone`.

Alert query behavior:

| Query | Uses event-time zone? | Notes |
| --- | ---: | --- |
| Zone alert summary | Yes | `AlertEventRepository.countByZoneIdAndStatus` filters `alertEvent.zone.id`. |
| Zone alert list | Yes | `AlertQueryServiceImpl` filters `root.get("zone").get("id")`. |
| Device alert summary | Device scoped | Expected to follow device history/current device detail. |
| Farm overview open alert count | No | `countByFarmPlotIdAndStatus` filters `alertEvent.device.farmPlot.id`, which is current device farm. |

Zone-level alert behavior is mostly correct. Farm-level alert counts can be wrong after relocation because the farm filter is based on the device's current farm, not an alert-time farm snapshot.

## 8. Frontend Findings

| Page/Component | API/hook | Scope currently used | Risk |
| --- | --- | --- | --- |
| `ZoneDetailMetricsPage` zone overview | `useZoneOverview(zoneId)` | Zone snapshot backend endpoint | Low |
| `ZoneDetailMetricsPage` zone chart | `useZoneChart(zoneId, sensor, range)` | Zone aggregate endpoint | Low |
| `ZoneDetailMetricsPage` alerts | `useAlertEvents({ zoneId })` | Zone alert snapshot | Low |
| `ZoneDetailMetricsPage` camera/media panel | `useDeviceMedia(zoneDeviceId)` | Device history for current zone device | High |
| `DeviceDetailPage` latest/chart/media | `useDeviceLatestReadings`, `useDeviceChart`, `useDeviceMedia` | Device history/current latest | Expected, but product decision needed |

React Query/cache notes:

- Device update mutations invalidate broad device and metrics query keys, so stale frontend cache is less likely to be the primary issue.
- Invalidation is broad, not old-zone/new-zone specific, but `metricsKeys.all()` should refetch zone overview/charts.
- Media queries are keyed by device id, so after relocation the same device media cache is still valid for device history. It becomes incorrect only when reused as zone media history.

Mobile was not deeply audited in this pass. Needs verification if mobile zone screens also display device-scoped media/history in a zone context.

## 9. Root Cause

Confirmed root causes and risks:

1. No assignment history exists. Current device farm/zone is overwritten directly, so the system cannot answer assignment-interval questions.
2. Web zone media/diagnosis uses device media history for the device currently assigned to the zone. This causes old media/diagnosis from previous zones to appear in the new zone.
3. Latest readings are device+sensor unique and not reset/staled on relocation. This can create confusing behavior: device detail carries latest values; zone overview waits for rows whose stored latest zone matches the zone; there is no explicit "new zone has no fresh sample yet" state.
4. Farm-level alert summary counts use the device's current farm through `alertEvent.device.farmPlot`, so old alerts can be counted under the new farm after relocation.
5. Device-level endpoints intentionally show device history. If product expects zone pages to show only zone history, frontend must not compose zone pages from device-history endpoints.

Not confirmed as current root causes:

- Zone chart backend query joining current device zone. Current repositories filter aggregate `zone_id`.
- Aggregate rebuild rewriting old data into a new zone. Current aggregation groups by stored source zone.

## 10. Recommended Fix Plan

### Phase 1 - Quick Correctness Patch

- Add a zone-scoped media API or reuse an existing zone media query so `ZoneDetailMetricsPage` does not call `useDeviceMedia(zoneDeviceId)` for zone media history.
- Keep device detail media as device-history scoped.
- Add a relocation notice in zone/device UI: after move, latest values for the new zone appear only after the device sends new telemetry.
- On device zone/farm update, invalidate old and new zone queries explicitly if the web client knows both values.

### Phase 2 - Latest Reading Relocation Policy

- Decide and implement one latest policy:
  - delete latest readings for the device when assignment changes,
  - mark latest readings stale with a `staleReason = DEVICE_RELOCATED`,
  - or keep old latest but show its source zone and reading time clearly.
- If zone overview must not show stale old values, reset latest on `PATCH /iot/devices/{deviceId}`, `/release`, and possibly successful claim/connect after release.

### Phase 3 - Snapshot Media/Alert/Farm Data

- Keep using `zone_id` snapshots for media and alerts.
- Consider adding `farm_plot_id` snapshots to `SensorReadingSeries`, `SensorLatestReading`, aggregate tables, `DeviceMediaEvent`, and `AlertEvent`.
- Fix farm-level alert counts to use event-time farm snapshot once available. Until then, document that farm alert summary has current-assignment risk.

### Phase 4 - Device Assignment History

- Add `iot_device_assignments`:
  - `id`
  - `device_id`
  - `owner_user_id`
  - `farm_plot_id`
  - `zone_id`
  - `assigned_from`
  - `assigned_to`
  - `created_at`
  - `reason`
- Record rows on connect, claim, update, and release.
- Use assignment intervals for historical queries where zone/farm snapshots are missing or need backfill.

### Phase 5 - Aggregate Query/Backfill

- Verify all existing raw and aggregate rows have correct `zone_id`.
- Backfill missing/incorrect zone snapshots using assignment history if available.
- Rebuild aggregates after correcting historical data.
- Decide whether old rows without reliable assignment should be marked unknown rather than assigned to current zone.

### Phase 6 - FE UX Cleanup

- Make scope explicit:
  - Device detail: "device history".
  - Zone pages: "zone history".
- Avoid using device-level media/chart APIs in zone-history panels.
- Add "device recently moved" hint if current zone has no fresh latest telemetry yet.

## 11. Tests Needed

Backend:

- Updating device zone does not move old zone chart data.
- New telemetry after relocation appears in the new zone.
- Latest readings are reset/staled or explicitly retained according to the chosen policy.
- Media before relocation remains associated with old zone.
- Media after relocation is associated with new zone.
- Alert before relocation remains associated with old zone.
- Alert after relocation is associated with new zone.
- Farm overview alert counts do not move old alerts into the new farm after adding farm snapshots.
- Release disables schedules and applies the selected latest policy.

Frontend:

- Zone page media panel does not show media captured in another zone.
- Device detail still shows full device media history if that remains intended.
- Zone chart uses zone endpoint, not device chart endpoint.
- Zone media/cache invalidates correctly after device relocation.
- UI shows a clear empty/stale state when a newly moved device has not produced telemetry in the new zone.

## 12. Open Decisions

- Should device detail show all device history, or only current assignment history by default?
- Should zone overview show no latest value after relocation until a new sample arrives, or show the stale device latest with a warning?
- Is existing historical data important enough to backfill, or can old unreliable rows be marked unknown?
- Should farm-level reporting be strict event-time farm, current farm, or both?
- Should assignment history be implemented now, or is zone/farm snapshot on each event table enough for the first production fix?
- Should camera schedules follow a device into a new zone, or require explicit reconfiguration after relocation?
- Should mobile mirror the web distinction between device history and zone history?
