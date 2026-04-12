# IoT Metrics Collector Service — In-Depth Breakdown

## 1. What It Is

The `iot-metrics-collector-service` is a **Spring Boot 3 / Java 21** microservice that acts as the **IoT data spine** for the Leafy platform. Its responsibilities span the full lifecycle of a field sensor device:

- **Device provisioning & claiming** (physical → owned by a farmer)
- **Telemetry ingestion** via MQTT and HTTP
- **Real-time alert evaluation** against user-defined rules
- **Time-series aggregation** at 5-minute, 1-hour, and 1-day granularities
- **Dashboard query surface** (latest readings, zone/farm overview, charts)
- **Bidirectional device config** (push config down to devices via MQTT, receive ack)

Unlike the rest of Leafy's Java services (which use **MongoDB**), this service uses **PostgreSQL** — a deliberate choice to support time-range queries, partial unique indexes, and relational joins that are natural for time-series and alert data.

---

## 2. Dependencies & Tech Stack

| Concern | Library |
|---|---|
| Web | Spring Boot Starter Web |
| Persistence | Spring Data JPA + PostgreSQL JDBC |
| MQTT messaging | Spring Integration MQTT + Eclipse Paho `mqttv3` |
| Spring Integration | `spring-boot-starter-integration` |
| Service discovery | Spring Cloud Netflix Eureka Client |
| Centralised config | Spring Cloud Config Client |
| Utilities | Lombok |
| Database migration | Flyway (implied by `db/migration/` scripts) |

> **No Kafka** — this service uses MQTT as its primary inbound channel and does not participate in the Outbox/Kafka pattern used by other Leafy services.

---

## 3. Configuration

### 3.1 Bootstrap (`application.yaml`)

```yaml
spring:
  application:
    name: iot-metrics-collector-service
  config:
    import: configserver:http://localhost:8888
  cloud:
    config:
      uri: http://localhost:8888
      fail-fast: true
      retry:
        max-attempts: 6
        initial-interval: 1000
        max-interval: 2000
        multiplier: 1.1
```

This is a **minimal bootstrap file** — all real config (DB URL, MQTT broker URL, Eureka URL, etc.) comes from the Config Server. If the Config Server is unreachable, the service will retry up to 6 times with a 1–2 s interval before failing fast.

### 3.2 MQTT Properties (`MqttProperties`)

Bound via `@ConfigurationProperties(prefix = "app.mqtt")` and logged on startup:

| Property | Default | Description |
|---|---|---|
| `url` | — (required) | Broker URL, e.g. `tcp://localhost:1883` |
| `username` / `password` | — (optional) | Broker credentials |
| `clientId` | — (required) | Unique client ID for this service instance |
| `topics` | `[]` (required) | List of topics to subscribe |
| `qos` | `1` | QoS for all subscriptions |
| `completionTimeout` | `5000` ms | Adapter message-completion timeout |
| `automaticReconnect` | `true` | Auto-reconnect on drop |
| `cleanSession` | `true` | Start a clean session on each connect |
| `connectionTimeout` | `10` s | TCP connect timeout |
| `keepAliveInterval` | `20` s | MQTT keep-alive ping interval |

### 3.3 MQTT Wiring (`MqttInboundConfig`)

Two Spring Integration channels are declared:

| Channel Bean | Direction | Purpose |
|---|---|---|
| `mqttInputChannel` | Inbound | Receives all MQTT messages the service subscribes to |
| `mqttOutboundChannel` | Outbound | Publishes config payloads down to devices |

**Inbound adapter** (`MqttPahoMessageDrivenChannelAdapter`) subscribes to all topics listed in `MqttProperties.topics` and pipes messages into `mqttInputChannel`.

**`@ServiceActivator(inputChannel = "mqttInputChannel")`** wires `MqttInboundMessageHandler` as the consumer of every inbound message — this is where routing logic starts.

**Outbound handler** (`MqttPahoMessageHandler`) listens on `mqttOutboundChannel` and publishes whatever messages are sent to it synchronously (`setAsync(false)`).

