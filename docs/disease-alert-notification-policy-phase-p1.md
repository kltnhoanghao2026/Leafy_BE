# Disease Alert Notification Policy Phase P1

## Problem

Disease detection already created `AlertEvent` records, but notification publishing was skipped before Kafka.

The reason was:

```text
ImageDiseaseAlertServiceImpl created AlertEvent with alertRule = null.
KafkaAlertNotificationPublisher derived notifyWeb/notifyMobile only from alertRule.
Because alertRule was null, notifyWeb and notifyMobile were null.
The publisher required at least one notify flag to be true.
The disease alert was skipped and never reached iot.alert.triggered.
```

Telemetry threshold alerts were not affected because they are created from `AlertRule`.

## Fix

Phase P1 keeps telemetry behavior unchanged and adds an explicit disease notification policy at the IoT collector layer.

Disease alert policy:

| Condition | notifyWeb | notifyMobile | Publish Kafka |
| --- | ---: | ---: | ---: |
| Disease detected, no device config | true | true | yes |
| Disease detected, `DeviceConfig.alertEnabled=true` | true | true | yes |
| Disease detected, `DeviceConfig.alertEnabled=false` | false | false | no |
| Healthy image | n/a | n/a | no |

Implementation shape:

```text
DeviceMediaAnalysisServiceImpl detects disease
-> ImageDiseaseAlertServiceImpl creates AlertEvent
-> ImageDiseaseAlertServiceImpl resolves disease notification policy
-> KafkaAlertNotificationPublisher publishes AlertTriggeredEvent with explicit notify flags
-> notification-service sends UserNotification + FCM
```

Telemetry alerts still call `publishAlertTriggered(alertEvent)` and derive notification flags from `AlertRule`.
Disease alerts call `publishAlertTriggered(alertEvent, notifyWeb, notifyMobile)`.

## Payload

Example disease alert event after Phase P1:

```json
{
  "eventType": "ALERT_TRIGGERED",
  "alertEventId": "7b3a3f47-9d04-4d57-a6f2-7a55c4f0ad9b",
  "ownerUserId": "auth-user-1",
  "deviceId": "6ed2c6f0-cf66-4e6d-9f5e-1082e889df55",
  "deviceUid": "device-001",
  "zoneId": "zone-1",
  "farmPlotId": "farm-1",
  "sensorTypeCode": "CAMERA_DISEASE_DETECTION",
  "alertType": "DISEASE_DETECTED",
  "severity": "HIGH",
  "triggerValue": 0.86,
  "thresholdMin": null,
  "thresholdMax": null,
  "title": "Disease detected from camera image",
  "message": "Detected leaf rust with 86% confidence from camera image.",
  "notifyWeb": true,
  "notifyMobile": true,
  "referenceType": "ALERT_EVENT",
  "referenceId": "7b3a3f47-9d04-4d57-a6f2-7a55c4f0ad9b",
  "url": "/dashboard/alerts?alertId=7b3a3f47-9d04-4d57-a6f2-7a55c4f0ad9b"
}
```

## Limitations

* No disease cooldown/dedup yet.
* No user-level disease notification rule UI yet.
* No media event or analysis metadata is added to FCM payload in Phase P1.
* Mobile still routes to `AlertEvent` detail through `referenceId`.

## Next Phases

* Phase P2: cooldown/dedup by device, zone, and disease name.
* Phase P3: mobile/payload hardening, including optional `alertEventId` fallback and media/analysis metadata.

