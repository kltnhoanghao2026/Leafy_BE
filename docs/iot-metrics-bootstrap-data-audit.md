# IoT Metrics Bootstrap Data Audit

## 1. Executive Summary

- With the current module POM, `iot-metrics-collector-service` does not declare `flyway-core`; the SQL files under `src/main/resources/db/migration` are present, but Flyway activation for this module needs verification from the runtime classpath. The service config uses `spring.jpa.hibernate.ddl-auto: update`, so base tables are currently expected to be created/updated by Hibernate.
- If Flyway is active against a truly empty database before Hibernate creates entity tables, `V1__add_sensor_latest_readings_and_query_indexes.sql` will likely fail because it references pre-existing tables (`iot_devices`, `sensor_types`, `farm_zones`, `sensor_reading_agg_*`, `alert_events`) that V1 does not create.
- The only production bootstrap data clearly required by telemetry/chart/alert creation is `sensor_types` for the firmware metric codes. Telemetry ingest throws when a metric code has no matching `sensor_types.code`.
- Runtime flows create or update most state: devices, claims, device configs, user/farm/zone refs, readings, latest readings, aggregate rows, media events, media analysis rows, and camera schedules.
- Do not seed production claim codes, physical device ownership, user/farm/zone ownership, media events, alert events, raw telemetry, or aggregate rows.

## 2. IoT Database Tables

| Table | Entity | Repository | Migration tạo bảng | Vai trò | Runtime hay seed? |
| ----- | ------ | ---------- | ------------------ | ------- | ----------------- |
| `sensor_types` | `SensorType` | `SensorTypeRepository` | Hibernate DDL; no module SQL create | Sensor catalog used by telemetry, charts, alert rules | Must seed production for metric codes; camera disease type is auto-created |
| `iot_devices` | `IoTDevice` | `IoTDeviceRepository` | Hibernate DDL; V1 adds indexes only | Device identity/lifecycle | Runtime via provision/connect; optional dev seed only |
| `device_claims` | `DeviceClaim` | `DeviceClaimRepository` | Hibernate DDL | Claim code lifecycle | Runtime only |
| `device_configs` | `DeviceConfig` | `DeviceConfigRepository` | Hibernate DDL; V2 alters push tracking columns | Per-device config | Runtime auto-created on config get/update/push |
| `sensor_reading_series` | `SensorReadingSeries` | `SensorReadingSeriesRepository` | Hibernate DDL | Raw telemetry readings | Runtime ingest |
| `sensor_latest_readings` | `SensorLatestReading` | `SensorLatestReadingRepository` | V1 creates table | Latest per device/sensor | Runtime from telemetry ingest |
| `sensor_reading_agg_5m` | `SensorReadingAgg5m` | `SensorReadingAgg5mRepository` | Hibernate DDL; V1 adds indexes | 5 minute aggregates | Runtime scheduler |
| `sensor_reading_agg_1h` | `SensorReadingAgg1h` | `SensorReadingAgg1hRepository` | Hibernate DDL; V1 adds indexes | 1 hour aggregates | Runtime scheduler |
| `sensor_reading_agg_1d` | `SensorReadingAgg1d` | `SensorReadingAgg1dRepository` | Hibernate DDL; V1 adds indexes | 1 day aggregates | Runtime scheduler |
| `alert_rules` | `AlertRule` | `AlertRuleRepository` | Hibernate DDL | User alert thresholds | User/runtime; optional demo seed |
| `alert_events` | `AlertEvent` | `AlertEventRepository` | Hibernate DDL; V1 adds indexes | Actual alerts | Runtime only |
| `device_media_events` | `DeviceMediaEvent` | `DeviceMediaEventRepository` | Hibernate DDL; V3 alters capture fields/indexes | Camera capture requests/results | Runtime only |
| `device_media_analysis` | `DeviceMediaAnalysis` | `DeviceMediaAnalysisRepository` | V5 creates; V6 alters | Disease detection analysis jobs/results | Runtime only |
| `device_camera_schedules` | `DeviceCameraSchedule` | `DeviceCameraScheduleRepository` | V4 creates; V7 alters | Scheduled camera capture | User/runtime; optional demo seed |
| `users` | `UserRef` | `UserRefRepository` | Hibernate DDL | Local ref to external user | Runtime ensure from requests; do not production seed ownership |
| `farm_plots` | `FarmPlotRef` | `FarmPlotRefRepository` | Hibernate DDL | Local ref to external farm plot | Runtime ensure from requests; do not production seed ownership |
| `farm_zones` | `FarmZoneRef` | `FarmZoneRefRepository` | Hibernate DDL | Local ref to external farm zone | Runtime ensure from requests; do not production seed ownership |
| `files` | `FileRef` | via `EntityManager` in media service | Hibernate DDL | Local ref to uploaded file id | Runtime from image metadata |

