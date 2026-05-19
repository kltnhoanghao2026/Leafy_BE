# Phase A Camera Disease Workflow

This document covers the implemented Phase A workflow for camera media upload,
disease detection, and alert creation.

## Backend configuration

Configure the collector with these properties:

```properties
app.file-service.internal-upload-url=http://localhost:8080/internal/files/upload
app.file-service.presigned-url-template=http://localhost:8080/files/presigned-url/%s
app.disease-detection.predict-url=http://localhost:8080/diseases/predict
app.disease-detection.confidence-threshold=0.70
```

The collector stores analysis state in `device_media_analysis`. A media event can
have at most one analysis row, keyed by `media_event_id`.

## Admin upload workflow

Upload multiple images for one device and optionally run disease detection:

```bash
curl -X POST "http://localhost:8080/admin/camera/upload-folder?deviceUid=leafy-prototype-001&autoDetect=true" \
  -F "files=@./samples/leaf-1.jpg" \
  -F "files=@./samples/leaf-2.jpg"
```

Flow:

1. Collector receives `files[]` and `deviceUid`.
2. Collector uploads each file to File Service with multipart field `file`.
3. Collector creates `DeviceMediaEvent` with `triggerType=ADMIN_UPLOAD` and `status=UPLOADED`.
4. Collector requests a presigned URL for each `fileId`.
5. If `autoDetect=true`, collector calls Disease Detection with the image file.
6. If disease is detected, collector creates an `AlertEvent`.

## Client scheduled capture workflow

Create a schedule from the client device page:

```bash
curl -X POST "http://localhost:8080/iot/devices/leafy-prototype-001/camera/capture-schedule" \
  -H "Content-Type: application/json" \
  -d '{
    "timeOfDay": "08:30:00",
    "recurrence": "DAILY",
    "enabled": true,
    "resolution": "VGA",
    "quality": "MEDIUM"
  }'
```

When firmware later publishes `image/meta`, the collector updates
`DeviceMediaEvent` and automatically starts the same disease detection flow.

Manual disease detection for an uploaded/captured media event:

```bash
curl -X POST "http://localhost:8080/iot/devices/leafy-prototype-001/camera/detect" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaEventId": "00000000-0000-0000-0000-000000000000",
    "fileId": "file-id-from-file-service"
  }'
```

## ESP32 firmware pseudo-code

Firmware behavior remains the existing capture contract. The collector expects
the device to preserve `requestId` and publish metadata after upload.

```cpp
void onCameraCaptureCommand(JsonDocument payload) {
  CaptureJob job;
  job.requestId = payload["requestId"].as<String>();
  job.triggerType = payload["triggerType"] | "MANUAL";
  job.resolution = payload["resolution"] | "VGA";
  job.quality = payload["quality"] | "MEDIUM";
  captureQueue.push(job);
}

void captureWorkerLoop() {
  CaptureJob job = captureQueue.pop();
  for (int attempt = 1; attempt <= 3; attempt++) {
    CameraFrame jpeg = camera.captureJpeg(job.resolution, job.quality);
    UploadResult result = fileService.uploadMultipart(jpeg.bytes, "image/jpeg");
    if (result.success) {
      publishImageMeta(job, "SUCCESS", result.fileId, jpeg.sizeBytes, nullptr);
      return;
    }
    delay(5000);
  }
  publishImageMeta(job, "FAILURE", "", 0, "UPLOAD_FAILED");
}

void publishImageMeta(CaptureJob job, String status, String fileId, size_t sizeBytes, const char *error) {
  JsonDocument meta;
  meta["deviceUid"] = DEVICE_UID;
  meta["requestId"] = job.requestId;
  meta["triggerType"] = job.triggerType;
  meta["timestamp"] = nowIso8601();
  meta["status"] = status;
  meta["fileId"] = fileId;
  meta["contentType"] = "image/jpeg";
  meta["sizeBytes"] = sizeBytes;
  if (error != nullptr) {
    meta["errorMessage"] = error;
  }
  mqtt.publish("coffee/prod/devices/" + DEVICE_UID + "/image/meta", meta);
}
```

## Frontend entry points

- Admin: `/admin/iot-camera-batch-upload`
- Device Detail: the media panel can create a schedule, run capture, run disease
  detection on the latest uploaded image, and show analysis status.

## Runtime notes

- The collector does not duplicate analysis for the same `mediaEventId`.
- File Service and Disease Detection endpoints must be reachable from the
  collector process.
- If those endpoints require user JWTs, add an internal service token or expose
  internal endpoints before enabling this flow in production.
