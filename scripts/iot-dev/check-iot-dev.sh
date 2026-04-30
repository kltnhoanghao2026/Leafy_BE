#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.iot-dev.yml"
ENV_FILE="$ROOT_DIR/.env.iot-dev"
EXAMPLE_ENV_FILE="$ROOT_DIR/.env.iot-dev.example"

TIMEOUT_SECONDS=180

usage() {
  cat <<'EOF'
Usage: bash scripts/iot-dev/check-iot-dev.sh [--timeout seconds]

Checks readiness for the local/dev IoT stack.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --timeout)
      TIMEOUT_SECONDS="${2:-}"
      shift 2
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

wait_for_url() {
  local name="$1"
  local url="$2"
  local deadline
  deadline=$((SECONDS + TIMEOUT_SECONDS))

  printf "Waiting for %s at %s" "$name" "$url"
  until curl -fsS --max-time 5 "$url" >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      printf "\n%s did not become reachable within %ss\n" "$name" "$TIMEOUT_SECONDS" >&2
      return 1
    fi
    printf "."
    sleep 3
  done
  printf " ready\n"
}

wait_for_url "Config Server" "http://localhost:8888/actuator/health"
wait_for_url "Eureka" "http://localhost:8761"
wait_for_url "IoT collector" "http://localhost:8091/iot/alert-events"
wait_for_url "IoT test-data service" "http://localhost:8099/seed/simulation/status"

echo
echo "Container status:"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps

echo
echo "IoT local/dev stack is reachable."
