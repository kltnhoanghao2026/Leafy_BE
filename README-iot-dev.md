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

## Start Options

Full IoT stack:

```powershell
docker compose --env-file .env.iot-dev -f docker-compose.iot-dev.yml up -d --build
```

Infrastructure plus Spring Cloud core only:

```powershell
docker compose --env-file .env.iot-dev -f docker-compose.iot-dev.yml up -d postgresql mqtt-broker discovery-server config-server
```

Stop the IoT stack:

```powershell
docker compose --env-file .env.iot-dev -f docker-compose.iot-dev.yml down
```

Stop and remove the PostgreSQL volume:

```powershell
docker compose --env-file .env.iot-dev -f docker-compose.iot-dev.yml down -v
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
docker compose --env-file .env.iot-dev -f docker-compose.iot-dev.yml ps
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

## Minimal Demo Workflow

Bootstrap a minimal dataset:

```powershell
curl -Method POST http://localhost:8099/seed/bootstrap/minimal
```

Seed 7 days of history:

```powershell
curl -Method POST http://localhost:8099/seed/history/last-7d
```

Start live simulation:

```powershell
curl -Method POST http://localhost:8099/seed/simulation/start
```

Trigger a high-temperature anomaly:

```powershell
curl -Method POST http://localhost:8099/seed/scenarios/high-temperature `
  -ContentType "application/json" `
  -Body '{"deviceUid":"prod-minimal-device-1","count":5}'
```

Trigger a failed config acknowledgement:

```powershell
curl -Method POST http://localhost:8099/seed/scenarios/config-ack-failure `
  -ContentType "application/json" `
  -Body '{"deviceUid":"prod-minimal-device-1","errorMessage":"Simulated apply failure"}'
```

Stop live simulation:

```powershell
curl -Method POST http://localhost:8099/seed/simulation/stop
```

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

Services running on wrong hosts:

- Inside Compose, use service names such as `config-server`, `discovery-server`, `postgresql`, `mqtt-broker`, and `iot-metrics-collector-service`.
- Outside Compose, use `localhost` plus the mapped ports.
