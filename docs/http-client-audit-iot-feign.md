# HTTP Client Audit - Backend & IoT

## 1. Executive Summary

- Backend Leafy_BE currently uses multiple HTTP client styles: OpenFeign for most synchronous internal service-to-service calls, RestTemplate in `profile-service` and `iot-metrics-collector-service`, RestClient in `plant-management-service` and `iot-test-data-service`, and WebClient in `api-gateway`.
- No production usage of Java `java.net.http.HttpClient`, Apache HttpClient, OkHttp, or custom raw Feign clients was found by the audit search.
- `iot-metrics-collector-service` already uses OpenFeign for internal `file-service` APIs and for the disease prediction endpoint. Its remaining production `RestTemplate` usage is in `DiseaseDetectionClient` for downloading image bytes from a URL that may be an external presigned S3 URL.
- Recommendation: do not convert dynamic external/presigned URL downloads to OpenFeign. Keep a dedicated download client there, but harden timeout, max-size, and content-type validation. Keep/standardize Feign for stable internal service contracts.

## 2. Backend-wide HTTP Client Usage

| Module | HTTP Client đang dùng | File | Gọi service nào | URL/config | Có Feign chưa? | Nhận xét |
| ------ | --------------------- | ---- | --------------- | ---------- | -------------- | -------- |
| `auth-service` | OpenFeign | `src/main/java/com/leafy/authservice/client/ProfileServiceClient.java`, `ProfileClient.java`, `NotificationClient.java` | `profile-service`, `notification-service` | Service name via Feign/Eureka; `ProfileClient` path `/internal`, `NotificationClient` path `/internal` | Yes: `@EnableFeignClients`, `spring-cloud-starter-openfeign` | Has service config in `config-server/auth-service.yaml` with loggerLevel FULL, connectTimeout 5000, readTimeout 10000. `FeignConfig` sets `Logger.Level.FULL`. |
| `community-feed-service` | OpenFeign | `client/SearchServiceClient.java`, `ProfileServiceClient.java`, `PlantManagementServiceClient.java` | `search-service`, `profile-service`, `plant-management-service` | Service name via Feign/Eureka; endpoints include `/sync/posts/reset`, `/profiles/active`, `/plans` | Yes: `@EnableFeignClients(basePackages = "com.leafy.communityfeedservice.client")`, OpenFeign dependency | Internal calls use Feign; no RestTemplate/WebClient found. |
| `file-service` | OpenFeign dependency enabled; no outgoing Feign client found in source search | `FileServiceApplication.java` | None found | N/A | Yes: `@EnableFeignClients`, OpenFeign dependency | Uses `spring-boot-starter-webflux` dependency but no direct WebClient usage found. Provides internal file endpoints consumed by IoT. |
| `iot-metrics-collector-service` | OpenFeign + RestTemplate | `integration/file/FileServiceFeignClient.java`, `integration/disease/DiseaseDetectionFeignClient.java`, `integration/disease/DiseaseDetectionClient.java`, `config/HttpClientConfig.java` | `file-service`, disease detection through gateway/base URL, external/presigned file URL download | Feign service name `file-service`; disease Feign URL `${app.disease-detection.gateway-url}`; RestTemplate dynamic full URL | Yes: `@EnableFeignClients`, OpenFeign dependency | Feign is already used for internal file-service and disease predict. RestTemplate only production use is image download. No RestTemplate timeout configured. |
| `iot-test-data-service` | RestClient | `config/SeedConfig.java`, `client/RestIotCollectorClient.java`, `RestFarmSeedClient.java`, `RestProfileSeedClient.java` | IoT collector, farm API, profile API | Base URLs from `SeedProperties` | No OpenFeign dependency | This is a test/seed service. Uses explicit auth headers in RestClient calls. Not a priority for IoT collector refactor. |
| `message-service` | OpenFeign | `client/ProfileServiceClient.java` | `profile-service` | `@FeignClient(name = "profile-service", path = "/internal/profiles")` | Yes: `@EnableFeignClients(...)`, OpenFeign dependency | Internal profile batch lookup through Feign. |
| `notification-service` | OpenFeign + external SDK client | `client/ProfileServiceClient.java`, `config/BrevoConfig.java` | `profile-service`, Brevo mail API SDK | Feign service name; Brevo SDK has connect/read timeout 60000 | Yes: `@EnableFeignClients`, OpenFeign dependency | Feign for internal profile API; Brevo is not a Spring HTTP client but has explicit timeout config. |
| `plant-management-service` | OpenFeign + RestClient | `client/ProfileServiceClient.java`, `service/species/SpeciesServiceImpl.java` | `profile-service`, external Perenual API | Feign service name; RestClient base URL `${perenual.api.base-url}` | Yes: `@EnableFeignClients`, OpenFeign dependency | RestClient targets external Perenual API and is reasonable to keep outside Feign. Retry is page-level skip/log, no explicit timeout found. |
| `profile-service` | OpenFeign + RestTemplate | `client/AuthClient.java`, `SearchClient.java`, `SearchSyncClient.java`, `client/FileServiceClient.java`, `config/AsyncConfig.java` | `auth-service`, `search-service`, `file-service` | Feign service names; RestTemplate `http://file-service/internal/files/upload` with `@LoadBalanced` bean | Yes: `@EnableFeignClients`, OpenFeign dependency | Internal file upload still uses RestTemplate. Since this is a stable internal contract, it is a Feign conversion candidate outside IoT. No RestTemplate timeout found. |
| `search-service` | OpenFeign interfaces | `client/ProfileClient.java`, `PlantManagementClient.java`, `CommunityPostClient.java`, `AuthUserClient.java` | `profile-service`, `plant-management-service`, `community-feed-service`, `auth-service` | Service names via Feign/Eureka; internal paths `/internal/...` | `@EnableFeignClients` present; direct `spring-cloud-starter-openfeign` dependency not found in `search-service/pom.xml` but `common` dependency has it | Needs verification: Feign may arrive transitively through `common`, but explicit OpenFeign dependency is inconsistent with other modules. |
| `socket-service` | No outgoing HTTP client found | `SocketServiceApplication.java` | None found | N/A | `@EnableFeignClients` not present; direct OpenFeign dependency not found | `config-server/socket-service.yaml` contains `feign` timeout/circuitbreaker config despite no Feign usage found. Likely stale or future config. |
| `api-gateway` | WebClient | `service/SystemHealthService.java` | Actuator health endpoints of registered services | Uses `ReactiveDiscoveryClient` instance URI + `/actuator/health` | No OpenFeign dependency | WebClient is appropriate for reactive health probing. Has per-call `.timeout(Duration.ofSeconds(3))`. |
| `config-server` | None found | N/A | N/A | Provides config YAML | No | Contains URL and Feign config for services. |
| `discovery-server` | None found | N/A | N/A | N/A | No | No outgoing HTTP client found. |
| `common` | Feign config/support | `config/FeignCommonConfig.java`, `FeignSecurityInterceptor.java`, `FeignErrorDecoder.java` | Applies to Feign clients when scanned/imported | Header propagation and central error decoder | OpenFeign dependency | Defines common Feign pattern: propagate user headers and decode `ApiResponse` errors. |

