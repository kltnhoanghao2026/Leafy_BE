# Leafy IoT Device Pre-Implementation Checklist

- Confirm exact target board variants: ESP32 devkit, ESP32-CAM, or both.
- Finalize actual sensor hardware list and pin assignments.
- Finalize metric mapping to backend codes: `AIR_TEMP`, `AIR_HUMIDITY`, `SOIL_MOISTURE`, `LIGHT_INTENSITY`.
- Confirm `sensor_types` rows are seeded in the collector database before telemetry testing.
- Decide whether firmware topic env is fixed to `coffee/prod` for prototype or locally configurable.
- Decide prototype-level MQTT auth/security approach for local/dev broker access.
- Confirm device identity source: factory constants, QR label, NVS preload, or local setup entry.
- Confirm local setup portal scope: Wi-Fi only, or Wi-Fi plus MQTT endpoint for dev.
- Confirm Wi-Fi setup UX and physical reset/reconfigure trigger.
- Finalize NVS keys for Wi-Fi, MQTT, device identity, runtime config, and calibration.
- Finalize config validation rules and stale/equal/newer version behavior.
- Decide whether battery reporting is real hardware, placeholder, or omitted.
- Decide whether camera is phase 1 or phase 2. Recommended: phase 2.
- Confirm status heartbeat cadence for prototype. Recommended: 30 seconds.
- Confirm telemetry defaults: sample 60 seconds, publish 300 seconds until backend config arrives.
- Confirm whether local telemetry buffering is required. Recommended: latest-sample memory buffer only for v1.
- Confirm local/dev broker URL and port.
- Confirm gateway/backend onboarding ownership: firmware local portal must not claim devices directly in v1.
