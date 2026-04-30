# Backend Module Audit And Frontend Reconciliation

Date: 2026-04-18

Source-of-truth inputs inspected:

- Backend source under `Leafy_BE`, including controllers, DTOs, services, repositories/models, gateway/config, and selected support code.
- Current frontend audit at `Leafy_FE/docs/frontend/frontend-api-integration-audit.md`.

This report intentionally does not treat old implementation summaries as authoritative. Where older notes said a UI was mock but current source shows backend-driven frontend behavior, the current code wins.

## 1. Executive Summary

The backend is strongest around the IoT monitoring workflow. The `iot-metrics-collector-service` now has production-usable APIs and service implementations for dashboard overview, zone overview, device detail, charts, alert listing/detail, alert acknowledge/resolve, alert-rule CRUD, device provision/claim/my-devices, and device config update/push/ack. The current frontend already uses many of these, so dashboard, zone metrics, alert listing, device detail/config, alert-rule management, and device onboarding are mostly end-to-end.

The main remaining system gaps are not evenly distributed:

1. Alert lifecycle UI is missing even though backend acknowledge/resolve exists.
2. Device/farm management remains local/mock in frontend even though `farm-service` and IoT device inventory APIs exist; the contract is awkward because farm ownership uses explicit `ownerProfileId`.
3. Push notification frontend/backend contract is mismatched: backend exposes only `POST /push-tokens`, no deactivate endpoint, and gateway routing does not currently expose `/push-tokens` cleanly under the configured `/api/notifications/**` route.
4. Notification inbox/read APIs are absent even though push delivery logs are persisted.
5. Demo/operator backend tooling exists in `iot-test-data-service`, but the gateway has no route and the frontend has no operator UI.

Backend-ready but frontend-missing areas include community feed/post/comment/vote, file upload/presigned download for avatars/media, search over posts/profiles, disease prediction/history, RAG chat/ingestion/treatment plans, plant/species/events/treatment-plan management, and auth session/device management.

The most important cross-service blockers are identity propagation (`X-User-Id`, `X-Profile-Id`, roles), gateway route consistency, farm/profile/IoT ID alignment, and the missing IoT-to-notification alert event publisher.

## 2. Module Landscape Overview

| Module | Primary domain responsibility | Public frontend-facing? | Current maturity | Notes |
|---|---|---:|---|---|
| `iot-metrics-collector-service` | IoT devices, telemetry ingest/query, dashboards, alerts, alert rules, config push | Yes | High | Core IoT UI is mostly backend-ready and partly integrated. Alert lifecycle backend exists; push notification event emission is not present. |
| `farm-service` | Farm plots and zones | Yes | Medium | CRUD/list APIs exist, but ownership is passed as `ownerProfileId` rather than derived from auth. Good enough for pickers and local-screen replacement with integration work. |
| `profile-service` | Profiles, preferences, certificates, profile search sync | Yes | High | Profile/settings backend is broad. Frontend uses profile partially and underuses preferences/certificates. |
| `notification-service` | Email sending, push token register, Firebase push delivery from Kafka alert events | Partly | Medium-low | Push register exists; deactivate and notification inbox/read endpoints are missing. Gateway route appears mismatched with controller paths. |
| `auth-service` | Register/OTP/login/refresh/logout/session/device/user account management | Yes | High | Web/mobile auth and device-session flows are implemented; frontend underuses logout-device/session APIs. |
| `community-feed-service` | Posts, feed, comments, votes/shares, search indexing events | Yes | Medium-high | Can replace local community UI for feed/post/comment/vote. More product widgets may still need aggregation APIs. |
| `file-service` | S3-backed upload/download/presigned URLs and file metadata | Yes | Medium-high | Ready for avatar/media upload, but needs frontend integration and ownership/product decisions. |
| `search-service` | Elasticsearch post/profile search and sync/reindex | Yes | Medium | Backend can support search and experts discovery. Depends on indexed data and gateway path conventions. |
| `rag-service` | RAG chat, document ingestion, treatment-plan persistence | Yes | Medium | FastAPI endpoints exist for chat/stream/ingest/tasks/treatment plans. No current frontend route. |
| `plant-management-service` | Plants, species, plant events, treatment plans | Yes | Medium-high | CRUD/query APIs exist and can support monitor/reports/planning, but frontend lacks screens. |
| `disease-detection-service` | Leaf detection, disease prediction, diagnose history | Yes | Medium | FastAPI inference/history endpoints exist. Frontend has no visible disease monitor/report route wired. |
| `api-gateway` | JWT validation, header injection, service routing, health/fallbacks | Yes | Medium | Injects identity headers correctly. Route coverage is incomplete/mismatched for notification and absent for test-data. |
| `common` | Shared DTOs, security context, JWT utilities, outbox events | Internal | High | Enables gateway-header auth and Kafka outbox. Does not include IoT alert event type/publisher. |
| `iot-test-data-service` | Non-prod IoT demo bootstrap, history seeding, live simulation, scenarios | Operator-facing | Medium-high | Useful for operator UI, but not routed through gateway and guarded for non-prod only. |
| `config-server` | Centralized Spring config | Infra | Medium-high | Provides gateway/service configs; some route gaps originate here. |
| `discovery-server` | Eureka service registry | Infra | High | Standard registry only. |

## 3. Module-by-Module Audit

### 3.1 `iot-metrics-collector-service`

**What it owns**

Owns the IoT runtime domain: devices, device claims, telemetry/status ingestion, latest readings, chart aggregates, dashboard summaries, alert events, alert-rule definitions, device config snapshots, MQTT config push, and MQTT config acknowledgements.

**Public API surface**

Controllers inspected under `iot-metrics-collector-service/src/main/java/com/leafy/iotmetricscollectorservice/controller`:

