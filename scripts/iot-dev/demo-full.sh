#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_DATA_BASE_URL="${TEST_DATA_BASE_URL:-http://localhost:8099}"
WITH_CONFIG_ACK=0
SKIP_START=0

usage() {
  cat <<'EOF'
Usage: bash scripts/iot-dev/demo-full.sh [--skip-start] [--with-config-ack]

Runs the full IoT demo workflow:
  1. Start and check the stack unless --skip-start is passed
  2. Bootstrap full demo data
  3. Seed the last 30 days of history
  4. Start live simulation
  5. Trigger high-temperature and low-soil-moisture anomalies
  6. Optionally publish a config ack scenario
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-start)
      SKIP_START=1
      shift
      ;;
    --with-config-ack)
      WITH_CONFIG_ACK=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 2
      ;;
  esac
done

post_json() {
  local label="$1"
  local path="$2"
  local body="${3:-}"

  echo
  echo "==> $label"
  if [[ -n "$body" ]]; then
    curl -fsS -X POST "$TEST_DATA_BASE_URL$path" \
      -H "Content-Type: application/json" \
      -d "$body"
  else
    curl -fsS -X POST "$TEST_DATA_BASE_URL$path"
  fi
  echo
}

if [[ "$SKIP_START" -eq 0 ]]; then
  bash "$SCRIPT_DIR/start-iot-dev.sh"
else
  bash "$SCRIPT_DIR/check-iot-dev.sh"
fi

post_json "Bootstrap full demo data" "/seed/bootstrap/full"
post_json "Seed last 30 days of history" "/seed/history/last-30d" '{"readingsPerHour":2,"includeAnomalies":true}'
post_json "Start live simulation" "/seed/simulation/start" '{"telemetryIntervalSeconds":60,"statusIntervalSeconds":30,"anomaliesEnabled":true}'
post_json "Trigger high-temperature anomaly" "/seed/scenarios/high-temperature" '{"deviceUid":"prod-full-device-1","count":6,"targetValue":44.0}'
post_json "Trigger low-soil-moisture anomaly" "/seed/scenarios/low-soil-moisture" '{"deviceUid":"prod-full-device-2","count":6,"targetValue":15.0}'

if [[ "$WITH_CONFIG_ACK" -eq 1 ]]; then
  post_json "Publish config ack success" "/seed/scenarios/config-ack-success" '{"deviceUid":"prod-full-device-1","configVersion":1}'
fi

cat <<'EOF'

Full demo workflow complete.

Verification URLs:
  Collector alert events:    http://localhost:8091/iot/alert-events
  Collector alert rules:     http://localhost:8091/iot/alert-rules
  Test-data simulation:      http://localhost:8099/seed/simulation/status
  Eureka dashboard:          http://localhost:8761

Stop simulation:
  curl -X POST http://localhost:8099/seed/simulation/stop
EOF
