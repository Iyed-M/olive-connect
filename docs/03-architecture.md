# 03 — Architecture

## 1. System architecture (C4 level 1–2)

```mermaid
flowchart TB
    subgraph Users
        V[Visitor<br/>browser]
        U[Authenticated users<br/>phone / desktop browser]
    end

    subgraph VPS["Linux VPS (Docker Compose)"]
        NG["Nginx<br/>TLS, static SPA, reverse proxy,<br/>gzip, cache headers, rate limit"]
        subgraph App
            API["Spring Boot API<br/>(REST + SSE)"]
            SCHED["Schedulers<br/>imports, KPI warm-up, reminders"]
        end
        MDB[("MongoDB<br/>replica set rs0 (1 node)")]
        RDS[("Redis<br/>cache, tokens, pub/sub")]
        INBOX[/"Import inbox volume<br/>/data/inbox (CSV/JSON)"/]
        subgraph Monitoring
            PROM[Prometheus]
            GRAF[Grafana]
            LOKI[Loki + Promtail]
            KUMA[Uptime Kuma]
        end
    end

    SIM["Data simulator<br/>(weather, maturity, mill capacity,<br/>reference data)"]

    V -- HTTPS --> NG
    U -- HTTPS --> NG
    NG -- "/api/**, /api/events (SSE)" --> API
    API --> MDB
    API --> RDS
    SCHED --> INBOX
    SCHED --> MDB
    SIM -- writes files / HTTP --> INBOX
    PROM -- scrape /actuator/prometheus --> API
    GRAF --> PROM
    GRAF --> LOKI
    KUMA -- probe --> NG
```

**Main choices**

- **Single deployable backend** (a modular monolith). With 3 students and a 30 h budget per module, microservices would add work without any benefit. The packages are still split by feature, so the code stays modular.
- **SPA + REST API**: Nginx serves the React build as static files and proxies `/api`. The Actuator runs on the same port 8080 as the API and is not proxied. The browser only ever talks to Nginx, so there is no CORS to configure in production.
- **Real-time** uses **Server-Sent Events** (`/api/events`), which is simpler than WebSocket and enough for one-way server→client notifications. Redis pub/sub broadcasts each event to every API instance, so the backend can scale horizontally.
- MongoDB runs as a **single-node replica set**. That is required for multi-document transactions (`@Transactional` on round creation plus bookings) and for change streams.

## 2. Layered architecture (subject §IV.1)

For each use case, the API is split into **Controller → Service → DAO (Spring Data Repository) → Entity**, with **constructor-based dependency injection** between the layers.

```mermaid
flowchart TB
    subgraph Presentation["Controller layer (@RestController)"]
        C1[HarvestRequestController]
        C2[CollectionRoundController]
        C3[FieldAlertController]
        C4[...]
    end
    subgraph DTO["DTO (record + static from())"]
        D1[HarvestRequestCreateDto / HarvestRequestDto]
    end
    subgraph Business["Service layer (@Service, @Transactional)"]
        S1[HarvestRequestService]
        S2[CollectionRoundService]
        S3[ResourceBookingService]
        S4[NotificationService]
        S5[KpiService]
    end
    subgraph DAO["DAO layer (Spring Data)"]
        R1[HarvestRequestRepository<br/>extends MongoRepository]
        R2[CollectionRoundRepository]
        R3[ResourceBookingRepository]
        R4["KpiRepositoryCustom (MongoTemplate,<br/>aggregation)"]
        RC[(Spring Cache → Redis)]
    end
    subgraph Model["Entity layer (@Document)"]
        E1[HarvestRequest]
        E2[CollectionRound]
        E3[ResourceBooking]
    end
    C1 --> D1
    C1 --> S1
    C2 --> S2
    S1 --> R1
    S2 --> R2
    S2 --> S3
    S3 --> R3
    S1 --> S4
    S2 --> S4
    S5 --> R4
    S5 -.@Cacheable.-> RC
    R1 --> E1
    R2 --> E2
    R3 --> E3
```

**Rules for each layer** (checked in code review):

| Layer | Responsibilities | Must not |
|---|---|---|
| Controller | HTTP mapping, `@Valid` input validation, map DTO ⇄ domain, HTTP status codes | contain business rules, call repositories |
| Service | Business rules, ownership checks, transactions, orchestration, events, logging | return entities to controllers (it returns DTOs or domain objects mapped by the controller) |
| Repository (DAO) | Persistence via Spring Data (derived queries, `@Query`, custom `MongoTemplate` implementations for geo/aggregation) | contain business logic |
| Entity | `@Document` classes, indexes (`@Indexed`, `@CompoundIndex`, `@GeoSpatialIndexed`), auditing fields, `@Version` | depend on upper layers |

Cross-cutting: `GlobalExceptionHandler` (`@RestControllerAdvice`) → RFC 7807 `ProblemDetail`; `SecurityConfig`; `AuditorAware`; Log4j2; Actuator.

## 3. Backend package structure (package-by-feature)