> The outbound handler uses a `"-outbound"` suffix on the client ID to avoid conflicting with the inbound client ID.

### 3.4 JPA / Audit

`JpaAuditConfig` enables JPA auditing (`@EnableJpaAuditing`), which populates `createdAt`/`updatedAt` in entities that extend `BaseAuditEntity`.

`JacksonConfig` customises the shared `ObjectMapper` (e.g., instants serialised as ISO-8601 strings).

---

## 4. MQTT Topic Schema

The topic structure is parsed by `MqttTopicParser`:

```
coffee/{env}/devices/{deviceUid}/{messageType}
```

Supported message types:

| Topic suffix | Handled by | Purpose |
|---|---|---|
| `/telemetry` | `TelemetryIngestService` | Sensor readings from device |
| `/status` | `DeviceStatusIngestService` | Online/offline heartbeat |
| `/ack` | `DeviceConfigAckService` | Device acknowledges a config push |
| `/image/meta` | logged only (WIP) | Camera/snapshot metadata |

**Outbound** (server → device):
```
coffee/{env}/devices/{deviceUid}/config/set
```

The namespace (`coffee/prod` by default) is inferred from the first subscribed topic.

---

## 5. Data Model (PostgreSQL)

The service owns 9 core tables:

### 5.1 `iot_devices`

The central aggregate root.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | Auto-generated |
| `device_uid` | VARCHAR(100) UNIQUE | Hardware-assigned UID (used in MQTT topics) |
| `device_code` | VARCHAR(100) UNIQUE | Human-readable code used for claiming |
| `device_name` | VARCHAR(255) | Friendly name |
| `device_type` | VARCHAR(100) | e.g., `SENSOR_NODE`, `CAMERA_NODE` |
| `firmware_version` | VARCHAR(100) | Updated on every telemetry message |
| `status` | `DeviceStatus` enum | `ONLINE` / `OFFLINE` |
| `provisioning_status` | `ProvisioningStatus` enum | `PROVISIONED` → `CLAIMED` → `DISABLED` |
| `owner_user_id` | UUID FK → `users` ref | Set when a farmer claims the device |
| `farm_plot_id` | UUID FK → `farm_plots` ref | Set during claim |
| `zone_id` | UUID FK → `farm_zones` ref | Set during claim |
| `installed_at` | TIMESTAMPTZ | Optional physical install timestamp |
| `last_seen_at` | TIMESTAMPTZ | Updated on every telemetry ingest |
| `is_active` | BOOLEAN | Soft-delete flag |
| `metadata` | JSONB | Free-form extra fields |

> **Cross-service refs** (`UserRef`, `FarmPlotRef`, `FarmZoneRef`, `FileRef`) are JPA entities mapped to their own tables but **not joined to foreign services' schemas** — they act as local reference tables populated during the claiming flow.

### 5.2 `sensor_types`

Catalogue of sensor metrics. One row per physical measurement kind.

| Column | Notes |
|---|---|
| `code` | UNIQUE string, e.g., `TEMP`, `HUMIDITY`, `SOIL_MOISTURE` |
| `unit` | e.g., `°C`, `%`, `lux` |
| `min_default` / `max_default` | Suggested normal range |

### 5.3 `sensor_reading_series` (raw time-series)

Every individual sensor reading lands here first. High write volume — each MQTT telemetry message may insert N rows (one per metric code in the payload).

| Column | Notes |
|---|---|
| `id` | BIGINT identity (sequential for write performance) |
| `device_id` | FK iot_devices |
| `zone_id` | FK farm_zones |
| `sensor_type_id` | FK sensor_types |
| `reading_value` | DOUBLE PRECISION |
| `reading_time` | TIMESTAMPTZ (device timestamp or ingest time) |
| `quality_status` | `GOOD` / `STALE` / `INVALID` |
| `raw_payload` | JSONB snapshot of original message |

### 5.4 Aggregate tables (3 resolutions)

