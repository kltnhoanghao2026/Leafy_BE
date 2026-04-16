# IoT Demo And Refresh Strategy

This guide is for frontend developers and demo operators using the local/dev IoT stack.

## Recommended End-To-End Demo Flow

### 1. Start the stack

PowerShell:

```powershell
Copy-Item .env.iot-dev.example .env.iot-dev
.\scripts\iot-dev\start-iot-dev.ps1
```

Shell:

```bash
cp .env.iot-dev.example .env.iot-dev
bash scripts/iot-dev/start-iot-dev.sh
```

The start scripts wait for:

- Config Server: `http://localhost:8888/actuator/health`
- Eureka: `http://localhost:8761`
- Collector: `http://localhost:8091/iot/alert-events`
- Test-data service: `http://localhost:8099/seed/simulation/status`

### 2. Bootstrap demo data

Minimal:

```powershell
.\scripts\iot-dev\demo-minimal.ps1 -WithAnomaly
```

Full:

```powershell
.\scripts\iot-dev\demo-full.ps1 -WithConfigAck
```

Shell equivalents:

```bash
bash scripts/iot-dev/demo-minimal.sh --with-anomaly
bash scripts/iot-dev/demo-full.sh --with-config-ack
```

What this does:

- Creates reference data directly only where needed.
- Provisions devices through collector APIs.
- Generates claim codes through collector APIs.
- Claims devices through collector APIs.
- Creates alert rules through collector APIs.
- Publishes history and runtime telemetry/status through MQTT.
- Triggers anomaly telemetry that can generate real alert events.

### 3. Inspect core screens

Useful URLs:

- Collector alert events: `http://localhost:8091/iot/alert-events`
- Collector alert rules: `http://localhost:8091/iot/alert-rules`
- Test-data simulation status: `http://localhost:8099/seed/simulation/status`
- Eureka: `http://localhost:8761`

There is no dedicated Swagger/OpenAPI setup for the two IoT services in the current repo state.

### 4. Push config and simulate ack

Manual sequence:

1. Read device list for a seeded user: `GET /iot/devices/me` with `X-User-Id`.
2. Read device config: `GET /iot/devices/{deviceId}/config`.
3. Update config: `PUT /iot/devices/{deviceId}/config`.
4. Push config: `POST /iot/devices/{deviceId}/config/push`.
5. Simulate ack via test-data service: `POST /seed/scenarios/config-ack-success`.
6. Re-read config and verify `lastPushStatus=ACKED`, `lastAckAt` set, and `appliedAt` set.

Failure path:

- Call `POST /seed/scenarios/config-ack-failure`.
- Re-read config and verify `lastPushStatus=FAILED`, `lastAckAt` set, and `lastPushError` populated.

### 5. Stop simulation or stack

Stop only live simulation:

```powershell
Invoke-RestMethod -Method Post http://localhost:8099/seed/simulation/stop
```

Stop stack:

```powershell
.\scripts\iot-dev\stop-iot-dev.ps1
```

Reset local IoT DB only when needed:

```powershell
.\scripts\iot-dev\stop-iot-dev.ps1 -Volumes
```

## Refresh Strategy

Use polling thoughtfully. The current implementation is request/response plus MQTT ingestion; there is no frontend websocket/SSE stream in these IoT services.

| UI area | Suggested refresh | Reason |
| --- | --- | --- |
| Dashboard overview | 30 to 60 seconds | Counters update after ingest/status changes but do not need second-level polling. |
| Alert preview widget | 15 to 30 seconds during demo, 30 to 60 seconds otherwise | Alert generation is real but not pushed to frontend. |
| Alert center list | 10 to 20 seconds when focused, 30 to 60 seconds in background | Operators need near-current status during anomaly demos. |
| Latest readings on device detail | 15 to 30 seconds | Live simulation publishes telemetry every 60 seconds by default. |
| Zone overview latest readings | 15 to 30 seconds | Same latest snapshot source as device readings. |
| Device list | 30 to 60 seconds | Status changes on status ingest every 30 seconds in demo simulation. |
| Device config after push | 5 to 10 seconds for up to 60 seconds | Ack is asynchronous over MQTT. Stop polling after `ACKED` or `FAILED`. |
| Charts | On page load and range/sensor change | Chart data comes from aggregate tables; avoid rapid polling. |

## Data Freshness Model

Realtime-ish data:

- Device latest readings: updated during telemetry ingest.
- Device status and `lastSeenAt`: updated during status ingest and telemetry ingest.
- Alert events: generated during telemetry ingest when enabled rules match threshold violations.
- Config ack state: updated when MQTT ack arrives.

Aggregated data:

- Charts use aggregate tables.
- `H24` and `D3` use 5-minute aggregates.
- `D7` and `D30` use 1-hour aggregates.
- `D90` uses 1-day aggregates.
- Aggregate scheduler rebuild cadence is 1 minute for 5-minute aggregates, 5 minutes for 1-hour aggregates, and 1 hour for 1-day aggregates.

Manual refresh data:

- Alert rule list/configuration should refresh after create/update/toggle/delete.
- Device config should refresh after update, push, and config ack simulation.
- Bootstrap/history/simulation test-data endpoints should show response summaries directly to the operator.

## Demo Data Notes

Default seeded sensor codes:

- `AIR_TEMP`
- `AIR_HUMIDITY`
- `SOIL_MOISTURE`
- `LIGHT_INTENSITY`

Default deterministic device UIDs in local/dev:

- Minimal: `prod-minimal-device-1`, `prod-minimal-device-2`
- Full: `prod-full-device-1` through `prod-full-device-6`

The `prod` segment in these UIDs and topics is the current MQTT namespace default used by the local/dev compose file to align with the collector subscribed topics. It does not mean the test-data service is running with a production Spring profile.

## Avoid Over-Polling

- Do not poll charts every few seconds.
- Do not poll every alert detail row individually in a list; use `GET /iot/alert-events` for the list and fetch detail only on selection.
- Do not start duplicate simulations; `POST /seed/simulation/start` returns the current session if already running.
- Keep page sizes at or below 100; services clamp sizes above 100.

## Common Demo Troubleshooting

No devices in history or simulation:

- Run `POST /seed/bootstrap/minimal` or `POST /seed/bootstrap/full` first.
- Check `GET /iot/devices/me` with a seeded user `X-User-Id`.

No alert after anomaly:

- Verify alert rules exist and are enabled with `GET /iot/alert-rules`.
- Verify the anomaly targets a device that was bootstrapped.
- Check alert rule cooldown. Active `OPEN` or `ACKNOWLEDGED` alerts can suppress duplicates within cooldown.

Config ack does not update config:

- Verify the ack `deviceUid` exists.
- Verify `configVersion` matches current device config. If unsure, omit `configVersion` in the test-data request so the service fetches it.
- Verify ack type is generated by test-data service as `config`.

Charts look empty after history seed:

- Wait for aggregate scheduler windows to rebuild.
- Check the selected `sensorCode` and `range`.
- Try `H24` or `D7` after seeding history.