- `DashboardController`: `GET /iot/dashboard/overview`, `GET /iot/farm-zones/{zoneId}/overview`, `GET /iot/devices/{deviceId}/detail`.
- `TelemetryQueryController`: `GET /iot/devices/{deviceId}/latest-readings`, `GET /iot/devices/{deviceId}/charts`, `GET /iot/farm-zones/{zoneId}/charts`.
- `AlertController`: `GET /iot/alert-events`, `GET /iot/alert-events/{alertEventId}`, `POST /iot/alert-events/{alertEventId}/acknowledge`, `POST /iot/alert-events/{alertEventId}/resolve`.
- `AlertRuleController`: `GET/POST /iot/alert-rules`, `GET/PUT/DELETE /iot/alert-rules/{ruleId}`, `PATCH /iot/alert-rules/{ruleId}/enabled`.
- `DeviceController`: `POST /iot/devices/provision`, `POST /iot/devices/{deviceId}/claim-code`, `POST /iot/devices/claim`, `GET /iot/devices/me`, `GET/PUT /iot/devices/{deviceId}/config`, `POST /iot/devices/{deviceId}/config/push`.
- `TelemetryIngestController`: `POST /iot/devices/{deviceUid}/telemetry`, `POST /iot/devices/{deviceUid}/status`.

**Key implemented flows**

- Device onboarding is real: `DeviceServiceImpl` provisions unique `deviceUid`/`deviceCode`, generates 15-minute claim codes, validates claim state, binds owner/farm plot/zone, and exposes filtered paged `/iot/devices/me`.
- Dashboard and telemetry reads are real: `DashboardQueryServiceImpl` counts devices/zones/open alerts from repositories, pulls latest readings, media summaries, and config snapshots; `TelemetryQueryServiceImpl` uses aggregate repositories for chart ranges.
- Device config is real: `DeviceConfigServiceImpl` creates defaults, validates intervals, increments version, resets ack fields; `DeviceConfigPushServiceImpl` publishes MQTT config and marks SENT/FAILED; `DeviceConfigAckServiceImpl` consumes MQTT `ack` messages and marks ACKED/FAILED when version matches.
- Alerts are real: telemetry ingest persists sensor readings, updates latest readings, and calls `AlertEvaluationServiceImpl`, which evaluates enabled rules, applies scope/cooldown, and creates `AlertEvent` rows.
- Alert lifecycle is real: `AlertLifecycleServiceImpl` supports OPEN -> ACKNOWLEDGED and OPEN/ACKNOWLEDGED -> RESOLVED with timestamps.
- Alert rules are real: `AlertRuleServiceImpl` validates sensor type, thresholds, scope, cooldown, owner, filters, paging, sorting, enable/disable, update, and delete.

**Current limitations / missing pieces**

- Alert push delivery is incomplete end-to-end. `AlertEvent.pushSent` exists and notification-service listens for Kafka topic `iot.alert.triggered`, but source search found no IoT publisher for `AlertTriggeredEvent`, `KafkaTemplate`, or outbox emission. Alert evaluation only sets `pushSent(false)`.
- Media is modeled (`DeviceMediaEvent`, `DeviceMediaSummaryResponse`) and dashboard returns latest media, but there is no public media ingest controller. MQTT config subscribes to `coffee/prod/devices/+/image/meta`, but inspected controller/API surface does not expose direct media ingestion.
- Device inventory supports list/claim/provision/config, but not full user-facing rename/move/retire/delete operations beyond claim/config.
- Several user-scoped APIs still depend directly on `X-User-Id`. Behind the gateway this is correct, but direct service calls or frontend-added headers are brittle.
- Alert rule scope validates `deviceId` existence but does not validate `zoneId`/`farmPlotId` against farm-service ownership.

**Frontend relevance**

Already used by current frontend for dashboard overview, zone overview/charts, alert list/detail, device detail, config get/update/push, alert-rule CRUD/enable/delete, onboarding claim, and `/iot/devices/me`. Backend is ahead of frontend for alert acknowledge/resolve and config ack status visibility.

**Integration notes**

Depends on MQTT broker, PostgreSQL, gateway identity headers, and externally consistent farm plot/zone UUIDs. It is not currently the publisher that completes notification push delivery.

### 3.2 `farm-service`

**What it owns**

Farm plots and farm zones stored in MongoDB.

**Public API surface**

- `FarmPlotController`: `POST /farms/plots`, `GET /farms/plots?ownerProfileId=...`, `GET/PUT/DELETE /farms/plots/{id}`, `GET /farms/plots/admin`.
- `FarmZoneController`: `POST /farms/plots/{farmPlotId}/zones`, `GET /farms/plots/{farmPlotId}/zones`, `GET/PUT/DELETE /farms/zones/{id}`, `GET /farms/admin/zones`.
- `FarmSeederController` exists for seeded data.

**Key implemented flows**

- Plot CRUD with generated unique plot codes and soft-delete via `active=false`.
- Zone CRUD/list per plot with duplicate zone-name validation within a plot.
- Models include farm area, address, location, boundary GeoJSON, crop/soil metadata.

**Current limitations / missing pieces**

- User ownership is not derived from security context. The list contract requires `ownerProfileId` from the caller, and create accepts owner data from the request.
- No dedicated "my plots" endpoint using `X-Profile-Id`.
- No combined picker endpoint returning plots and zones in one call.
- No validation that IoT-collector farm/zone references exist at IoT rule/device-claim time.

**Frontend relevance**

Backend is ready enough to replace the local `/dashboard/devices` farm/zone/device management mock for farm/zone CRUD and pickers. The frontend gap is mostly UI/integration, with a contract cleanup recommended for authenticated "my farm" calls.

**Integration notes**

Farm-service has a `ProfileServiceClient`, but the inspected plot/zone service implementation does not use it for ownership validation in the main CRUD paths.

### 3.3 `profile-service`

**What it owns**

User profiles, user preferences/settings, certificate/approval requests, profile synchronization to search, internal profile lookup.

**Public API surface**

- `ProfileController`: profile CRUD/admin, `GET /profiles/me`, `GET/PUT /profiles/user/{userId}`, `GET /profiles/active`, `GET /profiles/search`, activate/deactivate/verify, existence checks.
- `UserPreferenceController`: `GET /preferences/me`, `GET /preferences/user/{userId}`, section-level PATCH endpoints for general/security/privacy/sync/appearance/message/notification/utilities, section GETs, and reset.
- `CertificateController`: submit approval requests and admin approval status changes.
- `ProfileSyncController`: admin profile sync start/resume/status.
- `InternalProfileController`: internal create and lookup APIs for auth/search/community support.