## 3. OpenFeign Usage Patterns

| Feign Client | Module | Service gọi tới | Config | Header propagation | Error handling |
| ------------ | ------ | --------------- | ------ | ------------------ | -------------- |
| `ProfileServiceClient`, `ProfileClient` | `auth-service` | `profile-service` | Service name, paths include `/internal` and direct profile endpoints | Common interceptor if `com.leafy.common` is scanned; auth service also has Feign logging config | `common/FeignErrorDecoder.java` available; expected common pattern. |
| `NotificationClient` | `auth-service` | `notification-service` | `@FeignClient(name = "notification-service", path = "/internal")` | Common interceptor | Common Feign error decoder. |
| `SearchServiceClient` | `community-feed-service` | `search-service` | `@FeignClient(name = "search-service")` | Common interceptor only if common config scanned | Common Feign error decoder if active. |
| `ProfileServiceClient` | `community-feed-service` | `profile-service` | `@FeignClient(name = "profile-service")` | Common interceptor if active | Common Feign error decoder if active. |
| `PlantManagementServiceClient` | `community-feed-service` | `plant-management-service` | `@FeignClient(name = "plant-management-service")` | Common interceptor if active | Common Feign error decoder if active. |
| `FileServiceFeignClient` | `iot-metrics-collector-service` | `file-service` | `@FeignClient(name = "file-service", path = "/internal/files")` | Common interceptor if active; no IoT-specific header config on this client | Common Feign error decoder if active. |
| `DiseaseDetectionFeignClient` | `iot-metrics-collector-service` | Disease detection through gateway/base URL | `name = "disease-detection-gateway-client"`, `url = "${app.disease-detection.gateway-url}"`, path `/internal/diseases`, multipart `/predict` | `DiseaseDetectionFeignConfig` sets `X-User-Id`, `X-User-Email`, `X-User-Roles` as SYSTEM | Common decoder may apply; client-specific interceptor applies system headers. |
| `ProfileServiceClient` | `message-service` | `profile-service` | `@FeignClient(name = "profile-service", path = "/internal/profiles")` | Common interceptor likely active via explicit scan package | Common Feign error decoder. |
| `ProfileServiceClient` | `notification-service` | `profile-service` | `@FeignClient(name = "profile-service", path = "/internal/profiles")` | Common interceptor if active | Common Feign error decoder. |
| `ProfileServiceClient` | `plant-management-service` | `profile-service` | `@FeignClient(name = "profile-service")` | Common interceptor if active | Common Feign error decoder. |
| `AuthClient`, `SearchClient`, `SearchSyncClient` | `profile-service` | `auth-service`, `search-service` | Service names; `AuthClient` path `/internal/users` | Common interceptor if active | Common Feign error decoder. |
| `ProfileClient`, `PlantManagementClient`, `CommunityPostClient`, `AuthUserClient` | `search-service` | `profile-service`, `plant-management-service`, `community-feed-service`, `auth-service` | Internal paths such as `/internal/profiles`, `/internal/plans`, `/internal/posts`, `/internal/accounts` | Common interceptor if active | Needs verification because direct OpenFeign dependency is absent in `search-service/pom.xml`. |

