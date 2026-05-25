# IoT Alert Notification Pipeline Phase 4

## Flow

```text
IoT telemetry violates an alert rule
-> iot-metrics-collector-service creates AlertEvent
-> publishes iot.alert.triggered when notifyWeb || notifyMobile
-> notification-service validates and forwards to iot.alert.ready
-> notification-service persists UserNotification type=IOT_ALERT
-> notification-service delivers IN_APP and/or FCM based on event flags
```

## Channel Rules

| notifyWeb | notifyMobile | Expected delivery |
| --------: | -----------: | ----------------- |
| true | false | UserNotification + IN_APP WebSocket |
| false | true | UserNotification + FCM |
| true | true | UserNotification + IN_APP WebSocket + FCM |
| false | false | no notification event |

`notification.push.enabled=false` disables FCM dispatch but still allows IN_APP delivery when `notifyWeb=true`.

## Payload Fields

The IoT producer keeps existing fields and adds routing/channel metadata:

| Field | Purpose |
| ----- | ------- |
| `alertEventId` | Alert event identifier. |
| `ownerUserId` | Auth user id that owns the IoT device/rule. |
| `deviceId`, `deviceUid` | Device reference for UI metadata. |
| `zoneId`, `farmPlotId` | Farm scope metadata when available. |
| `sensorTypeCode` | Sensor that triggered the alert. |
| `severity` | Alert severity label. |
| `title`, `message` | Notification copy. |
| `notifyWeb`, `notifyMobile` | Delivery channel flags. |
| `referenceType` | `ALERT_EVENT`. |
| `referenceId` | Same as `alertEventId`. |
| `url` | Relative web route `/dashboard/alerts?alertId=<alertEventId>`. |

Example:

```json
{
  "eventType": "ALERT_TRIGGERED",
  "alertEventId": "7ed2...",
  "ownerUserId": "auth-user-1",
  "deviceId": "device-id-1",
  "deviceUid": "leafy-prototype-001",
  "zoneId": "zone-1",
  "farmPlotId": "farm-1",
  "sensorTypeCode": "AIR_TEMP",
  "severity": "CRITICAL",
  "title": "CRITICAL AIR_TEMP alert",
  "message": "AIR_TEMP exceeded max threshold: 38.5 > 35.0",
  "notifyWeb": true,
  "notifyMobile": false,
  "referenceType": "ALERT_EVENT",
  "referenceId": "7ed2...",
  "url": "/dashboard/alerts?alertId=7ed2..."
}
```

## Idempotency

`UserNotification` already has a sparse unique index on `(recipientId, type, referenceId)`.
IoT alerts use:

```text
type = IOT_ALERT
referenceId = alertEventId
```

This keeps repeated Kafka deliveries for the same alert from creating duplicate user-facing notification rows.

## Recipient Mapping

IoT service emits `ownerUserId` as the auth user id. Notification-service resolves the matching profile id from `notification_users.findByUserId(...)` for notification history. It also carries `recipientUserId` through the batched event so IN_APP WebSocket and FCM can route to the auth user id.

## Topics

| Service | Property | Topic |
| ------- | -------- | ----- |
| iot-metrics-collector-service | `app.kafka.topics.alert-triggered` | `iot.alert.triggered` |
| notification-service | `notification.kafka.topics.alertTriggered` | `iot.alert.triggered` |
| notification-service | `notification.kafka.topics.alertReady` | `iot.alert.ready` |

## Known Limitations

* Browser FCM Web Push behavior remains a later phase.
* Frontend polling/toast fallback remains active.
* No telemetry WebSocket/SSE stream is added in this phase.
* If `notification_users` does not yet contain a user mapping, notification history falls back to the auth user id as recipient id.