## 3. Required Bootstrap Data

| Data | Why required | Current source | Missing behavior |
| ---- | ------------ | -------------- | ---------------- |
| `sensor_types` rows for firmware metric codes | `TelemetryIngestServiceImpl` maps every `payload.metrics` key by `sensorTypeRepository.findByCode(metricCode)` | Only iot-test-data-service seeds them; no production migration in this module | Ingest throws `EntityNotFoundException("Sensor type not found: ...")`; MQTT handler logs unexpected error and drops that message transaction |

No code path found that requires devices, configs, refs, alert rules, latest rows, aggregate rows, schedules, claims, or media rows to exist at app startup.

## 4. Runtime-created Data

| Data | Created by | Default value / behavior | Need seed? | Notes |
| ---- | ---------- | ------------------------ | ---------- | ----- |
| `iot_devices` | `DeviceServiceImpl.provisionDevice` / `connectDevice` | `PROVISIONED`, `OFFLINE`, `isActive=true`; connect claims owner/farm/zone | No | MQTT status/telemetry before device exists is ignored |
| `users` | `DeviceServiceImpl.ensureUserRef` | ID only | No | Created from `X-User-Id`/request flow |
| `farm_plots` | `DeviceServiceImpl.ensureFarmPlotRef` | ID only | No | Local ref only |
| `farm_zones` | `DeviceServiceImpl.ensureFarmZoneRef` | ID only | No | Telemetry requires claimed device with zone |
| `device_claims` | `generateClaimCode` | 8 char UUID-derived code, 15 minute TTL, `PENDING` | No | Release revokes pending claims |
| `device_configs` | `getDeviceConfig`, `updateDeviceConfig`, `pushConfig` | sampling 60s, publish 300s, offline 900s, alert enabled, version 1 | No | Ack without config is ignored with warning |
| `sensor_reading_series` | Telemetry ingest | One row per metric | No | Requires device, owner, zone, sensor type |
| `sensor_latest_readings` | `AggregateLatestReadingServiceImpl` | Upsert by device/sensor | No | Created after raw readings save |
| `sensor_reading_agg_*` | `AggregateScheduler` / `AggregateServiceImpl` | Rebuild from raw/previous aggregate windows | No | Empty source lists return without error |
| `alert_events` | Alert evaluation and image disease detection | `OPEN`, severity from rule/detection | No | Actual events must not be seeded |
| `files` | `DeviceMediaServiceImpl.ensureFileRef` | ID only | No | From uploaded image metadata |
| `device_media_events` | `requestCapture`, image meta flow | `REQUESTED` -> command/upload/fail states | No | Actual media state |
| `device_media_analysis` | `DeviceMediaAnalysisServiceImpl` | `PENDING` then processing/result | No | Actual analysis state |
| `device_camera_schedules` | Schedule API | Enabled default, next run computed | No | Optional demo only |
| `CAMERA_DISEASE_DETECTION` sensor type | `ImageDiseaseAlertServiceImpl` | Auto-created when first disease alert is created | No mandatory seed | Could be seeded for catalog completeness, but code handles absence |

## 5. Optional Dev/Demo Seed Data

| Data | Reason | Recommended source |
| ---- | ------ | ------------------ |
| Prototype/demo `iot_devices` | Useful for ESP32/demo device like `leafy-prototype-001` | Non-prod seed service or demo profile, not production migration |
| Demo `users`, `farm_plots`, `farm_zones` | Needed to make demo devices claimed and telemetry acceptable | Non-prod seed service after profile/farm services have known IDs |
| Sample alert rules | Demonstrates alert behavior | Non-prod seed API using real sensor type IDs and owned scope |
| Sample telemetry/latest/aggregates | Makes dashboards non-empty | Non-prod simulation/history seed |
| Demo camera schedules | Demonstrates scheduled capture | Non-prod seed only |

## 6. Data That Should Not Be Seeded

| Data | Reason |
| ---- | ------ |
| Production physical devices | Device identity/ownership is real operational data and depends on hardware provisioning |
| Production user/farm/zone ownership | Owned by auth/profile/farm services; local refs should be created/synced from real requests |
| Claim codes | Short-lived security/runtime state |
| Raw telemetry, latest readings, aggregates | Sensor measurements, generated by device ingest/scheduler |
| Alert events | Incident history, generated by evaluation/detection |
| Media events and media analysis | Actual file/capture/detection state |
| Production camera schedules | User/device-specific automation |

## 7. Sensor Type Analysis

