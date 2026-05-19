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
    "quality": "MEDIUM"
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
