# Likely oral-exam questions and answers

## Why MongoDB instead of a relational database?

The domain contains bounded nested aggregates, GeoJSON queries, time-series
readings and aggregation pipelines. MongoDB fits those shapes directly, while
the replica set supplies the transactions needed for round plus booking
consistency. A relational database would also work, but would add join tables
and a second geospatial/time-series design without improving this scope.

## What is embedded and what is referenced?

Plots, round stops, quality assessment and status histories are embedded because
they are bounded and read with the owning document. Users, mills, workers,
equipment, bookings, imports and notifications have independent lifecycles or
query patterns and are referenced. Snapshots preserve historical facts.

## Why these indexes?

Each index answers a real screen or rule: owner/history, queue priority,
geospatial map/nearest queries, booking overlap, import journal filters, TTL
notifications and KPI season/date dimensions. Compound order follows the
leading equality/range fields used by the query. `getIndexInfo()` and the
explicit performance test are the evidence checks.

## When are transactions used?

Creating a CollectionRound and its ResourceBookings is a multi-document
consistency boundary, so it uses MongoDB transactions on the replica set. Most
single-document changes rely on atomic MongoDB writes and optimistic versioning;
the `@Version` conflict becomes a 409 `STALE_VERSION` response.

## How is security enforced?

The JWT carries Roles, the security filter enforces authenticated URLs, and
`@PreAuthorize`/service rules enforce role and ownership. Public map and
registration endpoints are explicit exceptions. Refresh sessions are opaque,
rotated and revocable in Redis; passwords are BCrypt-hashed and login attempts
are rate-limited.

## Why Redis if it is not the source of truth?

Redis is appropriate for short-lived refresh sessions, cache entries, rate
limits and pub/sub fan-out. Loss of Redis may log users out or reduce
performance, but MongoDB still contains the business facts.

## Why SSE instead of WebSockets?

OliveConnect needs one-way server-to-browser notification events. SSE is simpler
to operate through Nginx and reconnects naturally; Redis pub/sub distributes an
event between API instances.

## How are imports safe?

The importer validates typed fields, records row-level errors, deduplicates on
external references or checksums, and records an ImportJob. Accepted data is
persisted only after parsing; rejected rows remain explainable in the journal.

## What would you improve next?

The next operational improvements are richer offline conflict resolution,
additional KPI dimensions and horizontal API replicas. They are future work,
not undocumented behavior claimed by the final report.
