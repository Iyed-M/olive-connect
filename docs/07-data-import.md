# 07 — Data Import & Intranet Enrichment (subject §III.2)

## 1. Three data sources

```mermaid
flowchart LR
    subgraph S1["Source 1 — Reference data (simulated)"]
        r1[orchards.csv]
        r2[plots.csv]
        r3[varieties.json]
        r4[resources.csv<br/>workers, tractors, bins, tools]
        r5[mills.json]
    end
    subgraph S2["Source 2 — Dynamic data (simulated)"]
        d1[weather_YYYYMMDDHHmm.json]
        d2[maturity_YYYYMMDDHHmm.csv]
        d3[mill_capacity_YYYYMMDD.json]
    end
    subgraph S3["Manual entry"]
        m1[Intranet app<br/>admin / logistics]
        m2[Operational app<br/>farmers, managers, team leaders, mills]
    end
    SIM[data-simulator] --> S1
    SIM --> S2
    S1 -->|daily 02:00| IMP[ImportService<br/>parse → validate → upsert]
    S2 -->|every 15 min| IMP
    IMP --> DB[(MongoDB)]
    IMP --> J[(import_jobs)]
    IMP --> AA[AutoAlertService]
    S3 --> API[REST API] --> DB
```

| Source | Datasets | Frequency | Target collection | Write mode |
|---|---|---|---|---|
| 1 (reference) | varieties, mills, orchards + plots, workers, equipment | daily (cron `0 0 2 * * *`) + manual trigger | `olive_varieties`, `mills`, `orchards`, `workers`, `equipment` | **upsert** on natural key (`code`, `externalRef`) — preserves manual enrichments on fields not provided by the source |
| 2 (dynamic) | weather, maturity, mill capacity | every 15 min (`fixedDelay`) | `weather_readings`, `maturity_readings` (insert), `mills.availableCapacityKg` (update), `orchards.currentMaturityIndex` (update, R11) | insert / targeted update |
| Manual | everything else (requests, rounds, alerts, receptions, enrichment) | real time | all | via API |

## 2. File formats (contract with the simulator)

**orchards.csv** (`;`-separated, UTF-8, header required)
```text
externalRef;name;memberNumber;lng;lat;delegation;plotCode;varietyCode;areaHa;treeCount;plantingYear;irrigated
ORC-0001;Henchir El Mezraa;ADH-0421;10.7603;34.7998;Sakiet Ezzit;P1;CHEMLALI;4.5;450;1998;false
ORC-0001;Henchir El Mezraa;ADH-0421;10.7603;34.7998;Sakiet Ezzit;P2;CHETOUI;1.2;180;2015;true
```
Rows are grouped by `externalRef`, and each group becomes one orchard document with its `plots[]` (rule R2). The delegation name is normalised to the `Delegation` code (`Sakiet Ezzit` becomes `SAKIET_EZZIT`). The orchard is upserted on `orchards.externalRef`, and fields entered by hand are kept (including plots that the file does not list: the plots of the file are matched by `plotCode`).

**varieties.json** (upsert on `code`)
```json
{ "generatedAt": "2026-11-21T02:00:00Z",
  "varieties": [ { "code": "CHEMLALI", "name": "Chemlali", "avgOilYieldPct": 21, "optimalMaturityMin": 3.0, "optimalMaturityMax": 4.5 } ] }
```

**mills.json** (upsert on `code`)
```json
{ "generatedAt": "2026-11-21T02:00:00Z",
  "mills": [ { "code": "MIL-SFX-01", "name": "Huilerie Sakiet Ezzit", "lng": 10.78, "lat": 34.81, "dailyCapacityKg": 60000 } ] }
```

**resources.csv** (`;`-separated, `type` is `WORKER`, `TRACTOR` or `BIN`; workers are upserted on `workers.externalRef`, tractors and bins on `equipment.code` = `externalRef`)
```text
externalRef;type;name;phone;capacityKg
WRK-0001;WORKER;Sami Trabelsi;+21620123456;
BIN-0007;BIN;Bin 7;;4000
```

