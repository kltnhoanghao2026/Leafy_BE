# IoT Local/Dev Stack

This runbook covers the local/dev stack for:

- `iot-metrics-collector-service`
- `iot-test-data-service`

It is intended for demo and testing only. The stack brings up PostgreSQL, an MQTT broker, Config Server, Eureka, the collector service, and the test-data service with aligned local/dev defaults.

## What This Stack Includes

- PostgreSQL on `localhost:5432`
- Mosquitto MQTT broker on `localhost:1883`
- Eureka (`discovery-server`) on `http://localhost:8761`
- Config Server on `http://localhost:8888`
- `iot-metrics-collector-service` on `http://localhost:8091`
- `iot-test-data-service` on `http://localhost:8099`

Notes:

- The collector service uses Config Server and Eureka, consistent with the rest of the Spring Cloud repo.
- The test-data service currently runs with direct environment-based configuration and calls the collector directly.
- MQTT topic defaults are aligned to `coffee/prod/...` because the collector currently subscribes to that namespace by default in this repo.

## Files

- [docker-compose.iot-dev.yml](/D:/KLTN/Leafy/Leafy_BE/docker-compose.iot-dev.yml)
- [.env.iot-dev.example](/D:/KLTN/Leafy/Leafy_BE/.env.iot-dev.example)
- [scripts/iot-dev](/D:/KLTN/Leafy/Leafy_BE/scripts/iot-dev)

## Frontend/API Handoff Docs

- [IoT API inventory](/D:/KLTN/Leafy/Leafy_BE/docs/iot/iot-api-inventory.md)
- [IoT request and response examples](/D:/KLTN/Leafy/Leafy_BE/docs/iot/iot-request-response-examples.md)
- [IoT enums and state models](/D:/KLTN/Leafy/Leafy_BE/docs/iot/iot-enums-and-state-models.md)
- [IoT frontend screen mapping](/D:/KLTN/Leafy/Leafy_BE/docs/iot/iot-frontend-screen-mapping.md)
- [IoT demo and refresh strategy](/D:/KLTN/Leafy/Leafy_BE/docs/iot/iot-demo-and-refresh-strategy.md)

## Prerequisites

- Docker Desktop or Docker Engine with Compose v2
- Java 21 and Maven only if you want to run one or both Spring services outside Docker

## Setup

1. Copy [.env.iot-dev.example](/D:/KLTN/Leafy/Leafy_BE/.env.iot-dev.example) to `.env.iot-dev`.
2. Adjust values only if needed.

Safe defaults:

- PostgreSQL database: `leafy_iot_db`
- PostgreSQL username/password: `postgres` / `postgres123`
- MQTT broker port: `1883`
- Config Server port: `8888`
- Eureka port: `8761`
- Collector port: `8091`
- Test-data-service port: `8099`

Derived from compose service names:

- Config Server URL: `http://config-server:8888`
- Eureka URL: `http://discovery-server:8761/eureka/`
- PostgreSQL host: `postgresql`
- MQTT broker host: `mqtt-broker`
- Collector base URL for the test-data service: `http://iot-metrics-collector-service:8091`

## Quickstart

PowerShell:

```powershell
Copy-Item .env.iot-dev.example .env.iot-dev
.\scripts\iot-dev\start-iot-dev.ps1
.\scripts\iot-dev\demo-minimal.ps1 -WithAnomaly
```

Shell:

```bash
cp .env.iot-dev.example .env.iot-dev
bash scripts/iot-dev/start-iot-dev.sh
bash scripts/iot-dev/demo-minimal.sh --with-anomaly
```

Run the richer demo:

```powershell
.\scripts\iot-dev\demo-full.ps1 -WithConfigAck
```

```bash
bash scripts/iot-dev/demo-full.sh --with-config-ack
```

Stop the stack safely:

```powershell
.\scripts\iot-dev\stop-iot-dev.ps1
```

```bash
bash scripts/iot-dev/stop-iot-dev.sh
```

Remove the local IoT PostgreSQL volume only when you intentionally want a clean database:

```powershell
.\scripts\iot-dev\stop-iot-dev.ps1 -Volumes
```

```bash
bash scripts/iot-dev/stop-iot-dev.sh --volumes
```

## Start Options

Preferred full stack startup:

```powershell
.\scripts\iot-dev\start-iot-dev.ps1
```

```bash
bash scripts/iot-dev/start-iot-dev.sh
```

The start scripts:

- Create `.env.iot-dev` from [.env.iot-dev.example](/D:/KLTN/Leafy/Leafy_BE/.env.iot-dev.example) if it is missing.
- Run Compose with [docker-compose.iot-dev.yml](/D:/KLTN/Leafy/Leafy_BE/docker-compose.iot-dev.yml).
- Wait for Config Server, Eureka, the collector service, and the test-data service to become reachable.
- Print the key local URLs and suggested demo commands.

