# NoSQL schema and persistence choices

MongoDB is the system of record because the domain contains nested business
objects, geospatial queries, time-series readings and aggregation pipelines.
Redis is deliberately not a source of truth: it stores refresh sessions,
caches, rate-limit counters and notification fan-out state with TTLs.

## Embedding versus referencing

The schema embeds bounded, read-together values: `plots[]` in `orchards`,
`stops[]` and status histories in their owning documents, and quality data in a
`mill_receptions` document. It references data that has an independent
lifecycle or query pattern: `users`, `mills`, `resource_bookings`, imported
readings and notifications. Snapshot fields on a HarvestRequest and a MillReception
preserve the facts needed for historical interpretation after a source changes.

## Indexes and queries

Indexes are declared beside the entities and reconciled by
`MongoIndexInitializer`. They cover ownership and recent history, active
workflow queues, geospatial map/nearest queries, booking overlap checks, import
journals and KPI filters. The index appendix lists the checked-in definitions;
the performance evidence records the KPI aggregation `explain("executionStats")`
output when that integration run is available.

The full collection samples, transformation rules, Redis keys and persistence
API justification are in [`docs/05-nosql-schema.md`](../../docs/05-nosql-schema.md).