Backend telemetry mapping is direct: each metric key in `TelemetryPayload.metrics` is looked up as `sensor_types.code`. There is no fallback, alias map, or bootstrap initializer in the collector. The same codes are used by the non-prod test-data service scenarios.

| code | name | unit | min_default | max_default | Required by | Seed hiện có? |
| ---- | ---- | ---- | ----------: | ----------: | ----------- | ------------- |
| `AIR_TEMP` | Air Temperature | `C` | 18 | 45 | Firmware/test telemetry, charts, alert rules | Only non-prod iot-test-data-service |
| `AIR_HUMIDITY` | Air Humidity | `%` | 35 | 95 | Firmware/test telemetry, charts, alert rules | Only non-prod iot-test-data-service |
| `SOIL_MOISTURE` | Soil Moisture | `%` | 10 | 85 | Firmware/test telemetry, charts, alert rules | Only non-prod iot-test-data-service |
| `LIGHT_INTENSITY` | Light Intensity | `lux` | 0 | 1200 | Firmware/test telemetry, charts | Only non-prod iot-test-data-service |
| `CAMERA_DISEASE_DETECTION` | Camera disease detection | `confidence` | null | null | Image disease alert events | Auto-created by collector at runtime |

If any required metric code is missing, the entire telemetry ingest transaction can fail for that MQTT/HTTP payload. Metrics before the missing key are not safely committed because the method is transactional.

## 8. Alert Rule Bootstrap Analysis

| Rule type | Có cần seed mặc định? | Lý do | Nên seed ở migration hay user tạo? |
| --------- | --------------------: | ----- | ---------------------------------- |
| User/device/zone/farm threshold rules | No | Telemetry persists without rules; evaluation simply returns when no enabled rule exists | User-created or non-prod seed |
| Global/system threshold rules | Not supported as true global by current create API | `AlertRuleServiceImpl.validateScope` requires device, zone, or farm plot scope and owner validation | Needs product decision before production seed |
| Camera disease alerts | No | Created from disease detection, independent from `alert_rules` | Runtime only |

Default production alert rules are risky because sensor thresholds are crop/location/device dependent and can spam alerts. The current service design favors user-scoped rules.

## 9. Device/Claim/Config Bootstrap Analysis

- Devices do not need production seed. `/iot/devices/provision` and `/iot/devices/connect` can create or update `iot_devices`.
- MQTT `status` and `telemetry` do not auto-provision devices; unknown device UID is retried briefly then ignored with warning.
- Telemetry is accepted only when the device is active, claimed (`ownerUser != null`), and bound to a zone.
- Device config rows do not need seed. They are auto-created on `getDeviceConfig`, `updateDeviceConfig`, or `pushConfig`.
- Config ack does not create missing config. It logs and ignores if device/config/version is missing.
- Claim codes should never be production seed. They are created by `generateClaimCode`, expire after 15 minutes, and are revoked on release.

## 10. Aggregation/Latest Reading Bootstrap Analysis

- `sensor_latest_readings` is runtime state updated after telemetry rows are saved.
- `sensor_reading_agg_5m` is rebuilt from `sensor_reading_series`.
- `sensor_reading_agg_1h` is rebuilt from `sensor_reading_agg_5m`.
- `sensor_reading_agg_1d` is rebuilt from `sensor_reading_agg_1h`.
- Empty tables do not break the scheduler; each rebuild returns when the source list is empty and scheduler catches/logs exceptions.
- Chart APIs require the device and requested sensor type to exist. If aggregates are empty, the response contains an empty point list.

## 11. Empty Database Behavior

| Case | Behavior |
| ---- | -------- |
| App start with DB empty | Needs verification. With only Hibernate DDL active, tables can be created by `ddl-auto:update`. If Flyway is active before Hibernate, V1 likely fails because it references tables not created by migrations. No startup bean found that queries required data except MQTT property validation/logging. |
| ESP32 sends status before provision | `DeviceStatusIngestServiceImpl` retries lookup and logs `Ignoring status for unknown device after retry`; no seed required. |
| ESP32 sends telemetry after provision but missing sensor type | `TelemetryIngestServiceImpl` throws `EntityNotFoundException("Sensor type not found: CODE")`; MQTT handler logs unexpected error; telemetry payload is dropped. |
| User connect device first time | Inserts/updates `iot_devices`, ensures `users`, `farm_plots`, `farm_zones`, marks device `CLAIMED`; no sensor type required for connect. |
| User views dashboard before readings | Farm/zone overview uses counts and empty latest/media; device detail requires device exists but returns empty latest/media and null config if absent. |
| User creates alert rule when sensor type missing | API requires a `sensorTypeId`; if missing/nonexistent, `missingAlertRuleSensorType` error. UI/backend must have sensor types available from DB, but no collector endpoint for listing sensor types was found in this audit. |

