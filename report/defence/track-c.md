# Track C — What I built and why

## Public map, imports, KPIs and production

Track C covers the public map and registration journey, external data imports,
dashboard aggregations and the production envelope. The map uses a public
projection instead of exposing the full Orchard document. ImportJob keeps
checksum/status/error telemetry, while dynamic readings use time-series
collections. KPI queries start with a selective match and aggregate denormalized
MillReception facts so the dashboard does not join the live collection flow at
request time.

The deployment keeps MongoDB and Redis unpublished, places the API on an
internal network, and puts TLS/rate limits/IP allow-lists at Nginx. Prometheus,
Grafana, Loki, exporters, Uptime Kuma and the restore drill turn deployment
claims into inspectable evidence.

## Evidence to show

- public map and registration approval E2E journey;
- importer rejected-row journal and settings page;
- KPI `explain()` output, timings and dashboard role scope;
- Compose isolation, Grafana panels, k6 summary and restore log.

## Defence sentence

“The dashboard is a read model: it is derived from reception facts with
aggregation pipelines and can be cached, while the reception document remains
the source of truth. This separates operational writes from analytical reads.”