**Key implemented flows**

- Auth-service creates profile synchronously during registration via internal profile client.
- Profile responses can be enriched with auth user data and approved certificates.
- Preferences are a real backend concept rather than only frontend local settings.
- Profile events/outbox support search/community denormalization.

**Current limitations / missing pieces**

- Avatar/profile picture fields exist, but no direct profile-controller upload flow; file-service must be integrated separately and then profile updated with the resulting URL/id.
- Some preference endpoints use the security context but are not currently consumed by frontend settings.
- Admin-heavy profile list/search endpoints are not relevant to normal user settings without UI decisions.

**Frontend relevance**

Frontend profile/settings is partially integrated. Backend is ready for more settings persistence than the frontend currently uses, including notification/appearance/security/privacy preferences.

**Integration notes**

Relies on gateway `X-User-Id`, `X-Profile-Id`, and role headers through `common` security filters.

### 3.4 `notification-service`

**What it owns**

Push token storage, Firebase push dispatch, notification delivery logs, and Brevo email sending.

**Public API surface**

- `PushTokenController`: `POST /push-tokens` only.
- `InternalPushController`: `POST /internal/push/test`.
- `MailingController`: `/mailing/send`, `/send-template`, `/send-simple`, `/send-bulk`, `/send-welcome`, `/send-password-reset`, `/send-otp`, `/send-notification`, `/status`, `/health`.
- `InternalMailingController`: `POST /internal/mailing/send`.

**Key implemented flows**

- `PushTokenService` upserts FCM tokens by token value, sets `userId`, `platform`, `deviceIdentifier`, active flag, and timestamps.
- `AlertTriggeredConsumer` listens to `${notification.kafka.topics.alertTriggered}` (`iot.alert.triggered` in config).
- `PushNotificationService` finds active user tokens, sends Firebase pushes, deduplicates by event/user/token, and writes `NotificationLogDocument` status as SENT or FAILED.
- Email service supports simple, template, bulk, welcome, password reset, OTP, and notification emails.

**Current limitations / missing pieces**

- No public deactivate/delete endpoint for push tokens. The frontend push-token deactivate wrapper is not backed by current source.
- No notification inbox/list/read/read-all API, even though `NotificationLogDocument` is persisted.
- Gateway config routes only `/api/notifications/**` to this service, but controllers are rooted at `/push-tokens` and `/mailing`. With `StripPrefix=1`, `/api/notifications/...` becomes `/notifications/...`, which does not match inspected controllers.
- Push delivery from IoT alerts is only complete if another publisher emits `iot.alert.triggered`; the inspected IoT collector does not.

**Frontend relevance**

Frontend push registration can work only if it calls the service at a path that actually reaches `/push-tokens`. Deactivation and notification inbox are backend gaps. Alert push is a cross-service gap.

**Integration notes**

Depends on Firebase credentials, MongoDB, Kafka, and a correct upstream alert event publisher.

### 3.5 `auth-service`

**What it owns**

Registration/OTP, login, JWT access/refresh token lifecycle, logout/session revocation, web/mobile refresh behavior, auth-device management, user account CRUD/admin, and internal user/account lookup.

**Public API surface**

- `AuthController`: `/auth/register/init`, `/auth/register/verify`, `/auth/register/resend-otp`, `/auth/login`, `/auth/refresh`, `/auth/refresh/mobile`, `/auth/logout`, `/auth/logout/mobile`, `/auth/logout-device`, `/auth/logout-other`.
- `DeviceController`: `/api/v1/devices` list/remove/remove-others for auth devices.
- `UserController`: `/users/me`, user CRUD/admin, active/search, activate/deactivate, change password, check email/phone.
- Internal controllers: `/internal/accounts/{userId}`, `/internal/users/{userId}`.

**Key implemented flows**

- Registration verifies OTP, creates account, creates profile synchronously, and issues tokens.
- Login registers/updates auth device from user agent/device id and binds refresh sessions.
- Refresh validates refresh token, Redis session, blacklist, rotates refresh token, and blacklists old refresh JTI.
- Logout blacklists access/refresh tokens and clears refresh cookie for web.
- Logout specific device and logout-other are implemented through refresh-session/device records.

**Current limitations / missing pieces**

- Cookie path is `/api/v1/auth` in `AuthServiceImpl`, while gateway auth route is `/api/auth/**`. This should be verified in runtime because a path mismatch can prevent refresh cookies from being sent to `/api/auth/refresh`.
- `DeviceController` is rooted `/api/v1/devices`, but gateway routes `/api/devices/**` with `StripPrefix=1`, yielding `/devices/**`, not `/api/v1/devices/**`. This suggests auth-device management may not be gateway-reachable through configured routes.

**Frontend relevance**

Login/register/refresh are already used by frontend auth. Logout/session/device management are backend-ready but underused by frontend settings/security UI.

**Integration notes**

Profile creation during registration makes `X-Profile-Id` possible in access tokens. Gateway extracts profile id and injects it downstream.

### 3.6 `community-feed-service`

**What it owns**

Community posts, feed, comments/replies, votes, shares, denormalized profile summaries, and search indexing events.

**Public API surface**

- `PostController`: `POST /posts`, `GET /posts/{id}`, `GET /posts/feed`, `GET /posts/user/{userId}`, `PUT /posts/{id}`, `DELETE /posts/{id}`.
- `CommentController`: create/get/update/delete comments, list comments by post, list replies by parent comment.
- `VoteController`: `POST /votes/{targetType}/{targetId}?type=...`, `GET /votes/posts/{postId}?type=...`.
- `InternalPostController` and seeder endpoints exist for service/internal/demo support.

**Key implemented flows**

- Post creation uses `ServiceSecurityUtils.getCurrentProfileId()` as author.
- Feed returns FEED and SHARE post types with author/shared-post enrichment.
- Shares are represented through `PostType.SHARE`, `sharedPostId`, `originalAuthorId`, and shared caption.
- Vote service toggles, switches, or deletes votes and emits outbox vote events.
- Post indexing decorator publishes post upsert/delete outbox events for search.