```text
tn.oliveconnect
├── OliveConnectApplication.java
├── config/            (M1a) SecurityConfig, ProblemSecurityHandler, AppProperties, AuditingConfig.
│                      Planned: RedisConfig, CacheConfig, OpenApiConfig, AsyncConfig, SchedulingConfig
├── auth/              (M1a) AuthController, MeController, AuthService, JwtService, RefreshTokenStore,
│                      LoginRateLimiter, LoginRequest, LoginResponse, AccessToken, Session,
│                      InvalidCredentialsException, UnauthenticatedException, TooManyAttemptsException
├── common/            (M1a) GlobalExceptionHandler, NotFoundException.
│                      Planned: Conflict, Forbidden, BusinessRule exceptions, PageResponse
├── user/              (M1a) User, FarmerProfile, Role, Delegation, MeDto, UserRepository, AdminSeeder.
│                      RegistrationRequest, RegistrationStatus, RegistrationRequestRepository, RegistrationService,
│                      RegistrationController and their DTOs (M9).
│                      Planned: UserService, UserController (admin)
├── orchard/           Orchard, Plot, GeoPoint, OliveVariety, HarvestStatus, DTOs, repositories,
│                      OrchardService, OrchardController, OliveVarietyController, OliveVarietySeeder,
│                      MapController (public + private layers, future)
├── harvest/           HarvestRequest, HarvestRequestStatus, StatusChange, HarvestRequestRepository,
│                      HarvestRequestService, HarvestRequestController
├── round/             CollectionRound, RoundStop, RoundStatus, CollectionRoundRepository,
│                      CollectionRoundService, PlanningService, CollectionRoundController
├── resource/          Worker, Equipment, EquipmentType, ResourceBooking, SupplyOrder,
│                      repositories, ResourceService, BookingService, SupplyService, controllers
├── alert/             FieldAlert, AlertType, AlertStatus, FieldAlertRepository, FieldAlertService,
│                      AutoAlertRule, FieldAlertController
├── mill/              Mill, MillReception, QualityGrade, repositories, MillService,
│                      MillReceptionService, MillController
├── telemetry/         WeatherReading, MaturityReading (time-series), repositories, TelemetryService
├── notification/      Notification, NotificationRepository, NotificationService,
│                      SseEmitterRegistry, RedisNotificationPublisher/Subscriber, NotificationController
├── dashboard/         KpiService, KpiRepositoryCustom(+Impl with Aggregation), DashboardController, dto/
├── dataimport/        ImportJob, ImportJobRepository, ImportScheduler, ImportService,
│                      parsers/ (CsvParser, JsonParser), validators/, ImportController (admin)
├── settings/          SystemSettings, SettingsService, SettingsController
└── system/            SystemStatusController, SystemStatusService, SystemStatusDto  (M0: GET /api/system/status)
```

## 4. Technology stack

| Concern | Choice | Version (target) |
|---|---|---|
| Language / runtime | Java | 21 LTS |
| Framework | Spring Boot (Web, Validation, Security, Data MongoDB, Data Redis, Cache, Actuator) | 3.4+ |
| Build | Maven via `mvnw` | 3.9 |
| Mapping | Hand-written static `from(entity)` methods on DTO records | — |
| API doc | springdoc-openapi (Swagger UI) | 2.x |
| Auth | JWT HS256 signed and checked by the Spring Security OAuth2 Resource Server (Nimbus, HMAC key from `JWT_SECRET`), BCrypt passwords | — |
| Logging | Log4j2 (`spring-boot-starter-log4j2`, JSON layout for Loki) — the subject cites Log4j | 2.x |
| CSV | OpenCSV or Jackson CSV | — |
| Metrics | Micrometer + Prometheus registry | — |
| Tests | JUnit 5, Mockito, AssertJ, Spring Boot Test, MockMvc, Testcontainers (MongoDB, Redis) | — |
| DB | MongoDB | 7.0 / 8.0 |
| Cache | Redis | 7.x |
| Frontend | React 19, Vite, TypeScript, pnpm, Tailwind CSS v4, shadcn/ui (custom olive theme), TanStack Router (file-based), TanStack Query, react-leaflet + Leaflet.markercluster, Recharts (shadcn chart), React Hook Form, React Joyride, Vitest + Testing Library, Playwright | — |
| Server | Nginx | 1.27 |
| Monitoring | Prometheus, Grafana, Loki/Promtail, node-exporter, cAdvisor, Uptime Kuma | — |
| Load test | k6 (or JMeter) | — |

## 5. Main cross-cutting flows

### Request flow through the layers

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant N as Nginx
    participant F as BearerTokenAuthenticationFilter
    participant C as Controller
    participant S as Service
    participant R as Repository
    participant M as MongoDB
    B->>N: HTTPS /api/... + Bearer JWT
    N->>F: proxy_pass http://api:8080
    F->>F: validate signature, expiry, load roles
    F->>C: SecurityContext populated
    C->>C: @Valid DTO, @PreAuthorize(role)
    C->>S: call with command / ids
    S->>S: ownership + business rules
    S->>R: Spring Data call
    R->>M: query (uses index)
    M-->>R: documents
    R-->>S: entities
    S-->>C: domain result
    C-->>B: DTO (JSON) + status code
```

### Real-time notification fan-out

```mermaid
sequenceDiagram
    participant S as Any Service
    participant NS as NotificationService
    participant M as MongoDB
    participant RP as Redis pub/sub<br/>channel notifications
    participant API as Each API instance<br/>(subscriber)
    participant SSE as SseEmitterRegistry
    participant B as User browser
    S->>NS: notify(userId, type, payload)
    NS->>M: insert Notification (persisted, unread)
    NS->>RP: PUBLISH notifications {userId, id}
    RP-->>API: message
    API->>SSE: find emitters for userId
    SSE-->>B: event: notification (data JSON)
    Note over B: If offline, notifications are fetched<br/>at next login via GET /api/notifications?unread=true
```
