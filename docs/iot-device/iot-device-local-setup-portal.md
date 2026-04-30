# Leafy IoT Local Setup Portal Design

## 1. Purpose

The local setup portal is for first-time network setup and basic device diagnostics. It is not the main Leafy web portal and it is not the backend provision/claim workflow.

The local portal should help a user get an ESP32/ESP32-CAM module onto Wi-Fi, confirm the device identity printed on the device, and run simple diagnostics before the main app/backend claim flow is completed.

## 2. Expected Flow

1. Device boots.
2. Firmware loads local config from NVS.
3. If Wi-Fi config is missing, invalid, or reset mode is triggered, device starts SoftAP mode.
4. User connects to the device AP.
5. User opens the portal, for example:

```text
http://192.168.4.1
```

6. User enters Wi-Fi SSID/password.
7. Portal displays device identity and basic diagnostics.
8. Device saves config to NVS.
9. Device restarts Wi-Fi in station mode and connects to the selected network.
10. If connection succeeds, device proceeds to MQTT connection and runtime operation.

Implemented SoftAP name pattern:

```text
Leafy-Setup-{suffix}
```

The suffix should come from the end of `deviceUid` when available, or from the ESP32 chip ID fallback. The v1 prototype uses an open AP for bench bring-up.

## 3. Minimum Pages/Screens

### Wi-Fi Setup Page

Fields:

- SSID
- Password
- Save/connect action

Behavior:

- Allow manual SSID entry.
- Do not display saved password after submit.
- Save credentials to NVS using `config_store`.
- Reboot after a successful save so the normal runtime state machine starts cleanly.

Future options:

- Scan nearby SSIDs.
- Add MQTT broker host/port editing for local/dev builds.

### Device Info Page

Show:

- `deviceUid`
- `deviceCode`
- `deviceType`
- firmware version
- current local config version
- product namespace/env, normally `coffee/prod`

Purpose:

- User can copy or scan device identity for backend/web portal provision and claim.

### Diagnostics/Test Page

Show:

- Wi-Fi connection state
- IP address
- RSSI
- MQTT connection state
- runtime config
- calibration values
- current sensor readings
- raw soil and light ADC values for calibration
- heap and uptime

Optional actions:

- Test sensor read
- Test MQTT status publish
- Reboot device

### Reset/Reconfigure Page

Actions:

- Clear Wi-Fi credentials only.
- Reboot into setup mode.

Future actions:

- Clear runtime config only.
- Factory reset local firmware config.

Safety:

- Require confirmation for destructive reset.
- Keep `deviceUid` and `deviceCode` unless a true factory reprovision mode is intentionally implemented.

## 3.1 Implemented V1 Routes

| Route | Method | Purpose |
| --- | --- | --- |
| `/` | `GET` | Device info and links |
| `/wifi` | `GET` | Wi-Fi setup form |
| `/wifi` | `POST` | Save SSID/password and schedule reboot |
| `/diagnostics` | `GET` | Runtime config, calibration, Wi-Fi/MQTT state, and sensor readings |
| `/reset` | `GET` | Wi-Fi reset confirmation |
| `/reset` | `POST` | Clear Wi-Fi config only and schedule reboot |
| `/api/status` | `GET` | Small JSON status endpoint |

The v1 portal intentionally does not include backend provision/claim, camera upload, calibration editing, or a full REST API.

## 4. Data Shown Locally

Minimum:

- `deviceUid`
- `deviceCode`
- firmware version
- Wi-Fi status
- MQTT status
- sensor detection summary

Useful prototype diagnostics:

- configured MQTT broker
- topic prefix
- RSSI
- uptime
- heap/free memory
- last error message

## 5. Error Handling

### Invalid Wi-Fi Credentials

Behavior:

- Save submitted credentials and reboot.
- If station mode cannot connect after reboot, the normal Wi-Fi reconnect backoff is used.
- A user can clear Wi-Fi config and return to setup mode through `/reset` while in setup mode, or by future physical reset trigger.

Future enhancement:

- Attempt connection from setup mode before reboot and show immediate failure.

### Save Failure

Behavior:

- Do not reboot.
- Show `Failed to save configuration`.
- Keep previous valid config if one exists.

### Reconnect Timeout

Behavior:

- Retry with backoff.
- After repeated failures, optionally re-enter setup mode.
- Avoid rapid reboot loops.

### Missing Sensors Or Diagnostic Warnings

Behavior:

- Show the missing sensor status.
- Continue booting if the sensor is optional.
- Omit missing/invalid metric values from telemetry.
- For required sensors, mark diagnostics as failed but still allow Wi-Fi/MQTT setup.

## Portal Scope Boundaries

The local setup portal should not:

- Create backend device records.
- Generate claim codes.
- Claim the device to a user/farm/zone.
- Store user JWTs.
- Pretend MQTT security is solved.

Those are backend/web/mobile responsibilities in the current architecture.