Reusable pattern for IoT: `FileServiceFeignClient` already follows the preferred service discovery style. `DiseaseDetectionFeignClient` uses a fixed URL property because it goes through the API gateway by default; consider service discovery if direct service-to-service is preferred.

## 4. IoT Metrics Collector Deep Dive

| Class/File | HTTP client | Purpose | Called service | Endpoint | Config property | Timeout/retry | Error handling | Có nên đổi sang Feign? |
| ---------- | ----------- | ------- | -------------- | -------- | --------------- | ------------- | -------------- | ---------------------- |
| `IotMetricsCollectorServiceApplication.java` | OpenFeign enablement | Enables Feign scanning | N/A | N/A | N/A | N/A | N/A | Already enabled. |
| `config/HttpClientConfig.java` | RestTemplate bean | Provides generic RestTemplate | Dynamic image download in `DiseaseDetectionClient` | N/A | N/A | No explicit connect/read timeout | N/A | Keep only if hardened; do not use for internal service contracts. |
| `integration/file/FileServiceFeignClient.java` | OpenFeign | Internal file-service upload, metadata, presigned URL, binary download by S3 key | `file-service` | `/internal/files/upload`, `/internal/files/{fileId}`, `/internal/files/presigned-url/{fileId}`, `/internal/files/download/s3-key` | Service discovery name `file-service` | Feign timeout config not present in IoT YAML | Common Feign decoder if active; wrapper validates null/missing data | Already Feign. Keep and standardize config. |
| `integration/file/FileServiceClient.java` | Wrapper around Feign | Provides typed file operations and builds internal download URL | `file-service` through Feign | Same as above; also builds `http://file-service/internal/files/download/s3-key?s3Key=...` sentinel URL | Hardcoded `http://file-service/internal/files/download/s3-key` | Feign default; no explicit timeout in IoT config | Throws `IllegalStateException` for missing file data; extracts `s3Key` for Feign download | Keep Feign. Consider replacing hardcoded sentinel URL with structured value or direct method call to avoid pretending it is an external URL. |
| `integration/disease/DiseaseDetectionFeignClient.java` | OpenFeign | Sends downloaded image as multipart to disease detection | Gateway/disease detection | POST `${app.disease-detection.gateway-url}/internal/diseases/predict` | `app.disease-detection.gateway-url` | No IoT Feign timeout config found | Feign exceptions; response parsed in `DiseaseDetectionClient` | Already Feign. Could switch to `@FeignClient(name = "disease-detection-service", path = "/internal/diseases")` if direct Eureka routing is desired. |
| `integration/disease/DiseaseDetectionFeignConfig.java` | Feign `RequestInterceptor` | Adds internal SYSTEM headers | Disease detection endpoint | N/A | N/A | N/A | N/A | Keep. This is the right pattern for background/system calls. |
| `integration/disease/DiseaseDetectionClient.java` | RestTemplate + OpenFeign | Downloads image bytes, builds in-memory multipart, calls predict Feign client | External presigned URL or `file-service` internal download; disease detection | `GET <dynamic fileUrl>` or Feign `downloadByS3Key`; then POST `/internal/diseases/predict` | `app.disease-detection.confidence-threshold` | Download retry only for `ResourceAccessException`, 2 attempts. RestTemplate has no configured timeout. | Rejects localhost/127/0.0.0.0 URLs; sanitizes log URL; classifies AWS `AuthorizationQueryParametersError`; throws `IllegalStateException` | Do not convert dynamic URL download to Feign. Harden RestTemplate or use WebClient for streaming/size limits. |
| `service/impl/DeviceMediaAnalysisServiceImpl.java` | Uses clients, no direct HTTP | Resolves internal file download URL and runs disease analysis job | File-service and disease detection indirectly | Calls `fileServiceClient.getInternalDownloadUrl`, `diseaseDetectionClient.detect` | `app.image-analysis.max-attempts` | Job-level retry `maxAttempts`, default 2 | Marks analysis failed, skips invalid presigned URL, sanitizes logs | No direct client conversion needed. |
| `integration/mqtt/CameraCaptureMqttPublisherImpl.java` | MQTT, not HTTP client | Publishes capture command containing upload endpoint for devices | Device uploads to file-service endpoint later | Payload field defaults to `app.file-service.upload-url` | `app.file-service.upload-url` | N/A | Throws on MQTT send/serialization failure | Not an HTTP client. Config cleanup needed because default/config include hardcoded URL variants. |

