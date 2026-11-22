# Design: architecture, UML, API and security

## Architecture and layering

Nginx serves the React SPA and proxies `/api/` to one Spring Boot application.
The API uses feature packages such as `harvest`, `round`, `resource`, `mill`,
`alert`, `imports` and `dashboard`. A request is handled by a Controller,
delegated to a Service for business rules, persisted through a Spring Data DAO,
and represented by a domain Entity. Constructor injection keeps dependencies
explicit and testable. The architecture and package diagram are in
[`docs/03-architecture.md`](../../docs/03-architecture.md).

## Domain model and flows

The UML model covers the aggregate boundaries, embedded histories and state
machines. Bounded data such as Orchard plots, Round stops and status histories
is embedded; shared or independently queried data such as Users, Mills and
Resources is referenced. The sequence diagrams in
[`docs/04-uml-design.md`](../../docs/04-uml-design.md) show both nominal and
alternative paths, including rejected requests, capacity warnings and stale
versions.

## API contract

The API is rooted at `/api`, uses JSON and ISO-8601 dates, and returns RFC 7807
`ProblemDetail` errors. The endpoint catalogue and DTO examples are in
[`docs/06-api-security.md`](../../docs/06-api-security.md). OpenAPI is exposed
by springdoc; the final JSON export is included when the evidence runner is
pointed at a running API.

## Security model

Authentication is a 15-minute HS256 JWT plus a seven-day, rotated opaque
refresh session in Redis. URL-level rules provide the public map, login,
registration and health exceptions; method-level rules enforce Role. Services
still apply ownership checks, so a Farmer cannot infer another Farmer's data
by changing an identifier. The security design and role matrix are maintained
in the API document and the traceability appendix.
