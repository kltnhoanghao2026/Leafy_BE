# IoT Device Security Hardening Phase D

## Endpoints Protected

Phase D adds owner checks based on the existing `X-User-Id` request header. A caller can access a device resource only when `iot_devices.owner_user_id` matches the header value.

Protected endpoint groups:

- Device config: `GET/PUT /iot/devices/{deviceId}/config`, `POST /iot/devices/{deviceId}/config/push`
- Camera capture: `POST /iot/devices/{deviceId}/camera/capture`
- Device media: `GET /iot/devices/{deviceId}/media`, `GET /iot/media-events/{mediaEventId}`
- Device-scoped camera schedules: `GET/POST/PUT/DELETE /iot/devices/{deviceUid}/camera/capture-schedule...`, `POST /run-scheduled/{scheduleId}`, `POST /detect`
- Telemetry: `GET /iot/devices/{deviceId}/latest-readings`, `GET /iot/devices/{deviceId}/charts`, `GET /iot/farm-zones/{zoneId}/charts`
- Dashboard: `GET /iot/farm-zones/{zoneId}/overview`, `GET /iot/dashboard/overview`, `GET /iot/devices/{deviceId}/detail`
- Alert events: `GET /iot/alert-events`, `GET /iot/alert-events/{alertEventId}`, `POST /acknowledge`, `POST /resolve`
- Alert rules: create/update/list filter scope validation
- Claim code: `POST /iot/devices/{deviceId}/claim-code`

## Ownership Rules

- Device by id: device exists and `ownerUser.id == X-User-Id`.
- Device by uid: device exists and `ownerUser.id == X-User-Id`.
- Media event: media event exists and linked device is owned by `X-User-Id`.
- Alert event: alert event owner matches `X-User-Id`, or linked device owner matches `X-User-Id`.
- Zone: caller owns at least one IoT device assigned to the zone.
- Farm plot: caller owns at least one IoT device assigned to the farm plot.

## Claim-Code Policy

`POST /iot/devices/{deviceId}/claim-code` now requires `X-User-Id`.

- Claimed device: only the current owner can generate a claim code.
- Unclaimed device: generation is allowed only when the device is active and `provisioningStatus = PROVISIONED`.
- Disabled or invalid-state devices are rejected.

This keeps the existing request body unchanged. A stronger physical possession proof, such as requiring `deviceCode`, is deferred because it would change the API contract.

## Alert Rule Scope Policy

Alert rules remain owner-scoped. Phase D also validates every explicit rule target:

- `deviceId`: must belong to the current user.
- `zoneId`: current user must own at least one device in the zone.
- `farmPlotId`: current user must own at least one device in the farm plot.

The same validation is applied to create/update payloads and explicit list filters.

## Deferred / Known Limitations

- Global camera schedule endpoints under `/iot/camera-schedules` remain deferred. They are admin-like, but no gateway/JWT/admin role contract is visible in this service. Adding a fake admin header was intentionally avoided.
- MQTT ingest/internal device ingestion endpoints are not protected by `X-User-Id`.
- Historical telemetry/media/alerts do not include owner snapshots. Authorization is based on current device ownership or alert owner fields.
- Zone/farm authorization is based on local IoT device association because this service does not verify ownership through plant-management in Phase D.

## Notes For Later Phases

- Define gateway role propagation for true admin endpoints.
- Consider adding physical possession proof for unclaimed device claim-code generation.
- Consider owner snapshots on historical records if data access must remain tied to the owner at event creation time.