## 5. RestTemplate Usage Analysis in IoT

### `DiseaseDetectionClient`

Purpose:
- Downloads image bytes before disease detection.
- If URL host is `file-service` and path is `/internal/files/download/s3-key`, the client delegates to `FileServiceClient.downloadInternalImage`, which uses Feign.
- Otherwise it calls `restTemplate.getForEntity(fileUrl, byte[].class)` against a full dynamic URL.

Endpoint nature:
- `http://file-service/internal/files/download/s3-key?s3Key=...` is an internal microservice sentinel URL, but actual HTTP call is converted back to Feign by extracting `s3Key`.
- Presigned S3 URLs are dynamic full HTTPS URLs with query signatures and should not be modeled as Feign clients.

Retry/error handling:
- Two attempts for `ResourceAccessException` only.
- Non-network `RestClientException` is not retried.
- AWS malformed credential errors are classified as `INVALID_PRESIGNED_URL`.
- URL validation rejects blank, non-HTTP(S), localhost, 127.*, 0.0.0.0, and IPv6 loopback hosts.

Headers:
- No internal headers are sent for dynamic URL downloads. This is correct for S3 presigned URLs.
- Internal `file-service` binary download goes through `FileServiceFeignClient`; common Feign headers may apply if common config is active.

Recommendation:
- Keep RestTemplate or replace with WebClient only for the dynamic external download path.
- Do not convert dynamic presigned S3 URL download to Feign.
- Add explicit connect/read timeout, max image size guard, content-type allowlist, and sanitized logging around the download client.
- Consider avoiding the hardcoded `http://file-service/...` sentinel URL and passing a structured `s3Key` or using `fileServiceClient.downloadByS3Key` directly from the analysis flow.

## 6. Configuration Review

