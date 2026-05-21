# Phase B Client Scheduled Capture Analysis

Phase B adds asynchronous disease analysis for uploaded scheduled captures.

## Flow

1. Client creates a schedule:

```bash
curl -X POST "http://localhost:8080/iot/devices/leafy-prototype-001/camera/capture-schedule" \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": true,
    "timeOfDay": "08:30:00",
    "recurrence": "DAILY",
    "resolution": "VGA",
    "quality": "MEDIUM",
    "uploadEndpoint": "http://localhost:8080/files/upload"
  }'
```

2. The collector schedule runner sends a scheduled capture command to MQTT.
3. The ESP32 captures and uploads the image, then publishes `image/meta`.
4. Collector updates `DeviceMediaEvent.status` to `UPLOADED`.
5. After the DB transaction commits, collector enqueues an in-memory image-analysis job.
6. The job stores/updates `DeviceMediaAnalysis`:
   - `PENDING`
   - `PROCESSING`
   - `PROCESSED`
   - `DISEASE_DETECTED`
   - `FAILED`
7. If disease is detected, collector creates one `AlertEvent` and links it to the analysis row.

## Image analysis job payload

The internal job contains:

```json
{
  "deviceUid": "leafy-prototype-001",
  "requestId": "capture-request-id",
  "mediaEventId": "device-media-event-uuid",
  "triggerType": "SCHEDULED",
  "timestamp": "2026-05-19T08:30:00Z",
  "fileId": "file-service-id",
  "s3Url": "https://presigned-url"
}
```

## Manual re-analysis

The client can force re-analysis for an uploaded media event:

```bash
curl -X POST "http://localhost:8080/iot/devices/leafy-prototype-001/camera/detect" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaEventId": "device-media-event-uuid",
    "fileId": "file-service-id",
    "force": true
  }'
```

Existing alerts are not duplicated when the same media event is analyzed again.

## Idempotency

- `device_media_analysis.media_event_id` is unique.
- Auto-enqueue skips media events that already have `PENDING`, `PROCESSING`,
  `PROCESSED`, or `DISEASE_DETECTED` analysis.
- Alert creation only happens when `diseaseDetected=true` and the analysis row
  has no linked `alertEvent`.

## Configuration

```properties
app.image-analysis.max-attempts=1
app.file-service.presigned-url-template=http://localhost:8080/files/presigned-url/%s
app.disease-detection.predict-url=http://localhost:8080/diseases/predict
app.disease-detection.confidence-threshold=0.70
```

## Frontend behavior

Device Detail now displays:

- All schedules for the current `deviceUid`.
- Latest image thumbnail and media history.
- Analysis status, disease type, severity, and alert badge.
- Schedule creation form with time, recurrence, resolution, quality, and optional
  upload endpoint.
- Manual `Trigger Analysis` action through the existing detect endpoint with
  `force=true`.

## Phase 1 client schedule CRUD

Client/mobile routes are scoped by `deviceUid`; the path value is authoritative,
so the request body cannot modify a schedule for a different device.

### Endpoints

```text
POST   /iot/devices/{deviceUid}/camera/capture-schedule
PUT    /iot/devices/{deviceUid}/camera/capture-schedule/{scheduleId}
DELETE /iot/devices/{deviceUid}/camera/capture-schedule/{scheduleId}
POST   /iot/devices/{deviceUid}/camera/run-scheduled/{scheduleId}
```

Admin routes remain unchanged:

```text
GET    /iot/camera-schedules
POST   /iot/camera-schedules
PUT    /iot/camera-schedules/{scheduleId}
DELETE /iot/camera-schedules/{scheduleId}
POST   /iot/camera-schedules/{scheduleId}/run-now
```

Client scoped list route:

```text
GET    /iot/devices/{deviceUid}/camera/capture-schedule
```

### Request body

```json
{
  "enabled": true,
  "timeOfDay": "08:30:00",
  "recurrence": "DAILY",
  "resolution": "HD",
  "quality": "HIGH",
  "uploadEndpoint": "https://files.example.com/files/upload"
}
```

### cURL examples

```bash
curl -X PUT "http://localhost:8080/iot/devices/leafy-prototype-001/camera/capture-schedule/{scheduleId}" \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": true,
    "timeOfDay": "09:00:00",
    "recurrence": "WEEKLY",
    "resolution": "QVGA",
    "quality": "LOW"
  }'

curl -X POST "http://localhost:8080/iot/devices/leafy-prototype-001/camera/run-scheduled/{scheduleId}"

curl -X DELETE "http://localhost:8080/iot/devices/leafy-prototype-001/camera/capture-schedule/{scheduleId}"
```

Allowed values:

- `triggerType`: `SCHEDULED`, `MANUAL` for backward compatibility. Client
  schedule routes force `SCHEDULED`.
- `recurrence`: `NONE`, `DAILY`, `WEEKLY`, `MONTHLY`.
- `resolution`: `QVGA`, `VGA`, `HD`.
- `quality`: `LOW`, `MEDIUM`, `HIGH`.
- `uploadEndpoint`: optional HTTP or HTTPS URL. When omitted, the collector
  uses `app.file-service.upload-url`.

### Response body

```json
{
  "id": "schedule-uuid",
  "deviceUid": "leafy-prototype-001",
  "enabled": true,
  "triggerType": "SCHEDULED",
  "timeOfDay": "08:30:00",
  "recurrence": "DAILY",
  "resolution": "HD",
  "quality": "HIGH",
  "uploadEndpoint": "https://files.example.com/files/upload",
  "lastRunAt": null,
  "nextRunAt": "2026-05-22T01:30:00Z",
  "lastMediaEvent": null
}
```

### MQTT payload

When the scheduler or run-now endpoint executes a schedule, the persisted
capture options are passed to the existing MQTT topic
`coffee/{env}/devices/{deviceUid}/camera/capture`:

```json
{
  "requestId": "capture-request-id",
  "deviceUid": "leafy-prototype-001",
  "triggerType": "SCHEDULED",
  "requestedAt": "2026-05-21T02:30:00Z",
  "resolution": "HD",
  "quality": "HIGH",
  "upload": {
    "mode": "FILE_SERVICE_MULTIPART",
    "endpoint": "https://files.example.com/files/upload"
  }
}
```

Run-now and due-schedule execution skip creating a new scheduled capture when
the same device already has an active scheduled media event in `REQUESTED`,
`COMMAND_SENT`, or `UPLOADING`.
