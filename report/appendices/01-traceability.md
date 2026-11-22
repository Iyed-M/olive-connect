# Appendix A — Traceability matrix

The complete naming contract is [`docs/11-traceability.md`](../../docs/11-traceability.md).
The condensed matrix below keeps the report readable while preserving the
same canonical names.

| Domain class or read model | Collection/location | Main API | React surface |
|---|---|---|---|
| User / FarmerProfile / Role | `users` | `/auth`, `/me`, `/users` | `auth/`, `admin/users` |
| RegistrationRequest | `registration_requests` | `/registrations` | `register`, `admin/registrations` |
| Orchard / Plot / OliveVariety | `orchards`, `olive_varieties` | `/orchards`, `/varieties` | `features/farmer/` |
| HarvestRequest / StatusChange | `harvest_requests` | `/harvest-requests` | `features/farmer/`, `features/collection/` |
| CollectionRound / RoundStop | `collection_rounds` | `/rounds`, `/planning` | `features/collection/`, `features/teamleader/` |
| Worker / Equipment / Unavailability | `workers`, `equipment` | `/workers`, `/equipment` | `logistics/` |
| ResourceBooking / SupplyOrder | `resource_bookings`, `supply_orders` | `/resources/*`, `/supply-orders` | `logistics/`, `collection/` |
| Mill / MillReception / QualityAssessment | `mills`, `mill_receptions` | `/mills`, `/receptions` | `features/mill/`, `farmer/MyReceptions` |
| FieldAlert | `field_alerts` and GridFS `alerts` | `/alerts`, `/files/alerts` | `features/alerts/`, `features/collection/` |
| Notification / event read models | `notifications`, Redis pub/sub | `/notifications`, `/events` | `features/notifications/`, `features/events/` |
| ImportJob / ImportError / SystemSettings | `import_jobs`, `system_settings` | `/imports`, `/settings` | `features/imports/` |
| WeatherReading / MaturityReading | time-series collections | import pipeline | dashboard and orchard data |
| KPI read models | aggregation over `mill_receptions` | `/dashboard` | `features/dashboard/` |

Requirements, use cases, endpoints and tests are cross-referenced in section 2
of the source matrix. The matrix is intentionally an appendix, not a second
place to invent names: a rename is made in `docs/11` and then propagated here.