**Current limitations / missing pieces**

- No dedicated trending topics, recommended experts, groups, or "online experts" aggregation endpoint was found.
- Media is URL/type only; upload/persistence must be handled by file-service or another media pipeline.
- Some controller naming still uses `userId`, but service methods operate on `profileId` authors; frontend should treat this as profile id unless contract is clarified.

**Frontend relevance**

Frontend community is still local/mock despite backend being ready for core feed, post, comment, vote, and share flows. Advanced sidebar widgets may need additional APIs or local derivation.

**Integration notes**

Depends on gateway identity headers, profile-summary sync events, and file-service for media upload.

### 3.7 `file-service`

**What it owns**

S3-backed file upload/download, presigned URLs, and MongoDB metadata.

**Public API surface**

`FileController` exposes:

- `POST /files/upload` multipart upload to S3 plus metadata creation.
- `GET /files/download/{fileId}`, `GET /files/download/s3-key/{s3Key}`.
- `GET /files/presigned-url/{fileId}`.
- Metadata CRUD/query: `POST /files`, `PUT /files/{fileId}`, `GET /files/{fileId}`, details, S3-key lookup, list, active list, user list, search, delete/deactivate/activate, S3-key existence.

**Key implemented flows**

- `S3ServiceImpl` uploads, downloads, deletes, and creates presigned GET URLs.
- `FileServiceImpl` stores uploader from reactive security context and supports metadata search/list/soft-delete.

**Current limitations / missing pieces**

- The service does not directly attach uploaded files to profiles/posts/IoT media. Callers must update those domain records after upload.
- Ownership checks for reading/updating files are not strongly visible in `FileController`; this should be reviewed before exposing broad file-management screens.

**Frontend relevance**

Ready to wire profile avatar upload and community media upload. Also useful for future IoT media display if IoT media references are connected.

**Integration notes**

Reactive security context depends on gateway headers. S3 credentials/bucket config must be present.

### 3.8 `search-service`

**What it owns**

Elasticsearch-backed post and profile search plus sync/reindex support and failed-event tracking.

**Public API surface**

- `PostSearchController`: `GET /posts/search?searchTerm=&postType=&authorId=&page=&size=&sortBy=&sortDir=`.
- `ProfileSearchController`: `GET /profiles/search?searchTerm=&role=&isVerified=&specialty=&page=&size=&sortBy=&sortDir=`.
- `PostSyncController`: `POST /sync/posts/reindex`, `POST /internal/search/posts/reindex`, `POST /sync/posts/reset`, `POST /internal/posts/reset`.
- `ProfileSyncController`: `POST /sync/bulk`, `POST /internal/search/sync/bulk`, `POST /sync/reindex`, `POST /internal/search/sync/reindex`.
- `FailedEventController` exists for failed indexing events.

**Key implemented flows**

- Profile and post search endpoints are frontend-usable.
- Post/community and profile-service can publish/sync index updates.
- Reindex/reset endpoints can rebuild indexes.

**Current limitations / missing pieces**

- Search is only as complete as index population. Frontend search route is missing, so no user-facing validation currently exists.
- Gateway maps `/api/search/posts/**` and `/api/search/profiles/**` with `StripPrefix=2`, so `/api/search/posts/search` becomes `/posts/search`, matching the controller.

**Frontend relevance**

Backend can support declared-but-missing search and experts pages today, at least for post/profile discovery. Reports/monitor need other modules too.

### 3.9 `rag-service`

**What it owns**

RAG-powered agricultural chat, streaming chat, document ingestion into vector DB, ingestion task status, and treatment-plan persistence/listing.

**Public API surface**

FastAPI routers under `/rag/v1`:

- `POST /rag/v1/chat`.
- `POST /rag/v1/chat/stream` using server-sent events.
- `POST /rag/v1/ingest` for PDF/DOCX/TXT upload with background processing.
- `GET /rag/v1/tasks`, `GET /rag/v1/tasks/{task_id}`.
- `GET /rag/v1/treatment-plans/`, `GET /rag/v1/treatment-plans/{plan_id}`, `DELETE /rag/v1/treatment-plans/{plan_id}`.
- `GET /rag/health`.

**Key implemented flows**

- `ChatService` runs a LangGraph RAG pipeline, supports thread ids, persists generated treatment plans, and streams graph state/response chunks.
- Ingestion validates file size/type, hashes for dedupe, stores temporary upload, and indexes via background worker.
- Treatment-plan read/delete is owner/admin scoped.

**Current limitations / missing pieces**

- Ingestion task tracking is in-memory, so task history may not survive service restart.
- Frontend has no visible RAG/chat/operator route in the current audit.
- It depends on LLM provider configuration, vector DB, MongoDB, and gateway identity headers.

**Frontend relevance**

Backend is ready for an expert assistant/chat or treatment-plan UI. Current frontend routes for experts/reports are declared/missing, not using RAG.

### 3.10 `plant-management-service`

**What it owns**

Plant records, species catalog, plant events/calendar, and treatment plans.

**Public API surface**

- `PlantController`: plant CRUD/list, by species, by farm plot.
- `SpeciesController`: species CRUD/list and Perenual seeding.
- `PlantEventController`: event CRUD, bulk import, by plant/type/planned/plan/farm plot/farm zone, calendar range.
- `TreatmentPlanController`: create, get, my plans, by plant/farm plot/farm zone, update status, delete.

**Key implemented flows**

- Can represent plant inventory and farm-zone/plot association.
- Plant events can support calendar/monitor/report views.
- Treatment plans can be created independently and queried by current user/status.

**Current limitations / missing pieces**

- Current frontend has no plant management, monitor, or report page using these APIs.
- Cross-service consistency with farm-service plot/zone ids is caller-managed.
- Some controller comments show encoding artifacts, but functionality is readable.

**Frontend relevance**

Backend supports a future monitor/reports/plant calendar experience. Those frontend routes are currently placeholders/missing pages.