## 12. Migration Review

| Migration | Tables changed | Seeds inserted | Missing bootstrap concern |
| --------- | -------------- | -------------- | ------------------------- |
| `V1__add_sensor_latest_readings_and_query_indexes.sql` | Creates `sensor_latest_readings`; indexes `sensor_reading_agg_5m`, `sensor_reading_agg_1h`, `sensor_reading_agg_1d`, `iot_devices`, `alert_events` | None | References base tables that are not created by module SQL; no `sensor_types` seed |
| `V2__add_device_config_push_tracking.sql` | Alters `device_configs` | None | Assumes `device_configs` exists |
| `V3__add_device_media_capture_fields.sql` | Alters/indexes `device_media_events` | None | Assumes `device_media_events` exists |
| `V4__add_device_camera_schedules.sql` | Creates `device_camera_schedules` | None | No seed needed |
| `V5__add_device_media_analysis.sql` | Creates `device_media_analysis` | None | References `device_media_events` and `alert_events` |
| `V6__extend_device_media_analysis_for_jobs.sql` | Alters `device_media_analysis` | None | No seed needed |
| `V7__add_capture_options_to_camera_schedules.sql` | Alters `device_camera_schedules` and backfills existing rows to `VGA`/`MEDIUM` | Backfill of existing schedule config fields only | No production seed concern |

No `INSERT INTO sensor_types` was found in the collector migrations. The only discovered sensor type seed logic is in `iot-test-data-service`, using `ON CONFLICT (code) DO NOTHING`, intended for non-production bootstrap.

## 13. Recommended Minimal Production Seed

Add an idempotent production migration for required sensor types. Suggested values should be aligned with product thresholds, but the existing non-prod seed uses:

| code | name | unit | min_default | max_default | description |
| ---- | ---- | ---- | ----------: | ----------: | ----------- |
| `AIR_TEMP` | Air Temperature | `C` | 18 | 45 | Ambient temperature in Celsius. |
| `AIR_HUMIDITY` | Air Humidity | `%` | 35 | 95 | Relative ambient humidity percentage. |
| `SOIL_MOISTURE` | Soil Moisture | `%` | 10 | 85 | Root-zone soil moisture percentage. |
| `LIGHT_INTENSITY` | Light Intensity | `lux` | 0 | 1200 | Illuminance measured at canopy level. |

Example only, not implemented in this audit:

```sql
INSERT INTO sensor_types (
    id, code, name, unit, min_default, max_default, description, created_at
)
VALUES
    (..., 'AIR_TEMP', 'Air Temperature', 'C', 18, 45, 'Ambient temperature in Celsius.', now()),
    (..., 'AIR_HUMIDITY', 'Air Humidity', '%', 35, 95, 'Relative ambient humidity percentage.', now()),
    (..., 'SOIL_MOISTURE', 'Soil Moisture', '%', 10, 85, 'Root-zone soil moisture percentage.', now()),
    (..., 'LIGHT_INTENSITY', 'Light Intensity', 'lux', 0, 1200, 'Illuminance measured at canopy level.', now())
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    unit = EXCLUDED.unit,
    min_default = EXCLUDED.min_default,
    max_default = EXCLUDED.max_default,
    description = EXCLUDED.description;
```

`CAMERA_DISEASE_DETECTION` does not have to be seeded because collector code creates it lazily, but seeding it can be considered later for catalog completeness if a sensor type listing API/UI is added.

## 14. Recommended Implementation Phases

1. Phase 1: Add idempotent production seed migration for the four telemetry sensor types and verify Flyway/Hibernate ordering.
2. Phase 2: Add startup validation for required sensor type codes, producing a clear fatal error or health warning.
3. Phase 3: Decide whether sensor catalog listing API is needed for UI alert-rule creation.
4. Phase 4: Keep demo data in `iot-test-data-service`; do not mix demo devices/readings/rules into production migrations.
5. Phase 5: Add tests for telemetry ingest with missing sensor type and with seeded sensor type.

## 15. Recommended Next Codex Prompt

```text
Trong Leafy_BE, thực hiện Phase 1 của docs/iot-metrics-bootstrap-data-audit.md cho iot-metrics-collector-service: xác minh runtime có Flyway active hay không, sau đó thêm migration idempotent production seed cho các sensor_types bắt buộc AIR_TEMP, AIR_HUMIDITY, SOIL_MOISTURE, LIGHT_INTENSITY. Không seed device/user/farm/zone/claim/media/telemetry/alert events. Nếu migration ordering hiện tại khiến DB trống lỗi vì V1 tham chiếu bảng chưa tồn tại, đề xuất hoặc sửa migration theo hướng an toàn tối thiểu. Chạy test liên quan IoT.
```
