# IoT Alert FCM Web Push Phase 5

## Flow

```text
IoT AlertEvent
-> iot.alert.triggered
-> notification-service UserNotification type=IOT_ALERT
-> IN_APP WebSocket when notifyWeb=true
-> FCM WEB token when notifyWeb=true
-> FCM ANDROID/IOS tokens when notifyMobile=true
-> browser foreground toast or service worker notification
-> /dashboard/alerts?alertId=<alertEventId>
```

## Backend Required Env

| Env | Purpose |
| --- | ------- |
| `FIREBASE_ENABLED=true` | Enables Firebase bean creation. |
| `FIREBASE_CONFIG_PATH` | Path to Firebase service account JSON. |
| `NOTIFICATION_PUSH_ENABLED=true` | Allows FCM channel delivery. |

The frontend web VAPID key must belong to the same Firebase project as the backend service account.

## Channel Behavior

| notifyWeb | notifyMobile | Delivery |
| --------: | -----------: | -------- |
| true | false | `IN_APP` + FCM to `WEB` tokens |
| false | true | FCM to `ANDROID` / `IOS` tokens |
| true | true | `IN_APP` + FCM to `WEB`, `ANDROID`, `IOS` tokens |
| false | false | no notification event |

## FCM Data Payload

`NotificationDeliveryServiceImpl` forwards these string data fields when present:

```json
{
  "type": "IOT_ALERT",
  "referenceId": "<alertEventId>",
  "alertEventId": "<alertEventId>",
  "referenceType": "ALERT_EVENT",
  "url": "/dashboard/alerts?alertId=<alertEventId>",
  "severity": "CRITICAL",
  "deviceId": "...",
  "deviceUid": "...",
  "zoneId": "...",
  "farmPlotId": "...",
  "sensorTypeCode": "AIR_TEMP"
}
```

## Platform Targeting

`ReadyToDeliverEvent.fcmPlatforms` limits FCM delivery to selected token platforms.

* `notifyWeb=true` adds `WEB`.
* `notifyMobile=true` adds `ANDROID` and `IOS`.
* If no platform override is present, existing generic FCM delivery remains backward compatible and targets all active tokens.

## Known Limitations

* Browser push requires browser permission and HTTPS or localhost.
* Some browsers throttle or suppress background notifications.
* IN_APP WebSocket and frontend polling fallback remain active.
* Token registration still trusts the authenticated frontend payload shape already used by the existing API.
