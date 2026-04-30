# Leafy IoT Device Production Gaps, Risks, And Open Questions

This document lists gaps that should be resolved before production rollout. They do not necessarily block a local/dev prototype.

## 1. MQTT Authentication Weakness

Why it matters:

The current collector trusts `deviceUid` and topic shape. Any client that can publish to the broker can impersonate a device by publishing to that device's topic.

Prototype impact:

Does not block local/dev if the broker is isolated.

Future mitigation:

Add per-device MQTT credentials or mTLS, broker ACLs that restrict each credential to one topic prefix, and backend device credential lifecycle management.

## 2. Provision/Claim Authorization Concerns

Why it matters:

Provision and claim-code endpoints appear admin/internal, but the collector itself does not strongly enforce that boundary. If exposed broadly, unauthorized callers may create devices or generate claim codes.

Prototype impact:

Does not block local/dev; should be controlled by environment/network access.

Future mitigation:

Gate provision and claim-code APIs behind admin roles, service-to-service auth, or an internal-only route. Keep user claim behind normal JWT auth.

## 3. `claimTokenHash` Is Unused In Runtime Trust

Why it matters:

The backend `DeviceClaim` model includes `claimTokenHash`, but runtime MQTT ingest does not use it to authenticate devices.

Prototype impact:

Does not block local/dev.

Future mitigation:

Use a claim/provisioning secret to mint per-device credentials, or derive a device secret that can be used for MQTT auth or payload signatures.

## 4. `image/meta` Is Incomplete

Why it matters:

The collector recognizes `image/meta` but only logs it. There is no complete storage, file-service linkage, or media processing workflow.

Prototype impact:

Does not block telemetry prototype. Blocks production camera features.

Future mitigation:

Define media upload flow, file IDs, metadata schema, retention, authorization, and UI consumption before implementing camera firmware.

## 5. Sensor Type Dependency

Why it matters:

Telemetry fails if a metric code is missing from `sensor_types`. The collector has no public sensor-type management API.

Prototype impact:

Can block telemetry until reference sensor types are seeded.

Future mitigation:

Add managed sensor-type seeding/migrations or a restricted admin API. Keep firmware metric codes versioned and documented.

## 6. Topic Environment Rigidity

Why it matters:

The collector is effectively configured for `coffee/prod/...`, and parser code hard-codes product namespace `coffee`.

Prototype impact:

Does not block if firmware uses `coffee/prod`.

Future mitigation:

Make accepted product namespace/env configurable in backend and firmware, or define one stable production namespace and document it as immutable.

## 7. Offline Timeout Gap

Why it matters:

`offlineTimeoutSec` exists in config and is pushed to devices, but the collector does not appear to auto-mark stale devices offline via scheduler. A device can remain `ONLINE` if it disappears without publishing `online=false`.

Prototype impact:

Does not block basic demo, but status may be misleading.

Future mitigation:

Add backend stale-device scheduler using `lastSeenAt` and device config, or require broker LWT messages that publish offline status.

## 8. Status Field Persistence Gap

Why it matters:

Fields such as battery, RSSI, IP, SSID, and uptime are accepted by DTOs but are mostly not persisted or exposed.

Prototype impact:

Does not block telemetry/alert prototype. Limits diagnostics and UI visibility.

Future mitigation:

Add a device runtime snapshot table/entity, persist selected status fields, and expose them through device detail APIs.

## 9. Config Push/ACK Reliability Questions

Why it matters:

Current backend records `SENT`, `ACKED`, or `FAILED`, but retry policy, timeout behavior, duplicate config handling, and power loss during apply are not fully defined.

Prototype impact:

Does not block first config ACK implementation if firmware is idempotent.

Future mitigation:

Define backend config push retries, ACK timeout, stale version rules, and config audit history. Firmware should keep last known good config and ACK failure without corrupting local state.

## 10. Broker And Network Deployment

Why it matters:

Local/dev uses Mosquitto with simple defaults. Production needs TLS, credentials, firewall rules, observability, and broker durability decisions.

Prototype impact:

Does not block local/dev.

Future mitigation:

Choose managed MQTT or hardened broker deployment, enable TLS, monitor connection churn, and define topic ACLs.

## 11. Time Synchronization

Why it matters:

Telemetry/status timestamps are useful only if device time is synced. Without NTP, backend falls back to server time only when `ts` is omitted.

Prototype impact:

Does not block ingest.

Future mitigation:

Add NTP sync, timestamp validity checks, and omit `ts` until time is known.
