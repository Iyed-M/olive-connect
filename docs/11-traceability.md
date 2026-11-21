# 11 — Traceability Matrix (consistency proof)

The subject states that **consistency between the class model, the NoSQL schema, the Spring Boot API and the deployment** is checked closely. This matrix is kept up to date throughout the project and copied into the report as an appendix. Any rename has to be applied across a whole row.

## 1. Class ⇄ collection ⇄ layers ⇄ API ⇄ UI

| UML class | Transformation rule(s) | Collection / location | Entity (`@Document`) | Repository (DAO) | Service | Controller · base path | React feature |
|---|---|---|---|---|---|---|---|
| User | R1, R9 | `users` | `user.User` | `UserRepository` | `UserService`, `AuthService` | `AuthController /auth`, `MeController /me`, `UserController /users` | `auth/`, `admin/users`, `admin/UserForm`, `routes/_app.change-password.tsx` |
| FarmerProfile | R2 | `users.farmerProfile` | `user.FarmerProfile` (embedded) | — | `UserService` | `/me`, `/users` | `admin/UserForm` |
| Role (enum) | R9 | `users.roles[]` | `user.Role` | — | — | — | `auth/types.ts` (same values as the Java enum) |
| Delegation (enum) | R2 | `users.farmerProfile.delegation` (code, e.g. `SAKIET_EZZIT`) | `user.Delegation` | — | — | — | labels in `admin/users.ts` |
| (User admin DTOs — no class, write and read models) | — | `users` | `user.UserDto`, `UserCreateDto`, `UserUpdateDto`, `FarmerProfileDto` | — | `UserService` | `UserController /users` | `admin/users.ts` |
| RegistrationRequest | R1 | `registration_requests` | `user.RegistrationRequest` | `RegistrationRequestRepository` | `RegistrationService` | `RegistrationController /registrations` | `routes/register.tsx`, `routes/_app.admin.registrations.tsx` |
| Orchard | R1, R4, R10, R11 | `orchards` (+ `externalRef`, import key) | `orchard.Orchard` | `OrchardRepository` | `OrchardService` | `OrchardController /orchards` | `features/farmer/OrchardsPage`, `features/farmer/OrchardForm`, `features/farmer/OrchardMap` |
| (Public map DTOs — no class, anonymous read model) | R4 | `orchards` (projection) | `map.PublicOrchardFeature`, `map.PublicFeatureCollection` | `MongoTemplate` (projection) | `MapService` | `MapController /map/public` | `features/public/PublicMapPage` |
| Plot | R2 | `orchards.plots[]` | `orchard.Plot` (embedded) | — | `OrchardService` | `/orchards` | `features/farmer/OrchardForm` |
| GeoPoint | R10 | `orchards.location` | `orchard.GeoPoint` (embedded GeoJSON) | — | `OrchardService` | `/orchards` | `features/farmer/OrchardMap` |
| OliveVariety | R1 (natural key) | `olive_varieties` | `orchard.OliveVariety` | `OliveVarietyRepository` | `OrchardService` (Redis cached), `OliveVarietySeeder` | `OliveVarietyController /varieties` | `features/farmer/OrchardForm`, `intranet/Reference` |
| HarvestRequest | R1, R3, R4, R11, R14 | `harvest_requests` | `harvest.HarvestRequest` (+ embedded `OrchardSnapshot`) | `HarvestRequestRepository` | `HarvestRequestService`, weighted `PriorityCalculator` | `HarvestRequestController /harvest-requests` (paged GET + actions) | `farmer/HarvestRequestForm`, `farmer/MyRequests`, `farmer/HarvestRequestDetail`, `farmer/RequestActions`, `farmer/harvestRequests`, `collection/RequestQueue` |
| StatusChange | R2 | `*.statusHistory[]` | `common.StatusChange` (embedded) | — | each owning service | in detail DTOs | `components/Timeline` |
| CollectionRound | R1, R5, R14 | `collection_rounds` | `round.CollectionRound` | `CollectionRoundRepository` | `CollectionRoundService`, `PlanningService` | `CollectionRoundController /rounds`, `PlanningController /planning` | `collection/PlanningBoard`, `teamleader/MyRounds`, `teamleader/RoundRunner` |
| RoundStop | R2 | `collection_rounds.stops[]` | `round.RoundStop` (embedded) | — | `CollectionRoundService` | `/rounds/{id}/stops/{order}` | `teamleader/RoundRunner` |
| Worker | R1, R2 | `workers` (+ `externalRef`, import key) | `resource.Worker` | `WorkerRepository` | `ResourceService`, `ResourcesCsvImporter` | `/workers` | `logistics/WorkerForm`, `routes/_app.logistics.workers.*` |
| Equipment / Tractor / Bin / Tool | R1, R8 | `equipment` (`type`, `_class`) | `resource.Equipment` + subclasses | `EquipmentRepository` | `ResourceService` | `/equipment` | `logistics/EquipmentForm`, `routes/_app.logistics.equipment.*` |
| Unavailability | R2 | `workers/equipment.unavailabilities[]` | `resource.Unavailability` | — | `ResourceService` | `/…/{id}/unavailabilities` | `logistics/UnavailabilityPanel`, `logistics/ResourceCalendar` |
| ResourceBooking | R6 | `resource_bookings` | `resource.ResourceBooking` | `ResourceBookingRepository` | `BookingService`, `ResourceAvailabilityService` | `/resources/calendar`, `/resources/availability` | `collection/RoundForm`, `logistics/ResourceCalendar` |
| SupplyOrder | R1, R3 | `supply_orders` | `resource.SupplyOrder` | `SupplyOrderRepository` | `SupplyService` | `/supply-orders` | `logistics/SupplyOrders` |
| Mill | R1, R10 | `mills` | `mill.Mill` (minimal in M10) | `MillRepository` | `MillService`, `MillsJsonImporter`, `MillCapacityJsonImporter` | `MillController /mills` | `mill/CapacityPage` |
| MillReception | R1, R3, R11, R13 | `mill_receptions` | `mill.MillReception` | `MillReceptionRepository` | `MillReceptionService` | `/mills/{id}/receptions`, `/receptions` | `mill/ReceptionForm`, `farmer/MyReceptions` |
| QualityAssessment | R2 | `mill_receptions.quality` | `mill.QualityAssessment` (embedded) | — | `MillReceptionService` | idem | idem |
| FieldAlert | R1, R2, R10 | `field_alerts` (+ GridFS `alerts`) | `alert.FieldAlert` (minimal in M10), `alert.AlertType`, `alert.AlertSeverity`, `alert.AlertSource`, `alert.AlertStatus` | `FieldAlertRepository` | `FieldAlertService`, `imports.AutoAlertService` | `FieldAlertController /alerts` | `farmer/AlertForm`, `collection/AlertsBoard` |
| WeatherReading | R7, R12 | `weather_readings` (time-series) | `imports.WeatherReading` (`@TimeSeries`) | — (bulk writes through `MongoTemplate`) | `WeatherJsonImporter` | (import only), `/dashboard` | dashboard charts |
| MaturityReading | R7, R12 | `maturity_readings` (time-series) | `imports.MaturityReading` (`@TimeSeries`) | — (bulk writes through `MongoTemplate`) | `MaturityCsvImporter` | `/orchards/{id}/maturity` (planned) | orchard detail chart (planned) |
| Notification | R7 (+TTL) | `notifications` | `notification.Notification` | `NotificationRepository` | `NotificationService` | `NotificationController /notifications` (SSE `/events` in M8) | `notifications/NotificationBell`, `notifications/NotificationsPage`, `notifications/notifications` (30 s polling) |
| ImportJob / ImportError | R1 / R2 | `import_jobs` | `imports.ImportJob`, `imports.ImportError` (embedded), `imports.ImportStatus`, `imports.ImportSource` | `ImportJobRepository` | `ImportService`, `ImportJournalService`, `ImportScheduler` | `ImportController /imports` | `features/imports/ImportJournalPage`, `features/imports/ImportDetailPage`, routes `/intranet/imports`, `/intranet/imports/$id` |
| SystemSettings | R1 | `system_settings` (`_id: "global"`) | `settings.SystemSettings` | `SystemSettingsRepository` | `SettingsService` | `SettingsController /settings` | `features/imports/SettingsPage`, route `/intranet/settings` |
| (System status — no class, read model) | — | none (pings MongoDB and Redis) | `system.SystemStatusDto` | — | `SystemStatusService` | `SystemStatusController /system` | `lib/api.ts` (`getSystemStatus`, no page calls it since M9 replaced the status page with the public map) |
| (KPIs — no class, read model) | R13 | aggregation over `mill_receptions`, `collection_rounds` | DTOs `dashboard.dto.*` | `KpiRepositoryCustom` | `KpiService` | `DashboardController /dashboard` | `dashboard/` |

