#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.iot-dev.yml"
ENV_FILE="$ROOT_DIR/.env.iot-dev"
EXAMPLE_ENV_FILE="$ROOT_DIR/.env.iot-dev.example"
REMOVE_VOLUMES=0

usage() {
  cat <<'EOF'
Usage: bash scripts/iot-dev/stop-iot-dev.sh [--volumes|-v]

Stops the IoT local/dev stack. Volumes are preserved unless --volumes is passed.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --volumes|-v)
      REMOVE_VOLUMES=1
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

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing .env.iot-dev. Create it with:"
  echo "  cp \"$EXAMPLE_ENV_FILE\" \"$ENV_FILE\""
  exit 1
fi

ARGS=(down)
if [[ "$REMOVE_VOLUMES" -eq 1 ]]; then
  ARGS+=("-v")
fi

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "${ARGS[@]}"

if [[ "$REMOVE_VOLUMES" -eq 1 ]]; then
  echo "IoT local/dev stack stopped and volumes removed."
else
  echo "IoT local/dev stack stopped. Volumes were preserved."
fi
