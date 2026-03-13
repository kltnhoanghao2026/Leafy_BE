# Leafy Backend — Agent & Contributor Guide

## Project Overview

**Leafy** is a smart-farming platform that lets users monitor plant health, manage farms, and receive AI-powered disease diagnosis and treatment recommendations through a mobile app backed by a polyglot microservices architecture.

---

## Architecture at a Glance

| Layer              | Technology                                                          |
| ------------------ | ------------------------------------------------------------------- |
| API Gateway        | Spring Cloud Gateway (port **8060**)                                |
| Service Discovery  | Netflix Eureka (port **8761**)                                      |
| Centralised Config | Spring Cloud Config Server (port **8888**)                          |
| Core Services      | Spring Boot 3.5.9 · Java 21 · Maven multi-module                    |
| AI / RAG Service   | Python 3 · FastAPI · LangGraph · Qdrant                             |
| Disease Detection  | Python 3 · FastAPI (service scaffolded, implementation in progress) |
| Message Broker     | Apache Kafka (KRaft, port **9092**)                                 |
| Primary Database   | MongoDB 7.0 (port **27017**)                                        |
| Cache / Sessions   | Redis 7 (port **6379**)                                             |
| Email              | Brevo (SendInBlue) via `sib-api-v3-sdk`                             |

All services register with Eureka and pull configuration from the Config Server at startup.

---

## Service Map

```
backend/
├── pom.xml                        # Root Maven BOM — manages versions for all modules
├── docker-compose.yml             # Infrastructure (MongoDB, Kafka, Redis, Kafka-UI, core services)
├── common/                        # Shared library — DTOs, events, Kafka Outbox, security utils
├── api-gateway/                   # Spring Cloud Gateway — JWT pre-filter, rate limiting (Redis)
├── discovery-server/              # Eureka Server
├── config-server/                 # Spring Cloud Config Server
├── auth-service/                  # Authentication: JWT, OTP, device management (MongoDB + Redis)
├── profile-service/               # User profile management (MongoDB)
├── farm-service/                  # Farm CRUD (MongoDB) — implementation in progress
├── plant-management-service/      # Plant encyclopedia / catalogue (MongoDB)
├── file-service/                  # File upload & storage
├── notification-service/          # Email notifications via Brevo; REST-triggered by other services
├── disease-detection-service/     # [Python/FastAPI] Plant disease inference — scaffolded, WIP
└── rag-service/                   # [Python/FastAPI] LangGraph RAG pipeline, Qdrant, Treatment Plans
```

### Python service roots

| Service                     | Root                                 | Entry point                       |
| --------------------------- | ------------------------------------ | --------------------------------- |
| `rag-service`               | `backend/rag-service/`               | `run.py` → `uvicorn app.main:app` |
| `disease-detection-service` | `backend/disease-detection-service/` | TBD (WIP)                         |

---

## Development Commands

### Java Services (Maven Wrapper)

Run all commands from the `backend/` directory unless targeting a specific module.

```bash
# Build all modules (skip tests)
./mvnw clean install -DskipTests

# Build a single service
./mvnw clean install -pl auth-service -DskipTests

# Run tests for the entire project
./mvnw test

# Run tests for a specific service
./mvnw test -pl farm-service

# Run a service locally (from its directory, after building common first)
./mvnw spring-boot:run -pl auth-service

# Build common module first whenever changing shared types
./mvnw clean install -pl common -DskipTests
```

> **Windows note**: replace `./mvnw` with `mvnw.cmd` when running in PowerShell/CMD without WSL.

### Python Services

Each Python service has its own `.venv`. Always activate it before running commands.

```bash
# --- rag-service ---
cd backend/rag-service

# Create / restore virtual environment
python -m venv .venv
.venv\Scripts\activate          # Windows
source .venv/bin/activate       # Linux/macOS

# Install dependencies
pip install -r requirements.txt

# Run the service (registers with Eureka, hot-reloads)
python run.py

# Run with uvicorn directly (no Eureka registration)
uvicorn app.main:app --host 0.0.0.0 --port 8081 --reload

# Run tests
pytest

# Run tests with coverage
pytest --cov=app --cov-report=term-missing
```

```bash
# --- disease-detection-service ---
cd backend/disease-detection-service
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt   # once requirements.txt is added
uvicorn app.main:app --reload
pytest
```

### Infrastructure (Docker Compose)

```bash
# Start all infrastructure (MongoDB, Kafka, Redis, Kafka-UI, core Spring services)
docker compose up -d

# Tail logs for a specific service
docker compose logs -f auth-service

# Stop everything
docker compose down

# Rebuild a single Spring Boot image
docker compose build api-gateway
docker compose up -d api-gateway
```

---

## Coding Standards

### Java

#### Project conventions