## 2. Requirement ⇄ use case ⇄ endpoint ⇄ test

| Requirement | Use case / SD | Endpoints | Tests |
|---|---|---|---|
| (M0, no FR) | System status / walking skeleton | `GET /system/status` | T-SYS-1 |
| FR-6.2 (M1a) | Log in / SD-1 | `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `GET /me` | T-AUTH-1, T-AUTH-3..5, T-AUTH-7, T-AUTH-9..11, T-SEC-5 |
| FR-6.2 (M1b) | Admin manages Users / SD-10 | `GET /users`, `POST /users`, `GET` and `PUT /users/{id}`, `POST /users/{id}/enable`, `/disable` | T-USR-1..4, T-SEC-4, T-AUTH-6 |
| FR-1.1 | Manage orchards | `/orchards` | T-SEC-1, T-ORCH-1..6 |
| FR-1.2 | Declare harvest request / SD-2 | `POST /harvest-requests` | T-HR-1..3 |
| FR-1.3 | Modify / cancel / SD-4 | `PUT /harvest-requests/{id}`, `/cancel` | T-HR-4..6 |
| FR-1.4 | Validate / reject | `/validate`, `/reject` | T-HR-7 |
| FR-1.5 | Plan round / SD-3 | `POST /rounds`, `/planning/suggestions` | T-RD-1..5 |
| FR-1.6 | Notifications | `/notifications` (M3), `/events` (M8) | T-HR-1, T-RD-1, T-ML-1 |
| FR-1.7 | Update round / SD-6 | `/rounds/{id}/start`, `/stops/{order}`, `/complete` | T-RD-6..8 (`CollectionRoundExecutionControllerTest`) |
| FR-2.x | Report & handle alert / SD-5 | `/alerts/**` | T-AL-1..4 |
| FR-3.x | Resources | `/workers`, `/equipment`, `/resources/*`, `/supply-orders` | T-RS-1..2, T-RD-2 |
| FR-4.x | Map | `/map/public`, `/map/private` | T-SEC-3, T-MAP-1..3, T-PERF-1 |
| FR-5.x | Dashboard / SD-9 | `/dashboard/*` | T-KPI-1..2 |
| FR-6.1 (M9) | Registration request and approval / SD-REG | `POST /registrations`, `GET /registrations`, `POST /registrations/{id}/approve`, `/reject`, `PATCH /me/password` | T-REG-1..8 |
| FR-6.2 | Accounts | `/users` | T-AUTH-* |
| FR-6.3–6.4 (M10) | Import / SD-8 | `GET /imports`, `GET /imports/{id}`, `POST /imports/run`, `POST /imports/upload`, `GET` and `PUT /settings` | T-IM-1..8 |
| FR-6.5 | Intranet | `/orchards`, `/equipment`, `/rounds`, `/imports` (ADMIN/LM) | E2E |
| NFR-4 | Performance | all reads | T-PERF-1, index tests |
| NFR-5 | Security | all | T-SEC-*, T-AUTH-* |
| NFR-6 | Availability / backups | — | restore drill, Kuma report |

## 3. Deployment consistency checklist

- [x] (M0) Service names and ports agree: `web` publishes only port 80 (`HTTP_PORT`) and forwards `/api/` to `api:8080` (`frontend/nginx.conf`), `api` listens on 8080 (`server.port: ${API_PORT:8080}`, overridden only for host dev), `mongo` and `redis` publish nothing, and the Actuator shares port 8080 (healthcheck `http://localhost:8080/actuator/health` in `deploy/docker-compose.yml`).
- [x] (M0) `MONGO_URI` and `REDIS_HOST` in `deploy/docker-compose.yml` are the variables read by `application.yml`.
- [x] (M0) The commands in `.github/workflows/ci.yml` match [10](10-deployment.md) §2 (`./mvnw -B -U verify`, `pnpm install --frozen-lockfile`, `pnpm lint`, `pnpm test`, `pnpm build`, `docker compose -f deploy/docker-compose.yml build`).
- [ ] (M13) Env variable names in `application-prod.yml` match `.env.example` and `docker-compose.yml`.
- [ ] (M13) Nginx `location` blocks cover every API path prefix of section 1 (`/api/` catch-all, plus specific rules for `/api/events`, `/api/map/public` and `/api/auth/login`).
- [ ] The indexes created by the production migration are exactly the ones listed in [05](05-nosql-schema.md) §4 (an automated test compares `getIndexInfo()` with the expected list).
- [ ] Roles in `SecurityConfig` and `@PreAuthorize` match the [role matrix](06-api-security.md#43-role--resource-matrix).
- [ ] The frontend route guards (`RequireRole`) use the same values as the Java `Role` enum (hand-written union in `frontend/src/auth/`).
- [ ] (M13) The Actuator metrics used by the Grafana dashboards exist (dashboard provisioning is tested on staging).