Each is the same structure (`BaseSensorReadingAgg`), differing only in bucket size:

| Table | Bucket | Rebuilt every | Lookback |
|---|---|---|---|
| `sensor_reading_agg_5m` | 5 min | 1 minute | 15 min |
| `sensor_reading_agg_1h` | 1 hour | 5 minutes | 3 hours |
| `sensor_reading_agg_1d` | 1 day | 1 hour | 3 days |

Common columns: `device_id`, `sensor_type_id`, `zone_id`, `bucket_start`, `bucket_end`, `min_value`, `max_value`, `avg_value`, `sample_count`.

Unique partial indexes ensure idempotent upserts (separate indexes for `zone_id IS NULL` and `zone_id IS NOT NULL`).

### 5.5 `sensor_latest_readings`

A **materialised "last known value" table** — one row per `(device_id, sensor_type_id)`. Updated atomically after each ingest. Enables O(1) dashboard queries without scanning the time-series table.

### 5.6 `alert_rules`

User-defined threshold rules scoped at any of: device, zone, farm-plot, or globally per sensor type.

| Column | Notes |
|---|---|
| `sensor_type_id` | Required — what sensor to watch |
| `min_threshold` / `max_threshold` | Either or both can be set |
| `severity` | `LOW` / `MEDIUM` / `HIGH` / `CRITICAL` |
| `cooldown_minutes` | Minimum minutes between repeated alerts for the same rule+device+sensor |
| `enabled` | Can be toggled off without deletion |
| `notify_web` / `notify_mobile` | Channel flags (notification delivery not yet wired in this service) |

### 5.7 `alert_events`

Records every threshold violation detected.

| Column | Notes |
|---|---|
| `status` | `OPEN` → `ACKNOWLEDGED` → `RESOLVED` |
| `trigger_value` | The actual reading value that fired the alert |
| `threshold_min` / `threshold_max` | Snapshot of rule thresholds at the time of firing |
| `alert_type` | `THRESHOLD_LOW` or `THRESHOLD_HIGH` |
| `message` | Human-readable description |
| `push_sent` | Whether a push notification was dispatched |
| `opened_at`, `acknowledged_at`, `resolved_at` | Lifecycle timestamps |

### 5.8 `device_configs`

One-to-one with `iot_devices`. Configures device behaviour sent over MQTT.

| Column | Notes |
|---|---|
| `sampling_interval_sec` | How often device samples its sensors (default: 60s) |
| `publish_interval_sec` | How often device publishes to MQTT (default: 300s) |
| `offline_timeout_sec` | After how many silent seconds to mark OFFLINE (default: 900s) |
| `alert_enabled` | Master on/off for device alerts |
| `config_version` | Incremented on each update |
| `last_push_status` | `SENT` / `FAILED` / `ACKNOWLEDGED` |
| `last_push_error` | Error message if push failed |
| `last_ack_at` | Timestamp of device's last config ack |

### 5.9 `device_claims`

One-time-use claim codes linking an unclaimed device to a user.

| Column | Notes |
|---|---|
| `claim_code` | 8-char alphanumeric (TTL: 15 min) |
| `claim_token_hash` | Hashed token (for secure validation) |
| `expires_at` | Claim code expiry timestamp |
| `status` | `PENDING` → `CLAIMED` |

### 5.10 `device_media_events`

Records camera/image captures from devices (WIP — ingestion not fully implemented).

---

## 6. Flyway Migrations

| Version | Change |
|---|---|
| V1 | Creates `sensor_latest_readings` table + all performance indexes on aggregate and device tables |
| V2 | Adds `last_push_status`, `last_push_error`, `last_ack_at` columns to `device_configs` |

The base schema (all other tables) is created by Hibernate DDL rather than Flyway.

---

## 7. Core Feature Deep Dives

### 7.1 Device Provisioning & Claiming

