#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.iot-dev.yml"
ENV_FILE="$ROOT_DIR/.env.iot-dev"
EXAMPLE_ENV_FILE="$ROOT_DIR/.env.iot-dev.example"

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$EXAMPLE_ENV_FILE" "$ENV_FILE"
  echo "Created .env.iot-dev from .env.iot-dev.example."
fi

echo "Starting IoT local/dev stack..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build

echo
bash "$SCRIPT_DIR/check-iot-dev.sh"

cat <<'EOF'

Key URLs:
  Eureka:            http://localhost:8761
  Config Server:     http://localhost:8888/actuator/health
  Collector:         http://localhost:8091
  Test Data Service: http://localhost:8099

Next commands:
  bash scripts/iot-dev/demo-minimal.sh --with-anomaly
  bash scripts/iot-dev/demo-full.sh --with-config-ack
EOF
