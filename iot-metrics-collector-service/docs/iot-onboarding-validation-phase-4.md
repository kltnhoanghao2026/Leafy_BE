# IoT Onboarding Validation Phase 4

## Scope

This phase hardens backend validation for the IoT onboarding endpoints used by QR-based setup:

- `POST /iot/devices/provision`
- `POST /iot/devices/connect`
- `POST /iot/devices/claim`
- `POST /iot/devices/{deviceId}/claim-code`

Firmware QR v1 and legacy QR payloads map to the same backend connect request. The backend consumes only device identity and user-selected placement fields.

## Backend Connect Required Fields

`POST /iot/devices/connect` requires:

- `deviceUid`
- `deviceCode`
- `deviceType` if provided, otherwise defaults to `ESP32_CAM_SENSOR`
- `deviceName` optional
- `farmPlotId`
- `zoneId`

The backend does not accept or use firmware setup hints such as `type`, `version`, `setupApSsid`, `setupPortalUrl`, `firmwareVersion`, or Wi-Fi/MQTT secrets.

## Validation Rules

| Field | Endpoint | Rule |
| --- | --- | --- |
| `deviceUid` | connect/provision/claim | required, trimmed, `^[A-Za-z0-9._:-]{3,100}$` |
| `deviceCode` | connect/provision | required, trimmed, `^[A-Za-z0-9._:-]{3,100}$` |
| `deviceType` | connect/provision | blank defaults to `ESP32_CAM_SENSOR`; provided values are trimmed and limited to identity-safe characters |
| `deviceName` | connect/provision/update | connect/provision blank becomes null; update rejects blank; max 255 |
| `farmPlotId` | connect/claim | required UUID string |
| `zoneId` | connect/claim | required UUID string |
| `claimCode` | claim | required, trimmed, identity-safe characters, normalized uppercase |

Provision keeps `farmPlotId` and `zoneId` optional.

## Stable Error Codes

| Case | HTTP | Code | Message prefix |
| --- | ---: | ---: | --- |
| Blank `deviceUid` | 400 | 4638 | `IOT_DEVICE_UID_REQUIRED` |
| Invalid `deviceUid` | 400 | 4639 | `IOT_DEVICE_UID_INVALID` |
| Blank `deviceCode` | 400 | 4640 | `IOT_DEVICE_CODE_REQUIRED` |
| Invalid `deviceCode` | 400 | 4641 | `IOT_DEVICE_CODE_INVALID` |
| Invalid `deviceType` | 400 | 4642 | `IOT_DEVICE_TYPE_INVALID` |
| Invalid `deviceName` | 400 | 4643 | `IOT_DEVICE_NAME_INVALID` |
| Missing `farmPlotId` | 400 | 4644 | `IOT_FARM_PLOT_REQUIRED` |
| Missing `zoneId` | 400 | 4645 | `IOT_FARM_ZONE_REQUIRED` |
| Invalid `farmPlotId` | 400 | 4646 | `IOT_FARM_PLOT_INVALID` |
| Invalid `zoneId` | 400 | 4647 | `IOT_FARM_ZONE_INVALID` |
| Blank `claimCode` | 400 | 4648 | `IOT_CLAIM_CODE_REQUIRED` |
| Invalid `claimCode` format | 400 | 4649 | `IOT_CLAIM_CODE_INVALID` |
| Device code belongs to another UID | 409 | 4650 | `IOT_DEVICE_CODE_CONFLICT` |
| Device UID conflict | 409 | 4651 | `IOT_DEVICE_UID_CONFLICT` |
| Device already claimed by another user | 409 | 4613 | `Device already claimed` |
| Invalid claim code | 400 | 4611 | `Invalid claim code` |
| Expired claim code | 400 | 4612 | `Claim code has expired` |

## Rejected Payload Examples

Blank UID:

```json
{
  "deviceUid": " ",
  "deviceCode": "LEAFY-PROTO-001",
  "farmPlotId": "0d3f5e38-1793-47d2-b790-9a4032efa5f8",
  "zoneId": "f475e37e-d3cf-4f3f-baca-efb60d1c1ef7"
}
```

Missing farm:

```json
{
  "deviceUid": "leafy-prototype-001",
  "deviceCode": "LEAFY-PROTO-001",
  "deviceType": "ESP32_CAM_SENSOR",
  "zoneId": "f475e37e-d3cf-4f3f-baca-efb60d1c1ef7"
}
```

Unsupported identity characters:

```json
{
  "deviceUid": "leafy prototype 001",
  "deviceCode": "LEAFY-PROTO-001",
  "farmPlotId": "0d3f5e38-1793-47d2-b790-9a4032efa5f8",
  "zoneId": "f475e37e-d3cf-4f3f-baca-efb60d1c1ef7"
}
```

## Backward Compatibility

- Legacy QR payloads still work if they provide `deviceUid`, `deviceCode`, and `deviceType`.
- QR v1 payloads still work because web/mobile send only the clean backend fields.
- Blank `deviceType` is defaulted to `ESP32_CAM_SENSOR` for compatibility.
- Released devices return to `PROVISIONED` and can be connected again by another user.
- Existing update and release ownership checks remain unchanged.

## Deferred

- Preflight/availability endpoint.
- Rate limiting for repeated connect attempts.
- Audit log entries for connect/release.
- Short-lived proof-of-possession token beyond static `deviceCode`.