### 3.11 `disease-detection-service`

**What it owns**

Leaf detection, image-based disease prediction, and diagnose request/result history.

**Public API surface**

FastAPI under `/diseases`:

- `POST /diseases/predict`, `POST /diseases/predict/tflite`, `GET /diseases/predict/health`.
- `POST /diseases/detect-leaf`, `GET /diseases/detect-leaf/health`, `POST /diseases/detect-leaf/visualize`, `POST /diseases/detect-leaf/crop`.
- `GET /diseases/diagnose/requests`, `GET /diseases/diagnose/requests/{id}`, `DELETE /diseases/diagnose/requests/{id}`.
- `GET /diseases/diagnose/results`, `GET /diseases/diagnose/results/by-request/{id}`.
- `GET /diseases/test/user-info`.

**Key implemented flows**

- Prediction validates image uploads, runs Keras/TFLite inference, stores diagnose request/result records in MongoDB, and returns top-K predictions.
- Diagnose history is owner/admin scoped.
- Leaf detection supports raw detection, visualized output, and cropped output.

**Current limitations / missing pieces**

- Gateway routes to `lb://disease-classification-service`, and the FastAPI app name defaults to `disease-classification-service`; this is consistent by source, though the repo folder is named `disease-detection-service`.
- Frontend has no visible disease monitor/report UI using this module.

**Frontend relevance**

Backend is ready for a disease-detection workflow or reports integration. The current frontend gap is UI/product routing, not a missing backend inference API.

### 3.12 `api-gateway`

**What it owns**

JWT validation, blacklist checks, identity header injection, service routing, CORS, circuit breakers, fallbacks, and system health.

**Public API surface**

- Global `JwtAuthenticationFilter` validates access tokens, checks Redis blacklist, extracts user id/email/role/device/profile/JTI/TTL, and injects `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-JWT-Id`, `X-Device-Id`, `X-Profile-Id`, `X-Remaining-TTL`.
- Public endpoints include `/api/auth/**`, `**/internal/**`, actuator, and OpenAPI paths.
- Gateway routes are defined in config-server `config/api-gateway.yaml`.
- `SystemHealthController`/`SystemHealthService` checks known Eureka services and actuator health.

**Key implemented flows**

- Downstream services can rely on gateway-injected identity headers.
- Main routes exist for auth, users/devices, farms, files, plants/species/events/treatment plans, profiles/preferences, RAG, diseases, IoT, community, search, and messages.

**Current limitations / missing pieces**

- Notification route mismatch: gateway only routes `/api/notifications/**`, while notification controllers are `/push-tokens` and `/mailing`.
- `iot-test-data-service` has no gateway route.
- Auth device management route appears mismatched: gateway `/api/devices/**` strips to `/devices/**`, but controller is `/api/v1/devices`.
- Auth cookie path should be verified: auth-service sets refresh cookie path `/api/v1/auth`, while gateway auth path is `/api/auth/**`.
- Some public/internal route rules are broad (`**/internal/**`), which is convenient for internal calls but should be reviewed for exposure risk.

**Frontend relevance**

The gateway is the intended way to avoid frontend-managed identity headers. Current frontend risks around `X-User-Id` should be resolved by relying on the gateway and removing manual header construction where possible.

### 3.13 `common`

**What it owns**

Shared API responses, errors, security filters, JWT utilities, shared enums/events, outbox event model/publisher/retry support, and common Feign/security helpers.

**Key implemented flows**

- `SecurityContextFilter` turns gateway headers into `UserPrincipal` for servlet services.
- `ServiceSecurityUtils` exposes current account/profile/user principal.
- `JwtUtil` creates/extracts access/refresh tokens with user, role, profile, device, session, and JTI claims.
- Outbox publisher/event model supports profile/community/search indexing flows.

**Current limitations / missing pieces**

- No shared IoT alert-triggered event class was found in common. Notification has its own `AlertTriggeredEvent`, and IoT collector does not publish it.

**Frontend relevance**

Indirect but important: this is why frontend should not provide user identity manually once traffic goes through gateway.

### 3.14 `iot-test-data-service`

**What it owns**

Non-production demo/operator tooling for IoT bootstrap, history seeding, simulation, and alert/config scenarios.

**Public/operator API surface**

- `POST /seed/bootstrap/minimal`, `POST /seed/bootstrap/full`.
- `POST /seed/history/last-7d`, `POST /seed/history/last-30d`.
- `POST /seed/simulation/start`, `POST /seed/simulation/stop`, `GET /seed/simulation/status`.
- `POST /seed/scenarios/high-temperature`, `POST /seed/scenarios/low-soil-moisture`, `POST /seed/scenarios/config-ack-success`, `POST /seed/scenarios/config-ack-failure`.

**Key implemented flows**

- Bootstraps reference data, provisions/claims devices through real collector APIs, creates alert rules through collector APIs, publishes MQTT telemetry/status/config-ack scenarios, and can run a live simulation loop.
- Guarded by `NonProdEnvironmentGuard`; it refuses to run with `prod` profile and only permits configured non-prod profiles.

**Current limitations / missing pieces**

- No gateway route exists for this module in inspected gateway config.
- No frontend operator UI exists.
- This is intentionally non-production; it should not be exposed to normal users.

**Frontend relevance**

Backend is ready enough to justify a future internal operator/demo page, after adding a guarded gateway/admin route.

### 3.15 `config-server`

**What it owns**

Centralized Spring Cloud Config with native classpath configuration.

**Key implemented flows**

- Serves module configs from `src/main/resources/config`.
- Registers with Eureka and exposes health/refresh endpoints.
- Holds the gateway route definitions that determine frontend reachability.

**Current limitations / missing pieces**

- Several routing issues are config-level: notification paths, auth device route, missing test-data route.
- No RAG/disease Spring config files exist because those are FastAPI services using their own settings.

### 3.16 `discovery-server`

**What it owns**

Eureka service discovery.

**Key implemented flows**

- Standard registry on port 8761 with `register-with-eureka=false`, `fetch-registry=false`, and self-preservation disabled.

**Frontend relevance**

None directly, but gateway/service discovery depends on it.

