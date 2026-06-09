# IoT Device Management Phase A

Phase A adds backend support for owner-scoped device metadata updates and soft release/unclaim. It does not change database schema, MQTT topics, firmware behavior, or web/mobile UI.

## Endpoints Added

### PATCH `/iot/devices/{deviceId}`

Required header:

```http
X-User-Id: <current-user-id>
```

Request:

```json
{
  "deviceName": "Greenhouse 1 - Camera",
  "farmPlotId": "farm-plot-id",
  "zoneId": "zone-id",
  "active": true
}
```

Response: `DeviceResponse`.

Rules:

- The device must exist and must be owned by `X-User-Id`.
- `deviceName`, `farmPlotId`, `zoneId`, and `active` are the only mutable fields in this phase.
- `deviceName`, when present, is trimmed, must not be blank, and must be at most 255 characters.
- Null fields mean no change.
- Farm and zone references are created in the IoT database when absent, matching the existing connect flow.
- Hardware identity and lifecycle fields are not mutable here: `deviceUid`, `deviceCode`, `deviceType`, `ownerUser`, `provisioningStatus`, `status`, `lastSeenAt`, and `firmwareVersion`.

### POST `/iot/devices/{deviceId}/release`

Required header:

```http
X-User-Id: <current-user-id>
```

Request body: none.

Response: `DeviceResponse`.

## Release Policy

Release is a soft unlink. It does not delete the `iot_devices` row.

Release preserves:

- `deviceUid`
- `deviceCode`
- `deviceType`
- `status`
- `lastSeenAt`
- `firmwareVersion`
- telemetry, media events, alert events, and other history

Release clears:

- `ownerUser`
- `farmPlot`
- `zone`

Release sets:

- `provisioningStatus = PROVISIONED`
- `isActive = true`

Release also revokes pending claim codes by setting pending `DeviceClaim.status` to `REVOKED`.

## Camera Schedule Policy

Phase A disables all camera schedules for the released device and clears their `nextRunAt`. This prevents scheduled capture from continuing after a device becomes unclaimed. Schedules are not deleted, so history/configuration can be inspected or restored by future policy if needed.

## Claim/Connect After Release

After release:

- Another account can connect with the same `deviceUid` and `deviceCode` through `POST /iot/devices/connect`.
- Another account can claim the device if a new claim code is generated and submitted through `POST /iot/devices/claim`.
- Old pending claim codes are revoked during release and cannot be used.

`POST /iot/devices/{deviceId}/claim-code` still has no owner/admin check in Phase A. This is intentionally deferred to Phase D security hardening.

## Not Solved In Phase A

- No web or mobile UI changes.
- No admin force release/update role logic.
- No full ownership hardening for existing config/media/camera/telemetry/dashboard/alert endpoints.
- No transfer endpoint.
- No hard delete or soft delete endpoint.
- No cross-service farm-zone ownership validation.
- No historical data visibility policy change beyond preserving existing records.

## Notes For Phase B/C

Web and mobile should add API wrappers for:

- `updateDevice(deviceId, payload)`
- `releaseDevice(deviceId)`

Expected UI:

- Device detail/list action menu.
- Edit device modal/screen with name, farm picker, zone picker, and optional active toggle.
- Release confirmation explaining that the device will no longer belong to the current account and another account can reconnect through the existing claim/connect flow.

## Notes For Phase D

Add shared device access checks for existing endpoints:

- device detail
- config get/update/push
- media list/detail
- camera capture
- camera schedules
- telemetry latest/charts
- dashboard overview
- alert list/detail/lifecycle
- claim-code generation

Also define the gateway/auth contract for admin/client role propagation before adding admin force release/update.
