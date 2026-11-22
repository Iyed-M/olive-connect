# Appendix D — OpenAPI excerpt

The API is documented by springdoc. When the evidence runner has access to the
API port, it writes the complete machine-readable contract from
`report/evidence/openapi.json`; the report build records that artifact in the
manifest. This avoids embedding a stale specification in the report.

The stable contract conventions are:

```json
{
  "basePath": "/api",
  "format": "application/json",
  "dates": "ISO-8601 UTC",
  "errors": "RFC 7807 ProblemDetail",
  "authentication": "Bearer JWT except for documented public paths"
}
```

The human-readable endpoint catalogue is
[`docs/06-api-security.md`](../../docs/06-api-security.md), and the evidence
JSON is the appendix artifact for the particular build/deployment.
