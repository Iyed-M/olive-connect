# Appendix C — Index list

MongoDB index declarations live beside the Java entities and are reconciled by
`MongoIndexInitializer`. The authoritative definitions and justifications are
in [`docs/05-nosql-schema.md`](../../docs/05-nosql-schema.md); this appendix
groups them by query purpose.

| Query purpose | Collections and representative keys |
|---|---|
| Identity and ownership | `users.email`, `users.farmerProfile.memberNumber`, `orchards.ownerId`, `workers.code`, `equipment.code` |
| Geospatial reads | `orchards.location`, `mills.location`, `field_alerts.location` using `2dsphere` |
| Workflow queues | `harvest_requests(status, priorityScore)`, `collection_rounds(date, status)`, `field_alerts(status, createdAt)` |
| User history | `harvest_requests(farmerId, createdAt)`, `notifications(recipientId, read, createdAt)`, `mill_receptions(farmerId, receivedAt)` |
| Booking and planning | resource booking overlap keys, round date/team-leader/mill keys, stop request lookup |
| Imports and time series | import dataset/status/checksum/start time keys and orchard/delegation timestamp keys |
| KPI filters | season/date and denormalized orchard, delegation, variety and mill keys on `mill_receptions` |

The exact MongoDB key syntax, names, partial filters, uniqueness and TTL are
kept in the schema document and should be checked against the output of
`getIndexInfo()` in the production-shaped MongoDB run.