| Property | Current value/default | Used by | Risk | Suggested change |
| -------- | --------------------- | ------- | ---- | ---------------- |
| `spring.application.name` | `iot-metrics-collector-service` in config server and bootstrap/application file | Service registration/config | Low | Keep. |
| `eureka.client.service-url.defaultZone` | `${EUREKA_CLIENT_SERVICEURL_DEFAULTZONE:http://localhost:8761/eureka/}` | Eureka discovery used by Feign service name `file-service` | Localhost default is OK for local dev, risky if accidentally used in Docker/prod | Use env-specific override; document required value for Docker/prod. |
| `app.file-service.upload-url` | Config server default `${FILE_SERVICE_UPLOAD_URL:http://localhost:8084/internal/files/upload}`; code default `http://file-service:8084/internal/files/upload` in `CameraCaptureMqttPublisherImpl` | MQTT payload for device camera upload endpoint | Inconsistent defaults; config server `localhost` is invalid for remote IoT devices and Docker containers. Also this is not used by Feign upload. | Split device-facing upload URL from internal file-service Feign config. Use a reachable gateway/public URL for device payloads, not internal service name unless devices run in same network. |
| `app.disease-detection.gateway-url` | `${DISEASE_DETECTION_GATEWAY_URL:http://localhost:${SERVER_PORT_GATEWAY:8080}}` | `DiseaseDetectionFeignClient` base URL | Localhost default makes IoT call gateway on same container/host. In Docker this may fail unless overridden. Service-to-service through gateway is also a deliberate architecture decision that should be documented. | Prefer `@FeignClient(name = "disease-detection-service", path = "/internal/diseases")` for direct Eureka internal call, or set gateway URL per environment. |
| `app.disease-detection.predict-url` | `${DISEASE_DETECTION_PREDICT_URL:http://localhost:${SERVER_PORT_GATEWAY:8080}/internal/diseases/predict}` | No production usage found in IoT source | Stale/duplicated config can mislead maintainers | Remove in a later cleanup if verified unused, or align code to one property. |
| `app.disease-detection.confidence-threshold` | Default `0.70` in `DiseaseDetectionClient` | Disease result classification | Low | Move to config server if operations need to tune it. |
| `app.image-analysis.max-attempts` | Default `2` in `DeviceMediaAnalysisServiceImpl` | Job-level disease detection retry | Medium: separate from download retry; no config server value found | Add explicit config server value and document relationship with download retry. |
| `spring.cloud.openfeign.*` / `feign.client.config.*` | No IoT-specific timeout config found | IoT Feign clients | Medium: default Feign timeouts may be unsuitable for multipart image disease prediction | Add IoT Feign default or per-client connect/read timeouts. |
| RestTemplate timeout config | `HttpClientConfig` returns `builder.build()` with no timeout | Dynamic image download | High for stuck/slow downloads | Add connect/read timeouts and possibly buffer/size limits in Phase 1/3. |
| `http://file-service/internal/files/download/s3-key` | Hardcoded constant in `FileServiceClient` | Internal download sentinel URL | Medium: URL string is only used for later detection/extraction, not direct HTTP. This is surprising and can be confused with real RestTemplate call. | Replace with structured internal download reference or call Feign directly in later cleanup. |
| MQTT broker URL | `${MQTT_BROKER_URL:tcp://137.66.4.201:1883}` | MQTT integration | Not HTTP; outside this audit | Do not change in HTTP-client refactor. |

## 7. Recommendation

### Convert to Feign

- No immediate IoT RestTemplate internal microservice call remains that must be converted. The important internal file-service calls are already behind `FileServiceFeignClient`.
- Consider converting `DiseaseDetectionFeignClient` from gateway URL mode to service discovery mode if the architectural convention is direct service-to-service:

```java
@FeignClient(name = "disease-detection-service", path = "/internal/diseases")
public interface DiseaseDetectionFeignClient {
    @PostMapping(value = "/predict", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    JsonNode predict(@RequestPart("file") MultipartFile file);
}
```

- Outside IoT, `profile-service/client/FileServiceClient.java` is a strong Feign conversion candidate because it calls a stable internal `file-service` endpoint using load-balanced RestTemplate.

### Keep RestTemplate/WebClient

- Keep a non-Feign client for dynamic full URL image download in `DiseaseDetectionClient`.
- If images can be large or if streaming/backpressure matters, prefer WebClient or a streaming RestTemplate request callback rather than `byte[]` buffering.
- Keep WebClient in `api-gateway/SystemHealthService.java`; the use is reactive, discovery-based health probing with timeout.
- Keep RestClient in `plant-management-service/SpeciesServiceImpl.java` for the external Perenual API.

### Needs Config Cleanup

- Remove or mark unused `app.disease-detection.predict-url` after verification.
- Align `app.file-service.upload-url` defaults. Decide whether this property is device-facing public/gateway URL or internal service URL.
- Add IoT-specific Feign timeout config for file-service and disease prediction.
- Add explicit RestTemplate download timeout config.