```
Admin/factory                    User (farmer)
   │                                  │
   │  POST /iot/devices/provision     │
   │  { deviceUid, deviceCode, ... }  │
   │─────────────────────────────►Service: creates IoTDevice(PROVISIONED, OFFLINE)
   │                                  │
   │  POST /iot/devices/{id}/claim-code│
   │─────────────────────────────►Service: creates DeviceClaim(PENDING, TTL 15min)
   │◄──────── { claimCode: "AB12CD34" }│
   │  (print on device label/QR code) │
   │                                  │
   │                                  │  POST /iot/devices/claim
   │                                  │  { deviceUid, claimCode, farmPlotId, zoneId }
   │                                  │──────────────────────►Service:
   │                                  │                        - validates not expired
   │                                  │                        - sets ownerUser, farmPlot, zone
   │                                  │                        - marks CLAIMED
```

Validation gates prevent claiming inactive, already-claimed, DISABLED devices, or with expired/used claim codes.

### 7.2 Telemetry Ingest Pipeline

```
Physical Device
  │
  │  MQTT: coffee/prod/devices/{uid}/telemetry
  │  Payload: { ts: "2024-01-01T00:00:00Z", metrics: { TEMP: 28.5, HUMIDITY: 72.1 }, firmwareVersion: "1.2.3" }
  ▼
MqttInboundMessageHandler
  │  parse topic → DeviceTopicInfo(deviceUid, "telemetry")
  ▼
TelemetryIngestServiceImpl.ingest()
  ├─ findByDeviceUid(uid) → IoTDevice
  ├─ validateDevice(): must be active, claimed (owner != null), zone != null
  ├─ for each metric in payload.metrics:
  │   ├─ findSensorType by code (e.g. "TEMP")
  │   └─ build SensorReadingSeries(device, zone, sensorType, value, time, quality=GOOD)
  ├─ saveAll(readings) → sensor_reading_series
  ├─ AggregateLatestReadingService.updateLatestReadings()
  │   └─ for each reading: upsert sensor_latest_readings (only if newer)
  ├─ update IoTDevice.lastSeenAt = reading.ts
  ├─ update IoTDevice.firmwareVersion (if present in payload)
  └─ AlertEvaluationService.evaluateReadings(savedReadings)  ← same transaction
```

The HTTP path (`POST /iot/devices/{deviceUid}/telemetry`) mirrors exactly the same service logic for HTTP-based ingest.

### 7.3 Alert Evaluation Engine

Runs **synchronously** at the tail of every telemetry ingest call (same transaction).

```
evaluateReadings(readings)
  └─ for each SensorReadingSeries reading:
       ├─ isEvaluable? (device, sensorType, value all non-null)
       ├─ fetch all enabled AlertRules for this sensorType
       └─ for each rule:
            ├─ isApplicable?
            │   ├─ same sensorType?
            │   ├─ if rule.device != null → same device?
            │   └─ if rule.zone != null → same zone?
            ├─ getViolation(rule, value):
            │   ├─ value < minThreshold → ThresholdViolation("THRESHOLD_LOW", msg)
            │   └─ value > maxThreshold → ThresholdViolation("THRESHOLD_HIGH", msg)
            ├─ isDuplicateWithinCooldown?
            │   └─ existsByRule&Device&Sensor&StatusIn(OPEN,ACK)&openedAt > cutoff
            └─ save AlertEvent(OPEN, severity, triggerValue, thresholds, timestamps)
```

**Cooldown logic** prevents alert flooding: if an `OPEN` or `ACKNOWLEDGED` alert for the same rule+device+sensor already exists within `cooldownMinutes`, the new firing is silently skipped.

### 7.4 Time-Series Aggregation (Scheduled)

The `AggregateScheduler` runs three periodic jobs:

| Job | Period | Covers window | Source → Destination |
|---|---|---|---|
| `rebuild5mAggregates` | Every 1 min | Last 15 min | `sensor_reading_series` → `agg_5m` |
| `rebuild1hAggregates` | Every 5 min | Last 3 hours | `agg_5m` → `agg_1h` |
| `rebuild1dAggregates` | Every 1 hour | Last 3 days | `agg_1h` → `agg_1d` |