## 4. Backend Capability Matrix

| Business capability | Owning backend module | Public endpoint(s) present? | Implementation status | Frontend currently uses it? | Notes |
|---|---|---:|---|---:|---|
| Login | `auth-service` | Yes: `POST /auth/login` | Ready | Yes | Issues access token and refresh token/cookie. |
| Register + verify OTP | `auth-service` | Yes: `/auth/register/init`, `/auth/register/verify`, resend | Ready | Yes | Creates profile synchronously. |
| Refresh token | `auth-service` | Yes: `/auth/refresh`, `/auth/refresh/mobile` | Ready but cookie path needs verification | Yes | Cookie path may not match gateway path. |
| Logout current session | `auth-service` | Yes: `/auth/logout`, `/auth/logout/mobile` | Ready | Partly | Frontend could use more consistently. |
| Logout device/other sessions | `auth-service` | Yes: `/auth/logout-device`, `/auth/logout-other` | Ready | No/limited | Backend-ready frontend gap. |
| Auth device list/remove | `auth-service` | Controller yes: `/api/v1/devices` | Contract/gateway issue | No | Gateway path appears mismatched. |
| User account read/update | `auth-service` | Yes: `/users/me`, `/users/{id}` | Ready | Partly | Settings may not use all fields. |
| Profile read/update | `profile-service` | Yes: `/profiles/me`, `/profiles/user/{id}` | Ready | Yes/partly | Avatar requires file integration. |
| User preferences/settings | `profile-service` | Yes: `/preferences/*` | Ready | Mostly no | Theme/settings are still local/visual in frontend. |
| Certificate/approval | `profile-service` | Yes: `/profiles/{profileId}/approval-requests` | Ready | No | Future profile/expert verification UI. |
| Push token register | `notification-service` | Yes: `POST /push-tokens` | Backend exists, gateway route questionable | Yes/partly | Frontend wrapper exists; route must be verified. |
| Push token deactivate | `notification-service` | No | Missing backend | Frontend wrapper expected | Confirmed not found in source. |
| Notification inbox/read | `notification-service` | No | Missing backend | No | Logs exist but no list/read API. |
| Alert push dispatch | `notification-service` + IoT | Consumer yes, publisher not found | Incomplete cross-service | No | IoT does not publish `iot.alert.triggered` by inspected source. |
| Dashboard overview | `iot-metrics-collector-service` | Yes: `/iot/dashboard/overview` | Ready | Yes | Backend-driven current frontend. |
| Zone overview | `iot-metrics-collector-service` | Yes: `/iot/farm-zones/{zoneId}/overview` | Ready | Yes | Uses latest readings/media/alerts. |
| Device/zone charts | `iot-metrics-collector-service` | Yes | Ready | Yes | Aggregate repositories support ranges. |
| Device detail | `iot-metrics-collector-service` | Yes: `/iot/devices/{deviceId}/detail` | Ready | Yes | Includes latest readings, alert summary, config snapshot, media. |
| Config read/update/push | `iot-metrics-collector-service` | Yes: `/iot/devices/{id}/config`, `/push` | Ready | Yes | MQTT push and ack state implemented. |
| Config ack ingest | `iot-metrics-collector-service` | MQTT ack topic | Ready internal/device path | Display partly | Frontend can poll config after push. |
| Alert list/detail | `iot-metrics-collector-service` | Yes: `/iot/alert-events` | Ready | Yes | Paginated/filterable backend. |
| Alert acknowledge/resolve | `iot-metrics-collector-service` | Yes | Ready | No | Frontend-only gap. |
| Alert rule CRUD/enable | `iot-metrics-collector-service` | Yes: `/iot/alert-rules` | Ready | Yes | Scope validation is partial for farm/zone ownership. |
| Device provision/claim | `iot-metrics-collector-service` | Yes | Ready | Claim yes | Provision likely operator/backend tooling. |
| My IoT devices | `iot-metrics-collector-service` | Yes: `/iot/devices/me` | Ready | Yes in onboarding; not old inventory | Replace local device screen. |
| Farm plot CRUD/list | `farm-service` | Yes | Ready but contract awkward | No | Use for pickers/local screen replacement. |
| Farm zone CRUD/list | `farm-service` | Yes | Ready but contract awkward | No | Needs authenticated my-zones or frontend profile id. |
| Community feed/posts | `community-feed-service` | Yes | Ready | No | Frontend remains local/mock. |
| Comments/replies | `community-feed-service` | Yes | Ready | No | Backend-ready frontend gap. |
| Votes | `community-feed-service` | Yes | Ready | No | Backend-ready frontend gap. |
| File upload/download | `file-service` | Yes | Ready | No/limited | Needed for avatar/community media. |
| Post search | `search-service` | Yes: `/posts/search` via `/api/search/posts/search` | Ready if indexed | No | Missing frontend search page. |
| Profile/expert search | `search-service` | Yes: `/profiles/search` via `/api/search/profiles/search` | Ready if indexed | No | Could back experts route. |
| Disease prediction | `disease-detection-service` | Yes: `/diseases/predict` | Ready if models loaded | No | Missing frontend disease/report UI. |
| Diagnose history | `disease-detection-service` | Yes | Ready | No | Owner/admin scoped. |
| RAG chat/stream | `rag-service` | Yes: `/rag/v1/chat`, `/chat/stream` | Ready if infra configured | No | Missing frontend assistant/experts page. |
| RAG ingestion/tasks | `rag-service` | Yes | Partial: in-memory task tracker | No | Operator/admin knowledge UI needed. |
| Plant/species/events | `plant-management-service` | Yes | Ready | No | Could back monitor/reports. |
| Treatment plans | `plant-management-service`, `rag-service` | Yes | Ready in both contexts | No | Product decision needed on source of truth. |
| Demo bootstrap/simulation | `iot-test-data-service` | Yes service-local | Ready non-prod, no gateway | No | Operator UI/gateway gap. |

## 5. Frontend Reconciliation

### 5.1 Auth