**File name patterns.** `orchards.csv`, `varieties.json`, `mills.json` and `resources.csv` are exact names (source 1, imported in that order: varieties, mills, orchards, resources). `weather_YYYYMMDDHHmm.json`, `maturity_YYYYMMDDHHmm.csv` and `mill_capacity_YYYYMMDD.json` are source 2. A file whose name matches nothing is rejected (`FAILED`, moved to `rejected/`) when found in the inbox, and refused with 400 when uploaded.

**weather_202611210615.json**
```json
{ "generatedAt": "2026-11-21T06:15:00Z", "source": "SIM-WEATHER",
  "readings": [ { "stationId": "SFX-03", "delegation": "Sakiet Ezzit", "timestamp": "2026-11-21T06:15:00Z",
                  "temperatureC": 14.2, "humidityPct": 81, "rainfallMm": 2.4, "windKmh": 12 } ] }
```

**maturity_202611210600.csv** (`;`-separated, appended to the `maturity_readings` time series, then `orchards.currentMaturityIndex` is set from the latest reading)
```text
orchardExternalRef;timestamp;maturityIndex;source
ORC-0001;2026-11-21T06:00:00Z;3.8;SENSOR
```

**mill_capacity_20261121.json**
```json
{ "date": "2026-11-21", "mills": [ { "millCode": "MIL-SFX-01", "dailyCapacityKg": 60000, "availableCapacityKg": 42000 } ] }
```

## 3. Conformity checks (validation rules)

| Level | Rule | Example of rejection reason |
|---|---|---|
| File | allowed extension, file name pattern, UTF-8, size < 20 MB, header present | `FILE_FORMAT: missing column 'lat'` |
| File | **idempotency**: SHA-256 checksum not already imported | skipped, not an error |
| Record — types | number/date parsing, enum values | `line 14, maturityIndex: 'abc' is not a number` |
| Record — ranges | lng ∈ [7, 12], lat ∈ [30, 38] (Tunisia bbox); maturityIndex ∈ [0, 7]; rainfall ≥ 0; humidity ∈ [0, 100]; areaHa > 0; capacity ≥ 0; timestamp not in the future (> 1 h) | `line 3, lat: 48.85 outside Tunisia` |
| Record — references | `varietyCode` exists; `orchardExternalRef` exists; `memberNumber` matches a FARMER (or the orchard is created unassigned and flagged) | `line 7, varietyCode 'XYZ' unknown` |
| Record — consistency | sum of plot areas ≤ declared orchard area + 5 %; duplicate (same meta + timestamp) | `duplicate reading` |

`line` is the 1-based line of the CSV file (the header is line 1) or the 1-based index of the element in the JSON array. File-level failures use line 0 and field `file`. Each rejected record becomes an `ImportError {line, field, reason}`. The job ends with `SUCCESS` (0 rejects), `PARTIAL` (some rejects) or `FAILED` (the file is unusable or more than 50 % of records were rejected; in that case **nothing is committed**).

## 4. Processing pipeline

```mermaid
flowchart TD
    A[Scheduler tick] --> B[List /data/inbox/*]
    B --> C{checksum already<br/>in import_jobs?}
    C -- "yes (SUCCESS or PARTIAL)" --> Z[move to processed/,<br/>skip, no job]
    C -- no --> D[ImportJob RUNNING]
    D --> E[Parse streaming<br/>CSV / Jackson streaming JSON]
    E --> F{parse OK?}
    F -- no --> G[FAILED, move to rejected/]
    F -- yes --> H[Validate each record]
    H --> I[findDuplicates, then count rejects]
    I --> K{reject ratio > 50 %?}
    K -- yes --> G
    K -- no --> L[Persist valid records<br/>and update orchards.currentMaturityIndex,<br/>mills.availableCapacityKg]
    L --> M[AutoAlertService.evaluate dynamic imports only:<br/>rain ≥ threshold in 24 h?<br/>maturity in optimal window?]
    M --> N[SUCCESS / PARTIAL,<br/>move to processed/]
```

**Implementation**