**Aggregation strategy** in `AggregateServiceImpl`:

- **5m rebuild** — reads raw series, groups by `(deviceId, sensorTypeId, zoneId, 5-min-bucket)`, computes `min/max/avg/count`, upserts `sensor_reading_agg_5m`.
- **1h rebuild** — reads from `sensor_reading_agg_5m`, groups into 1h buckets. Uses **weighted average** (`avg * sampleCount / totalCount`) to correctly aggregate pre-averaged 5m buckets.
- **1d rebuild** — same pattern, reads from `sensor_reading_agg_1h`.

Bucket alignment uses `Math.floorDiv(epochSecond, bucketSizeSeconds) * bucketSizeSeconds` — pure epoch math, timezone-independent.

The upsert strategy: try `findExistingAggregate(bucketKey)`; if found, update stats in-place; otherwise create new. Partial unique indexes on PostgreSQL prevent duplicates.

### 7.5 Dashboard Query Surface

Three read-heavy endpoints, all backed by the materialised/aggregate tables:

| Endpoint | Reads from | Returns |
|---|---|---|
| `GET /iot/dashboard/overview?farmPlotId=` | `iot_devices`, `alert_events` | Total/online/offline devices, zone count, open alert count, last activity |
| `GET /iot/farm-zones/{zoneId}/overview` | `sensor_latest_readings`, `alert_events`, `device_media_events` | Latest readings per sensor, alert summary, latest media capture |
| `GET /iot/devices/{deviceId}/detail` | `iot_devices`, `sensor_latest_readings`, `alert_events`, `device_configs`, `device_media_events` | Full device snapshot |

### 7.6 Chart / Time-Series Query

`GET /iot/devices/{deviceId}/charts?sensorCode=TEMP&range=D7`

The `ChartRangeType` enum maps range strings to the correct aggregate source:

| Range | Lookback | Source table |
|---|---|---|
| `24H` / `H24` | 24 hours | `sensor_reading_agg_5m` |
| `3D` / `D3` | 3 days | `sensor_reading_agg_5m` |
| `7D` / `D7` | 7 days | `sensor_reading_agg_1h` |
| `30D` / `D30` | 30 days | `sensor_reading_agg_1h` |
| `90D` / `D90` | 90 days | `sensor_reading_agg_1d` |

The same chart endpoint exists for zones: `GET /iot/farm-zones/{zoneId}/charts`.

### 7.7 Device Config Push / Ack Cycle

```
App/User                     Service                    Physical Device
   │                            │                            │
   │  PUT /iot/devices/{id}/config                           │
   │  { samplingIntervalSec: 30 }                            │
   │───────────────────────────►│                            │
   │                            │ update device_configs      │
   │◄─────────────────────────── DeviceConfigResponse        │
   │                            │                            │
   │  POST /iot/devices/{id}/config/push                     │
   │───────────────────────────►│                            │
   │                            │ validate device is CLAIMED + active
   │                            │ fetch or create DeviceConfig (defaults: 60/300/900s)
   │                            ├──────────────────────────►│
   │                            │ MQTT: coffee/prod/devices/{uid}/config/set
   │                            │ { configVersion, samplingIntervalSec, ... }
   │                            │ set lastPushStatus = SENT  │
   │◄─────────────────────────── DeviceConfigResponse        │
   │                            │                            │
   │                            │◄──────────────────────────│
   │                            │  MQTT ack on /ack topic    │
   │                            │  DeviceConfigAckService    │
   │                            │  set lastPushStatus = ACKNOWLEDGED, lastAckAt = now
```

### 7.8 Alert Lifecycle

```
OPEN ──► ACKNOWLEDGED ──► RESOLVED
```

- `POST /iot/alert-events/{id}/acknowledge` → sets `status=ACKNOWLEDGED`, `acknowledgedAt=now`
- `POST /iot/alert-events/{id}/resolve` → sets `status=RESOLVED`, `resolvedAt=now`