- **Java version**: 21. Use `var`, records, and sealed classes where they improve clarity.
- **Build**: Maven multi-module. The root `pom.xml` is the single source of truth for dependency versions — never declare a version inside a child `pom.xml` for dependencies already managed by the BOM.
- **Packaging**: `com.leafy.<service-name>` (e.g., `com.leafy.authservice`).

#### Spring Boot patterns

- **Dependency injection**: Always use constructor injection via Lombok `@RequiredArgsConstructor`. Never use `@Autowired` on fields.
- **Service layer**: Define a `<Name>Service` interface and a `<Name>ServiceImpl` implementation class in the same package. Annotate the impl with `@Service`.
- **DTOs**: Use Java **Records** for all request/response DTOs (immutable, no boilerplate).
  ```java
  public record CreateFarmRequest(String name, String location) {}
  public record FarmResponse(String id, String name, String location) {}
  ```
- **Entities/Documents**: Use `@Document` for MongoDB entities. Extend `BaseModel` from `common` for `createdAt`/`updatedAt`.
- **Mapping**: Use **MapStruct** (`@Mapper`) for entity ↔ DTO conversion. Never map manually in controllers or services.
- **Validation**: Annotate request DTOs/records with Bean Validation (`@NotBlank`, `@NotNull`, etc.) and drive validation from controllers using `@Valid`.
- **Exception handling**: Throw `AppException(ErrorCode)` from `common`. Do not create new exception types unless `ErrorCode` is genuinely insufficient.
- **Responses**: Always wrap results in `ApiResponse<T>` from `common`. Never return raw domain objects from controllers.
- **Naming**:
  - Controllers: `<Resource>Controller`
  - Services: `<Resource>Service` / `<Resource>ServiceImpl`
  - Repositories: `<Entity>Repository extends MongoRepository`
  - Mappers: `<Entity>Mapper`

#### Kafka / Event-driven (Outbox Pattern)

- Produce events using `OutboxEventPublisher` from `common`. Never publish directly to KafkaTemplate inside a service.
- Event payload classes live in `common/src/main/java/com/leafy/common/event/`.
- Consumer classes go inside the consuming service under a `consumer/` or `listener/` sub-package.
- Keep event payloads stable; adding fields is safe, removing/renaming is a **breaking change**.

#### Security

- JWT validation is handled by the API Gateway and `SecurityContextFilter` from `common`. Individual services extract claims via `UserPrincipal` / `ServiceSecurityUtils`.
- Never log or return raw JWT tokens.
- Service-to-service calls use Feign clients with `FeignConfig` from the calling service.

### Python (FastAPI)

#### Project conventions

- **Python version**: 3.11+.
- **Dependency management**: `pip` + `requirements.txt`. Pin versions (`>=` is acceptable; avoid unpinned `*`).
- **Virtual environment**: `.venv/` inside each service directory. Never commit `.venv/`.
- **Configuration**: `pydantic-settings` (`BaseSettings`) only. All secrets via environment variables / `.env` file. Never hard-code credentials.

#### FastAPI patterns

- **Schemas / DTOs**: Define all request and response models as `pydantic.BaseModel` subclasses in `app/dto/` or `app/schemas.py`.
- **Type hints**: Required on every function signature — parameters, return types, and local variables where the type isn't obvious.
  ```python
  async def get_treatment_plan(plan_id: str) -> TreatmentPlanResponse:
      ...
  ```
- **Routers**: Each resource owns a router in `app/controllers/<resource>_controller.py`. Register routers in `app/main.py`.
- **Services**: Business logic lives in `app/services/`. Keep controllers thin (validate input → call service → return response).
- **Repositories**: Database access lives in `app/repositories/`. Services call repositories, never the reverse.
- **Exception handling**: Register handlers via `register_exception_handlers(app)` (already wired in `app/main.py`). Raise `HTTPException` or custom exceptions defined in `app/exceptions/`.
- **Async**: Prefer `async def` for route handlers and I/O-bound operations.

#### Testing

- Test files: `tests/` directory at service root, mirroring `app/` structure.
- Use `pytest` with `pytest-asyncio` for async tests.
- Mock external calls (MongoDB, Qdrant, LLM APIs) with `unittest.mock` or `pytest-mock`.
- Minimum: one happy-path and one error-path test per endpoint.

---

## Inter-Service Communication Rules

### Synchronous (REST via Feign / OpenFeign)

- **Feign clients only**: Service-to-service REST calls must go through a `@FeignClient` interface in the calling service under a `client/` package. No raw `RestTemplate` or `WebClient` for internal calls.
- Feign error handling is centralised in `FeignErrorDecoder` (common). Do not add try/catch around Feign calls for expected HTTP error codes.

### Asynchronous (Kafka)