- `ImportScheduler` uses `@Scheduled` with **ShedLock**, backed by MongoDB (collection `shedLock`), so that only one instance runs a given import when the API is scaled: cron `0 0 2 * * *` for the reference files, `fixedDelay` of 15 minutes for the dynamic files.
- `ImportService` (package `tn.oliveconnect.imports`) holds one `DatasetImporter<T>` strategy per dataset (`OrchardCsvImporter`, `VarietiesJsonImporter`, `MillsJsonImporter`, `ResourcesCsvImporter`, `WeatherJsonImporter`, `MaturityCsvImporter`, `MillCapacityJsonImporter`). Each one implements `accepts(fileName)`, `parse`, `validate`, `findDuplicates` and `persist`, so adding a dataset only means adding a bean (open/closed principle).
- The inbox is `app.imports.inbox-dir` (`IMPORT_INBOX_DIR`, `/data/inbox`). Handled files move to `processed/` or `rejected/` inside it. `POST /api/imports/run` and the scheduler share `ImportService.run(source)`, and `POST /api/imports/upload` copies the file into the inbox before importing it.
- Batch writes use `MongoTemplate.bulkOps(BulkMode.UNORDERED, …)` in batches of 1000, which handles 10⁵ readings in seconds.
- **Spring Batch** is a possible alternative: it brings restartable chunked steps, but more configuration. We keep the lighter design and mention Spring Batch in the report as a possible improvement.
- Automatic alert rules come from `system_settings`: `rainAlertMm24h` (default 20, compared with the highest 24 h rainfall total of one station of the delegation), and maturity entering `[optimalMaturityMin, optimalMaturityMax]` of the variety. A rule creates a `FieldAlert` with `source = SENSOR` (`type` `RAIN` or `MATURITY`) only when no open alert of the same type exists for that orchard. The rules live in `AutoAlertService`, which runs after each successful dynamic import. Automatic alerts send no notification in M10.

## 5. Data simulator (`data-simulator/`)

A small **Python 3 script** using only the standard library: `python3 simulator.py <inbox dir> [--only reference|dynamic] [--loop MINUTES] [--history] [--orchards N] [--seed S]` (see `data-simulator/README.md`). It writes the files of §2 into the inbox directory (in Docker, the `inbox-data` volume). It:

1. generates the reference set once: around 1 500 orchards around Sfax (random points inside polygons of the delegations: Sakiet Ezzit, Sakiet Eddaier, El Ain, Agareb, Menzel Chaker, Jebeniana…), 5 mills, 300 workers, 60 tractors, 200 bins, the varieties, and 500 farmers;
2. writes dynamic files every N minutes into the shared inbox volume, with realistic patterns: maturity rises by about 0.05 per day and differs per variety, and rain events are random;
3. injects about 2 % **invalid records** on purpose (out-of-range values, unknown variety) to demonstrate the conformity checks;
4. offers a `--history` mode that generates 3 past seasons of weather, maturity and mill capacity readings files (10⁵–10⁶ documents), to show the index performance on massive data. It does not generate `mill_receptions` (M7 data).

## 6. Intranet enrichment app

The subject requires "a small intranet application dedicated to enriching data about orchards, equipment and rounds". It is implemented as a **separate section of the same React app** (`/intranet/*`), reachable only by **ADMIN** and **LOGISTICS_MANAGER**. In production, Nginx can also restrict this path to the cooperative's IP range (`allow 41.x.x.0/24; deny all;`), which makes it a true intranet.

| Screen | Purpose |
|---|---|
| Orchards data grid | Fix imported orchards: move pin on map, edit plots, assign owner (unassigned imports), boundary polygon drawing (Leaflet.draw) |
| Equipment data grid | Add serial numbers, capacities, maintenance notes |
| Rounds history | Correct quantities after the fact (with history entry), attach comments |
| Import journal (`/intranet/imports`, `/intranet/imports/$id`) | ADMIN only. List with `dataset` and `status` filters, detail with the rejected rows (line, field, reason) and a CSV download, manual run (`REFERENCE` or `DYNAMIC`) and file upload |
| Settings (`/intranet/settings`) | ADMIN only. Form for `rainAlertMm24h` and `defaultBinCapacityKg` (`system_settings`) |
| Reference data | Varieties (optimal maturity window), delegations (planned) |