Manual Compose command:

```powershell
docker compose --env-file .env.iot-dev -f docker-compose.iot-dev.yml up -d --build
```

Infrastructure plus Spring Cloud core only:

```powershell
docker compose --env-file .env.iot-dev -f docker-compose.iot-dev.yml up -d postgresql mqtt-broker discovery-server config-server
```

Stop the IoT stack:

```powershell
.\scripts\iot-dev\stop-iot-dev.ps1
```

Stop and remove the PostgreSQL volume:

```powershell
.\scripts\iot-dev\stop-iot-dev.ps1 -Volumes
```

## Suggested Startup Sequence

If you run everything through Compose, the dependencies are already encoded and Compose will wait on health checks where practical.

The effective order is:

1. `postgresql`
2. `mqtt-broker`
3. `discovery-server`
4. `config-server`
5. `iot-metrics-collector-service`
6. `iot-test-data-service`

## Verification

PostgreSQL:

```powershell
docker compose --env-file .env.iot-dev -f docker-compose.iot-dev.yml ps postgresql
```

MQTT broker:

```powershell
docker compose --env-file .env.iot-dev -f docker-compose.iot-dev.yml ps mqtt-broker
```

Eureka:

- Open [http://localhost:8761](http://localhost:8761)

Config Server:

- Open [http://localhost:8888/actuator/health](http://localhost:8888/actuator/health)
- Check collector config: [http://localhost:8888/iot-metrics-collector-service/default](http://localhost:8888/iot-metrics-collector-service/default)

Collector service:

- Query alerts: [http://localhost:8091/iot/alert-events](http://localhost:8091/iot/alert-events)

Test-data service:

- Check simulation state: [http://localhost:8099/seed/simulation/status](http://localhost:8099/seed/simulation/status)

Container health summary:

```powershell
.\scripts\iot-dev\check-iot-dev.ps1
```

Shell:

```bash
bash scripts/iot-dev/check-iot-dev.sh
```

## Running The Spring Services Locally Against Docker Infra

Bring up only infra plus Spring Cloud core:

```powershell
docker compose --env-file .env.iot-dev -f docker-compose.iot-dev.yml up -d postgresql mqtt-broker discovery-server config-server
```

Run the collector locally:

```powershell
$env:SPRING_CONFIG_IMPORT="configserver:http://localhost:8888"
$env:SPRING_CLOUD_CONFIG_URI="http://localhost:8888"
$env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE="http://localhost:8761/eureka/"
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="leafy_iot_db"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="postgres123"
$env:MQTT_BROKER_URL="tcp://localhost:1883"
mvn -pl iot-metrics-collector-service spring-boot:run
```

Run the test-data service locally:

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
$env:SEED_DATASOURCE_URL="jdbc:postgresql://localhost:5432/leafy_iot_db"
$env:SEED_DATASOURCE_USERNAME="postgres"
$env:SEED_DATASOURCE_PASSWORD="postgres123"
$env:SEED_COLLECTOR_BASE_URL="http://localhost:8091"
$env:SEED_MQTT_URL="tcp://localhost:1883"
$env:SEED_MQTT_PRODUCT="coffee"
$env:SEED_MQTT_ENV="prod"
mvn -pl iot-test-data-service spring-boot:run
```

## Demo Workflow Scripts

Minimal demo:

```powershell
.\scripts\iot-dev\demo-minimal.ps1 -WithAnomaly
```

```bash
bash scripts/iot-dev/demo-minimal.sh --with-anomaly
```

The minimal demo starts/checks the stack, calls `POST /seed/bootstrap/minimal`, seeds `POST /seed/history/last-7d`, starts `POST /seed/simulation/start`, and optionally triggers `POST /seed/scenarios/high-temperature`.

Full demo:

```powershell
.\scripts\iot-dev\demo-full.ps1 -WithConfigAck
```

```bash
bash scripts/iot-dev/demo-full.sh --with-config-ack
```

The full demo starts/checks the stack, calls `POST /seed/bootstrap/full`, seeds `POST /seed/history/last-30d`, starts `POST /seed/simulation/start`, triggers high-temperature and low-soil-moisture anomalies, and optionally publishes a config ack success scenario.

If the stack is already running and you only want to run the HTTP workflow:

```powershell
.\scripts\iot-dev\demo-minimal.ps1 -SkipStart -WithAnomaly
.\scripts\iot-dev\demo-full.ps1 -SkipStart -WithConfigAck
```

```bash
bash scripts/iot-dev/demo-minimal.sh --skip-start --with-anomaly
bash scripts/iot-dev/demo-full.sh --skip-start --with-config-ack
```

Stop live simulation without stopping the stack:

```powershell
Invoke-RestMethod -Method Post http://localhost:8099/seed/simulation/stop
```

```bash
curl -X POST http://localhost:8099/seed/simulation/stop
```

## Manual Demo Calls

These are useful when you want to run one step at a time.

PowerShell:

```powershell
Invoke-RestMethod -Method Post http://localhost:8099/seed/bootstrap/minimal
Invoke-RestMethod -Method Post http://localhost:8099/seed/history/last-7d
Invoke-RestMethod -Method Post http://localhost:8099/seed/simulation/start
Invoke-RestMethod -Method Post http://localhost:8099/seed/scenarios/high-temperature `
  -ContentType "application/json" `
  -Body '{"deviceUid":"prod-minimal-device-1","count":5,"targetValue":44.0}'
Invoke-RestMethod -Method Post http://localhost:8099/seed/scenarios/config-ack-failure `
  -ContentType "application/json" `
  -Body '{"deviceUid":"prod-minimal-device-1","errorMessage":"Simulated apply failure"}'
```

Shell:

```bash
curl -X POST http://localhost:8099/seed/bootstrap/minimal
curl -X POST http://localhost:8099/seed/history/last-7d
curl -X POST http://localhost:8099/seed/simulation/start
curl -X POST http://localhost:8099/seed/scenarios/high-temperature \
  -H "Content-Type: application/json" \
  -d '{"deviceUid":"prod-minimal-device-1","count":5,"targetValue":44.0}'
curl -X POST http://localhost:8099/seed/scenarios/config-ack-failure \
  -H "Content-Type: application/json" \
  -d '{"deviceUid":"prod-minimal-device-1","errorMessage":"Simulated apply failure"}'
```

## Expected Demo Outcomes

- Bootstrap creates idempotent demo reference data, provisions and claims devices through the collector APIs, and creates alert rules through the collector APIs.
- History seeding publishes MQTT telemetry/status samples through the real collector ingest paths.
- Simulation keeps publishing runtime telemetry/status until stopped.
- Anomaly scripts publish threshold-crossing telemetry that should produce alert events after the collector evaluates the configured rules.
- Config ack scripts publish MQTT ack payloads to the collector ack topic for the selected device.

## Access Notes

- Collector direct API: [http://localhost:8091](http://localhost:8091)
- Test-data service direct API: [http://localhost:8099](http://localhost:8099)
- Eureka dashboard: [http://localhost:8761](http://localhost:8761)
- Config Server: [http://localhost:8888](http://localhost:8888)
- MQTT broker endpoint: `tcp://localhost:1883`

Swagger/OpenAPI:

- There is no dedicated Swagger/OpenAPI setup for the two IoT services in the current repo state.
- Use the direct endpoints above.

## Troubleshooting

Config Server not ready yet:

- Wait for `config-server` to become healthy before starting the collector manually.
- Recheck with [http://localhost:8888/actuator/health](http://localhost:8888/actuator/health).
- The helper scripts retry readiness checks, but the first image build can still take several minutes.

Eureka not ready yet:

- The collector depends on Eureka registration.
- Recheck [http://localhost:8761](http://localhost:8761) and container health.

Collector cannot connect to PostgreSQL:

- Confirm `postgresql` is healthy.
- Confirm DB values in `.env.iot-dev` still match the compose defaults and collector environment.

Collector or test-data service cannot connect to MQTT:

- Confirm `mqtt-broker` is healthy.
- Make sure `SEED_MQTT_ENV` remains aligned with the collector subscription namespace.

DB schema not ready:

- The collector service is responsible for creating/updating its schema on startup in the current repo setup.
- Start the collector before running bootstrap or history jobs from the test-data service.

Wrong profile or prod-profile block:

- `iot-test-data-service` will fail startup if `spring.profiles.active` contains `prod`.
- Keep it on `local`, `dev`, or `staging`.

PowerShell script blocked:

- If local execution policy blocks scripts, run the commands from a PowerShell session that allows local scripts or use the `bash scripts/iot-dev/*.sh` equivalents.

Demo endpoint returns validation or duplicate-data errors:

- The bootstrap flow is intended to be idempotent where practical, but a partial previous run can leave mixed local data.
- Stop the stack with `.\scripts\iot-dev\stop-iot-dev.ps1 -Volumes` or `bash scripts/iot-dev/stop-iot-dev.sh --volumes` only when you intentionally want to reset local IoT data.

Services running on wrong hosts:

- Inside Compose, use service names such as `config-server`, `discovery-server`, `postgresql`, `mqtt-broker`, and `iot-metrics-collector-service`.
- Outside Compose, use `localhost` plus the mapped ports.