---

## 8. REST API Surface Summary

All routes are prefixed by `/iot`:

### Device Management (`/iot/devices`)

| Method | Path | Description |
|---|---|---|
| POST | `/provision` | Register a new device (admin/factory) |
| POST | `/{id}/claim-code` | Generate OTP-style claim code |
| POST | `/claim` | User claims a device into their farm |
| GET | `/me` | List devices owned by current user |
| GET | `/{id}/config` | Get device config |
| PUT | `/{id}/config` | Update device config |
| POST | `/{id}/config/push` | Push config to device via MQTT |

### Telemetry Ingest (`/iot/devices`)

| Method | Path | Description |
|---|---|---|
| POST | `/{uid}/telemetry` | HTTP ingest (mirrors MQTT path) |
| POST | `/{uid}/status` | HTTP status update |

### Query & Charts (`/iot`)

| Method | Path | Description |
|---|---|---|
| GET | `/devices/{id}/latest-readings` | All latest readings for a device |
| GET | `/devices/{id}/charts` | Chart data (`range` + `sensorCode` params) |
| GET | `/farm-zones/{zoneId}/charts` | Zone-level chart |
| GET | `/devices/{id}/detail` | Full device dashboard snapshot |
| GET | `/farm-zones/{zoneId}/overview` | Zone dashboard |
| GET | `/dashboard/overview?farmPlotId=` | Farm dashboard summary |

### Alerts (`/iot/alert-events`)

| Method | Path | Description |
|---|---|---|
| GET | `/` | Search alerts (zoneId, deviceId, status, severity, from, to) |
| GET | `/{id}` | Get single alert detail |
| POST | `/{id}/acknowledge` | Acknowledge alert |
| POST | `/{id}/resolve` | Resolve alert |

---

## 9. Enums Reference

| Enum | Values |
|---|---|
| `DeviceStatus` | `ONLINE`, `OFFLINE` |
| `ProvisioningStatus` | `PROVISIONED`, `CLAIMED`, `DISABLED` |
| `ClaimStatus` | `PENDING`, `CLAIMED` |
| `AlertSeverity` | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `AlertStatus` | `OPEN`, `ACKNOWLEDGED`, `RESOLVED` |
| `DeviceConfigPushStatus` | `SENT`, `FAILED`, `ACKNOWLEDGED` |
| `ReadingQualityStatus` | `GOOD`, `STALE`, `INVALID` |
| `MediaType` | (WIP) |
| `TriggerType` | (WIP) |

---

## 10. Key Design Decisions

1. **PostgreSQL over MongoDB** — Time-range queries (`BETWEEN`), partial unique indexes for aggregate upserts, and multi-dimensional alert queries are far more ergonomic in a relational DB.

2. **Materialised latest-reading table** — `sensor_latest_readings` is an application-level materialised view. Trades storage for O(1) dashboard reads without needing PostgreSQL materialized views or TimescaleDB.

3. **Batch aggregate rebuild vs. streaming** — Rather than maintaining running aggregates in a stream processor, aggregates are rebuilt from a trailing window on a schedule. This is simpler, self-healing (re-runs fix gaps), and avoids late-arriving data edge cases.

4. **Synchronous alert evaluation** — Alerts are evaluated inline in the telemetry transaction. This avoids async complexity and ensures alerts are persisted in the same DB commit as the readings that triggered them.

5. **Local cross-service reference tables** — `UserRef`, `FarmPlotRef`, `FarmZoneRef` are local tables holding just the UUID. This keeps the service self-contained without Feign calls on the hot ingest path.

6. **QoS 1 MQTT** — "At least once" delivery. Idempotency is not fully enforced at the SQL level for raw series (duplicates can exist if a message is redelivered), but aggregate buckets are idempotently upserted via partial unique indexes.

7. **No Kafka** — This service stands outside the Outbox pattern; MQTT is its message bus. Other services that need IoT data would need to either call this service's REST API or have a future Kafka producer added here.
