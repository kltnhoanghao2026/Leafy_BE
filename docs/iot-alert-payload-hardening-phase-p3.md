# IoT Alert Payload Hardening Phase P3

## Changes

Phase P3 adds optional disease/media metadata to the IoT alert notification payload while preserving the telemetry alert contract.

Added optional fields on `AlertTriggeredEvent`:

* `mediaEventId`
* `analysisId`
* `diseaseName`
* `confidence`

Disease alerts populate these fields when `DeviceMediaAnalysis` context is available. Telemetry alerts leave them unset.

## FCM Data

Example disease alert FCM data:

```json
{
  "type": "IOT_ALERT",
  "referenceId": "<alertEventId>",
  "alertEventId": "<alertEventId>",
  "referenceType": "ALERT_EVENT",
  "severity": "HIGH",
  "deviceId": "<deviceId>",
  "deviceUid": "<deviceUid>",
  "zoneId": "<zoneId>",
  "farmPlotId": "<farmPlotId>",
  "sensorTypeCode": "CAMERA_DISEASE_DETECTION",
  "mediaEventId": "<mediaEventId>",
  "analysisId": "<analysisId>",
  "diseaseName": "leaf rust",
  "confidence": "0.86"
}
```

All FCM data values are strings. Metadata fields are copied only when present in the persisted notification payload.

## Compatibility

* Kafka topic names are unchanged.
* Existing telemetry payload fields are unchanged.
* Disease metadata is optional and is not required by notification-service validation.
* Mobile routes by `referenceId`; after P3 it can also fall back to `alertEventId` and `alertId`.
* Mobile still opens `AlertEvent` detail, not media detail.

## Limitations

* Metadata is not displayed in a new mobile UI yet.
* Disease media/analysis links can be used by a future alert detail enhancement.