- **Outbox Pattern**: The producing service inserts an `OutboxEvent` in MongoDB as part of the same transaction, and `OutboxEventPublisher` (common) polls and publishes to Kafka.
- **Topic naming convention**: `leafy.<domain>.<event-type>` in lowercase kebab-case (e.g., `leafy.account.registered`).
- **Schema changes**: When you modify a Java Kafka event class in `common/event/`:
  1. Version the event class if the change is breaking (e.g., `AccountRegisteredEventV2`).
  2. Update every consumer service that listens to that topic.
  3. Re-run `./mvnw clean install -pl common -DskipTests` before building dependent services.

### Python ↔ Java contract sync

- The `rag-service` and `disease-detection-service` are called by Java services via REST (through the API Gateway or directly via Eureka).
- **When a Java request/response DTO changes** that is consumed or produced by a Python service: update the corresponding Pydantic model in the Python service's `app/dto/` or `app/schemas.py` **in the same commit / PR**.
- Field naming: Java uses `camelCase`; Pydantic models must use `alias_generator = to_camel` or explicit `Field(alias=...)` to match Java serialisation automatically.

---

## Critical Boundaries

The following actions require **explicit user confirmation** before proceeding:

1. **Database schema / index changes**: MongoDB uses schemaless documents, but any change to `@Document` field names or index annotations that would make existing data unreadable must be discussed first. Never drop a MongoDB collection or remove a field from an existing document class without confirmation.

2. **Kafka topic/event schema changes**: Removing or renaming a field in any class under `common/event/` is a breaking change. Always ask before making such modifications.

3. **`common` module breaking changes**: `common` is a shared library. Any change to public APIs in `common` (exception codes, response wrappers, security filters, event types) affects all services simultaneously. Confirm before changing public signatures.

4. **New dependencies**: Do not add a new Maven dependency to any `pom.xml`, or a new entry to any `requirements.txt`, without asking first. For Maven, always check the root BOM first — the dependency may already be managed.

5. **Secrets and credentials**: Never commit `.env` files, credentials, API keys, or JWT secrets. The `.env` template must use placeholder values only (e.g., `JWT_SECRET=changeme`). Never log secret values.

6. **Docker Compose infrastructure changes**: Do not modify `docker-compose.yml` ports, volume names, or network names — this affects all developers' local environments.

7. **Security configuration**: Do not modify `SecurityConfig.java`, `SecurityContextFilter.java`, or `api-gateway` route security rules without explicit instruction.

8. **`git push --force`, `git reset --hard`, branch deletion**: Always ask before any destructive git operation.

---

## Environment Variables Reference

### Java Services (injected via Docker Compose / Config Server)

| Variable                                         | Description                          |
| ------------------------------------------------ | ------------------------------------ |
| `JWT_SECRET`                                     | HS256 signing key — minimum 256 bits |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`           | Eureka server URL                    |
| `SPRING_CONFIG_IMPORT`                           | Config Server import URI             |
| `SPRING_DATA_MONGODB_URI`                        | MongoDB connection string            |
| `SPRING_DATA_REDIS_HOST` / `_PORT` / `_PASSWORD` | Redis connection                     |
| `SERVER_PORT`                                    | Service listen port                  |

### Python Services (`.env` file at service root)

| Variable                               | Service     | Description                               |
| -------------------------------------- | ----------- | ----------------------------------------- |
| `SERVER_PORT` / `SERVER_PORT_RAG`      | rag-service | Uvicorn listen port                       |
| `SPRING_APPLICATION_NAME`              | rag-service | Eureka app name                           |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | rag-service | Eureka server URL                         |
| `MONGODB_URI`                          | rag-service | MongoDB connection string                 |
| `QDRANT_URL`                           | rag-service | Qdrant vector DB URL                      |
| `OPENAI_API_KEY`                       | rag-service | OpenAI / LLM API key                      |
| `LANGCHAIN_TRACING_V2`                 | rag-service | Enable LangSmith tracing (`true`/`false`) |
| `LANGCHAIN_PROJECT`                    | rag-service | LangSmith project name                    |
| `TAVILY_API_KEY`                       | rag-service | Tavily web-search API key                 |

---

## Project-wide Conventions

- **No secrets in source code**: Use environment variables. `.env` files are git-ignored.
- **No `@Autowired` field injection** in Java — constructor injection via `@RequiredArgsConstructor` only.
- **No `any` type** in Python — every function must be fully typed.
- **Logging**: Use SLF4J (`LoggerFactory.getLogger`) in Java; `logging.getLogger(__name__)` in Python. Never use `System.out.println` or `print()` for application-level logging.
- **API versioning**: All REST endpoints are prefixed with `/api/v1/`.
- **Health checks**: Every Spring Boot service exposes `/actuator/health`. The `rag-service` exposes `/health` via the FastAPI router.
- **OpenAPI docs**: Spring services use `springdoc-openapi`. FastAPI services expose `/docs` (Swagger UI) and `/redoc` automatically.
