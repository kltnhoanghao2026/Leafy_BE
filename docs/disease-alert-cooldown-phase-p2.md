# Disease Alert Cooldown Phase P2

## Problem

After Phase P1, disease detection alerts can publish Kafka events and mobile push notifications. Without cooldown, repeated camera captures of the same infected plant can create duplicate `AlertEvent` records and duplicate FCM pushes in a short time window.

## Policy

Cooldown key:

```text
deviceId + zoneId + diseaseName
```

Cooldown window:

```text
60 minutes
```

Disease name is normalized by trimming whitespace and uppercasing. If the detector does not return a disease name, the fallback key is `UNKNOWN_DISEASE`.

## Behavior

| Scenario | Behavior |
| --- | --- |
| First disease on a device/zone | Create `AlertEvent` and publish notification. |
| Same disease, same device, same zone within cooldown | Return the recent alert, suppress new `AlertEvent`, suppress Kafka/FCM. |
| Same disease, same device, same zone after cooldown | Create new `AlertEvent` and publish notification. |
| Different disease, same device, same zone | Create separate `AlertEvent` and publish notification. |
| Same disease, same device, different zone | Create separate `AlertEvent` and publish notification. |
| Healthy image | No disease alert. |
| `DeviceConfig.alertEnabled=false` | Preserve Phase P1 behavior: create alert when not duplicate, but do not publish notification. |

## Implementation

The cooldown check uses existing persisted analysis data, so no database migration is required:

```text
DeviceMediaAnalysis.status = DISEASE_DETECTED
DeviceMediaAnalysis.alertEvent is not null
DeviceMediaAnalysis.mediaEvent.device.id = deviceId
DeviceMediaAnalysis.mediaEvent.zone.id = zoneId
normalized diseaseName/diseaseType = diseaseKey
DeviceMediaAnalysis.analyzedAt >= now - 60 minutes
```

If a recent matching alert exists, `ImageDiseaseAlertServiceImpl.createDiseaseAlert(...)` returns that existing `AlertEvent` and does not call `AlertEventRepository.save(...)` or `KafkaAlertNotificationPublisher`.

## Limitations

* Cooldown is a service constant, not user-configurable yet.
* No disease-specific `AlertRule` exists yet.
* Duplicate analysis records are still saved and can link to the recent alert returned by the service.
* The FCM payload still references `AlertEvent`; it does not include `mediaEventId`, `analysisId`, `diseaseName`, or `confidence` yet.

## Next Phases

* Phase P3: mobile `alertEventId` fallback and optional disease/media metadata in notification payload.
* Future: user-configurable disease alert rules and cooldown duration.