- Backend ready: Yes. Register/init, verify, resend OTP, login, refresh, logout, logout-mobile, logout-device, logout-other, current user, and account management are implemented.
- Frontend using it: Yes for core login/register/refresh. Underuses logout-device/logout-other/auth-device management.
- Missing part: mostly frontend-only and gateway/contract cleanup. Verify cookie path `/api/v1/auth` vs gateway `/api/auth/**`, and auth-device gateway route `/api/devices/**` vs controller `/api/v1/devices`.
- Classification: both ready for core auth; contract gap for advanced session/device management.

### 5.2 Profile / Settings

- Backend ready: Yes for profile read/update and broad preference sections.
- Frontend using it: Profile is partially wired; settings controls remain partly local/visual according to frontend audit.
- Missing part: frontend-only for preferences; file/profile integration for avatar upload.
- Classification: backend ready, frontend partially integrated.

### 5.3 Push Notifications

- Backend ready: Partly. Token register exists and Firebase send exists. Deactivate and inbox/read APIs do not.
- Frontend using it: Push register/deactivate wrappers exist, but deactivate mismatches backend.
- Missing part: backend-only for deactivate and inbox/read; gateway contract gap for exposing `/push-tokens`.
- Classification: contract/backend gap.

### 5.4 Dashboard

- Backend ready: Yes. `/iot/dashboard/overview` is real and repository-backed.
- Frontend using it: Yes. The current frontend audit says dashboard is backend-driven.
- Missing part: none for current overview. Future farm selection should use farm-service pickers.
- Classification: both ready.

### 5.5 Zone Metrics

- Backend ready: Yes. Zone overview and zone chart endpoints are real.
- Frontend using it: Yes. Older mock notes are outdated for current zone metrics.
- Missing part: farm/zone picker cleanup if the user should navigate across owned zones.
- Classification: both ready for current screen.

### 5.6 Alerts

- Backend ready: Yes for list/detail/filter/page and lifecycle acknowledge/resolve.
- Frontend using it: Read-only list/detail uses backend; lifecycle buttons are absent/not wired.
- Missing part: frontend-only for ack/resolve UI/mutations; backend already provides endpoints.
- Classification: backend ready, frontend partially integrated.

### 5.7 Device Detail / Config

- Backend ready: Yes. Detail/config get/update/push and MQTT ack lifecycle are implemented.
- Frontend using it: Yes for detail/config/push; audit notes polling after push exists in frontend.
- Missing part: stronger UI surfacing of ack/failure state if not already complete; no backend blocker.
- Classification: mostly end-to-end ready.

### 5.8 Alert Rules

- Backend ready: Yes for CRUD/list/filter/enable/delete.
- Frontend using it: Yes.
- Missing part: contract quality: rule create/update can accept arbitrary zone/farmPlot IDs without cross-service ownership validation. Picker data should come from farm-service and IoT devices.
- Classification: both ready, with contract hardening recommended.

### 5.9 Device Onboarding / My Devices

- Backend ready: Yes. Provision, claim-code, claim, and `/iot/devices/me` are implemented.
- Frontend using it: Onboarding/claim uses backend; `/iot/devices/me` is visible in current UI paths according to frontend audit.
- Missing part: operator provisioning UX is absent; normal claim flow is ready.
- Classification: both ready for claim; operator provisioning frontend missing.

### 5.10 Device Management Local Screen

- Backend ready: Mostly. Farm plot/zone CRUD exists in farm-service and IoT device inventory exists in `/iot/devices/me`.
- Frontend using it: No. `/dashboard/devices` is still local/mock and conflicts with onboarding route.
- Missing part: frontend-only integration plus contract cleanup for authenticated farm ownership/pickers.
- Classification: mock frontend despite backend being mostly ready.

### 5.11 Community

- Backend ready: Yes for core feed/posts/comments/votes/shares.
- Frontend using it: No. Current frontend community remains local/mock.
- Missing part: frontend integration. Additional backend APIs may be needed only for advanced widgets such as trending/expert recommendations.
- Classification: mock frontend despite backend being ready for core flows.

### 5.12 Search / Monitor / Experts / Reports

- Search backend ready: Yes for post/profile search.
- Experts backend ready: Partly through profile search/filtering and RAG chat; no dedicated "experts page" aggregation was found.
- Monitor/reports backend ready: Partly through IoT dashboards, plant events/calendar, disease history, and treatment plans. No single report aggregation endpoint was found.
- Frontend using it: Routes are declared/missing pages per frontend audit.
- Missing part: frontend pages and product/API composition decisions. Reports may require backend aggregation depending on desired report semantics.
- Classification: route/product gap, not one simple missing endpoint.

### 5.13 Demo / Operator Tooling

- Backend ready: Yes service-local for non-prod bootstrap/history/simulation/scenarios.
- Frontend using it: No.
- Missing part: gateway route, admin/operator auth policy, and frontend page.
- Classification: backend ready but not exposed/integrated.

## 6. Highest-Priority Gaps

### A. Frontend-Only Gaps

| Rank | Gap | Impacted modules | Impacted frontend screens | Severity | Recommended next action |
|---:|---|---|---|---|---|
| 1 | Wire alert acknowledge/resolve UI | `iot-metrics-collector-service` | Alert Center | High | Add mutations for existing `/iot/alert-events/{id}/acknowledge` and `/resolve`, invalidate alert list/detail/dashboard queries. |
| 2 | Replace local `/dashboard/devices` | `farm-service`, `iot-metrics-collector-service` | Device management/inventory | High | Decide route ownership, use farm plots/zones and `/iot/devices/me`, remove or refactor `management-storage` local source of truth. |
| 3 | Wire community to backend | `community-feed-service`, `file-service` | Community | Medium-high | Start with feed/list/create/comment/vote; add media upload through file-service only if product requires it. |
| 4 | Add operator UI for demo data | `iot-test-data-service`, `api-gateway` | Demo/operator tooling | Medium | Create admin-only UI after gateway route/policy is added. |
| 5 | Implement missing search/experts page | `search-service`, `rag-service`, `profile-service` | Search/experts routes | Medium | Use `/api/search/posts/search` and `/api/search/profiles/search`; decide whether RAG chat belongs here. |

