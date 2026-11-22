# Data integration

External data enters through the import inbox or the authenticated upload/run
API. Reference datasets include varieties and mills; dynamic datasets include
orchards, resources, weather, maturity, mill capacity and historical
receptions. The simulator in `data-simulator/` produces contract-shaped files
for repeatable local demonstrations.

Each importer parses typed rows, records line-level `ImportError` values,
deduplicates with an external reference or checksum, persists accepted facts,
and records an `ImportJob` journal. A scheduled job watches the inbox while an
Admin can trigger a reference or dynamic run. Auto-alert rules turn relevant
weather or maturity facts into FieldAlerts without bypassing the normal alert
state machine.

Formats, conformity rules and the pipeline are specified in
[`docs/07-data-import.md`](../../docs/07-data-import.md). This separation keeps
external data reproducible and makes a rejected row explainable during the
defence.