### Needs Tests

- Feign contract tests for `FileServiceFeignClient` endpoints against `file-service` internal controller.
- `DiseaseDetectionClient` tests for timeout/error classification, content-type validation, max file size, and S3 URL sanitization.
- Config property tests or startup validation for required production URLs.

## 8. Proposed Implementation Phases

### Phase 1 - Audit-safe config cleanup

- Add explicit IoT Feign timeout config.
- Add explicit RestTemplate connect/read timeout config.
- Document `app.file-service.upload-url` as device-facing or internal.
- Remove or deprecate unused `app.disease-detection.predict-url` after verification.

### Phase 2 - Convert internal service calls to OpenFeign

- IoT file-service calls are already Feign; no mandatory conversion.
- If desired, switch disease detection from gateway URL Feign to direct `disease-detection-service` service discovery Feign.
- Outside IoT, convert `profile-service` internal file upload from load-balanced RestTemplate to Feign.

### Phase 3 - Harden dynamic URL download client

- Keep presigned S3 full URL download out of Feign.
- Add connect/read timeout, maximum file size, content-type validation, and sanitized logging.
- Consider WebClient or streaming download if files can be large.

### Phase 4 - Tests

- Add Feign client contract tests for file-service internal APIs.
- Add disease image download tests for retry, invalid URL, S3 signature error, oversized body, and non-image content type.
- Add config/property tests for required URL values in non-local profiles.

## 9. Do Not Change

- Do not use Feign for full dynamic presigned S3 URLs.
- Do not convert MQTT capture command publishing as part of HTTP-client cleanup.
- Do not change file upload/device capture workflow without first deciding whether upload endpoint is public/gateway-facing or internal network-facing.
- Do not replace `api-gateway` WebClient health checks with Feign.
- Do not replace external Perenual API RestClient with Feign unless a typed external API client contract is deliberately introduced.

## 10. Risk/Benefit Candidates

| Candidate | Current client | Change to Feign? | Benefit | Risk | Priority |
| --------- | -------------- | ---------------: | ------- | ---- | -------- |
| IoT file-service upload/metadata/presigned/internal download | OpenFeign already | No | Already gets service discovery and typed interface | Needs timeout/config consistency | P1 |
| IoT disease prediction endpoint | OpenFeign with fixed gateway URL | Maybe, only to service-discovery Feign | Avoid localhost/gateway URL config drift; clearer service-to-service contract | Gateway may be intentional for auth/routing; multipart timeout needs tuning | P1 |
| IoT dynamic image download from S3/presigned URL | RestTemplate | No | Feign would not fit dynamic full signed URLs | Current RestTemplate lacks timeout/size/content-type guard | P2 keep client, P1 harden |
| IoT `FileServiceClient` hardcoded internal download sentinel URL | String wrapper + Feign extraction | Not a Feign conversion; clean abstraction | Less surprising flow, fewer fake URLs | Touches existing tested workflow | P1 |
| `profile-service` internal file upload | Load-balanced RestTemplate | Yes | Aligns with project rule: internal REST via Feign; better contract/error handling | Multipart Feign setup/testing needed | P1 |
| `api-gateway` health probes | WebClient | No | Reactive timeout-based probing is appropriate | None | P3 |
| `plant-management-service` Perenual API | RestClient | No | External API, isolated seed flow | No explicit timeout/retry policy | P2 document/harden |
| `iot-test-data-service` seed calls | RestClient | Usually no | Seed/test utility, dynamic base URLs and scripted headers | Not production service-to-service standard | P2 |

## 11. Recommended Next Codex Prompt

```text
Trong Leafy_BE, thực hiện Phase 1 của docs/http-client-audit-iot-feign.md: chỉ cleanup config an toàn cho iot-metrics-collector-service. Thêm timeout rõ ràng cho RestTemplate download client và OpenFeign clients, chuẩn hóa/ghi chú property app.file-service.upload-url và app.disease-detection.gateway-url, bỏ hoặc deprecate app.disease-detection.predict-url nếu xác minh không dùng. Không đổi logic client, không chuyển RestTemplate sang Feign trong phase này. Chạy test liên quan IoT sau khi sửa.
```