### B. Backend-Only Gaps

| Rank | Gap | Impacted modules | Impacted frontend screens | Severity | Recommended next action |
|---:|---|---|---|---|---|
| 1 | Push-token deactivate endpoint missing | `notification-service` | Push lifecycle/settings | High | Add deactivate/delete endpoint by token/device/user and align frontend wrapper. |
| 2 | Notification inbox/read APIs missing | `notification-service` | Notifications center/header | High | Expose paginated logs by current user plus mark-read/read-all if inbox UX is required. |
| 3 | IoT alert event not published to notification topic | `iot-metrics-collector-service`, `notification-service`, `common` | Alert push notifications | High | Add shared alert event contract and publish `iot.alert.triggered` when alert event is created; update `pushSent`. |
| 4 | Authenticated farm picker contract missing | `farm-service`, `profile-service` | Onboarding, alert rules, dashboard | Medium-high | Add `GET /farms/plots/me` and possibly combined plots+zones picker using `X-Profile-Id`. |
| 5 | Report aggregation endpoint absent | `iot-metrics-collector-service`, `plant-management-service`, `disease-detection-service` | Reports route | Medium | Define report requirements before adding aggregation API. |

### C. Contract / Integration Gaps

| Rank | Gap | Impacted modules | Impacted frontend screens | Severity | Recommended next action |
|---:|---|---|---|---|---|
| 1 | Identity headers should be gateway-owned | `api-gateway`, all services, frontend API client | All authenticated screens | High | Remove frontend dependence on manually computed `X-User-Id` where possible; require gateway for authenticated APIs. |
| 2 | Notification gateway route mismatch | `api-gateway`, `notification-service` | Push registration/settings | High | Add routes for `/api/push-tokens/**` and `/api/mailing/**`, or move controllers under `/notifications`. |
| 3 | Auth cookie/device route mismatch | `auth-service`, `api-gateway` | Auth/session settings | Medium-high | Align refresh cookie path and `/api/v1/devices` gateway route. |
| 4 | `iot-test-data-service` not routed | `api-gateway`, `iot-test-data-service` | Operator UI | Medium | Add non-prod/admin route only if operator UI is planned. |
| 5 | Farm/IoT zone ownership not centrally validated | `farm-service`, `iot-metrics-collector-service` | Onboarding, alert rules | Medium | Either validate cross-service references or provide picker-only scoped IDs and enforce ownership on IoT side. |

## 7. End-to-End Readiness Assessment

| User journey | Readiness | Why |
|---|---|---|
| User login/register | Mostly ready | Core auth and profile creation are implemented and frontend uses them. Verify cookie path/session edge cases. |
| User enters dashboard and sees live monitoring | Mostly ready | Frontend uses backend dashboard; live values depend on telemetry ingestion/MQTT and seeded/real devices. |
| User opens zone metrics | Mostly ready | Backend and frontend are wired for zone overview/charts. Navigation/picker data can be improved. |
| User inspects alert center | Partially ready | Read path is wired; ack/resolve lifecycle backend exists but UI is missing. |
| User opens device detail and pushes config | Mostly ready | Detail/config/push/ack are implemented. UI should clearly show SENT/ACKED/FAILED and poll/refetch. |
| User manages alert rules | Mostly ready | CRUD/enable/delete is wired. Picker and cross-service validation need hardening. |
| User onboards and claims a device | Mostly ready | Claim flow is implemented and frontend uses it. Provisioning/operator side is not a normal-user UI. |
| Operator seeds demo data and runs simulation | Partially ready | Backend tooling is strong, but no gateway route/operator UI exists. |
| User uses community | Partially ready | Backend core community is ready; frontend remains local/mock. |
| User uses search/experts/reports | Not ready as product journeys | Backends exist in pieces, but frontend pages are missing and report semantics are undefined. |

## 8. Recommended Next Roadmap

### Immediate Next Steps

1. Wire alert acknowledge/resolve in the frontend using existing IoT endpoints.
2. Fix notification contract: add push-token deactivate, decide inbox/read API, and align gateway routes with `/push-tokens` and `/mailing`.
3. Resolve `/dashboard/devices`: either replace it with backend farm/IoT inventory or remove it from navigation until integrated.
4. Stop relying on frontend-managed identity headers for authenticated backend calls; make gateway-injected headers the only trusted path.
5. Verify auth refresh cookie path and auth-device route mapping against gateway.

### Short-Term Next Steps

1. Add authenticated farm picker APIs (`my plots`, `zones by my plot`, or combined picker) and use them in onboarding, dashboard, and alert rules.
2. Connect community frontend to `community-feed-service` for feed/create/comment/vote before adding advanced widgets.
3. Add IoT alert-to-notification publishing so mobile/web push can be end-to-end.
4. Add admin/non-prod gateway route and frontend operator page for `iot-test-data-service`.
5. Wire file-service for avatar upload, then update profile picture/avatar fields through profile-service.

### Later Improvements

1. Build search and experts pages from `search-service` and optionally RAG chat.
2. Define reports as a product contract, then either compose IoT/plant/disease data in frontend or add a backend report aggregation API.
3. Add disease detection UI and diagnose history around `disease-detection-service`.
4. Decide source-of-truth boundaries between RAG-generated treatment plans and `plant-management-service` treatment plans.
5. Harden cross-service ownership validation for farm plot/zone references used by IoT devices and alert rules.

## Source Notes And Outdated Prior Assumptions

- Older frontend notes that dashboard, zone metrics, alerts, device detail/config, alert rules, and onboarding were mock are outdated for current source. The current frontend audit and backend code show these are backend-driven now.
- Older notes that alert lifecycle actions were not available are outdated at backend level: `AlertController` and `AlertLifecycleServiceImpl` implement acknowledge/resolve. They remain missing in frontend UI.
- Earlier expectations around `/iot/devices/me` are now satisfied by backend and visible frontend onboarding usage, but the old local `/dashboard/devices` flow still exists separately.
- Push-token deactivate remains a real mismatch: backend source only exposes register.
- Demo/operator UI is still frontend-missing, but backend service-local tooling is implemented.

