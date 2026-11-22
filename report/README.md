# OliveConnect report and defence pack

This directory is the reproducible M14 deliverable. The design documents in
`docs/` remain the source of truth; the report chapters are a readable synthesis
and link back to those sources instead of copying a second, silently divergent
model.

## Build

From the repository root:

```sh
report/build.sh
```

The script renders every Mermaid source, derives the three focused class views
from `docs/04-uml-design.md`, and writes:

- `report/OliveConnect-report.pdf`
- `report/OliveConnect-report.docx`

It uses a local `pandoc` installation when available. On a clean machine with
Docker but without Pandoc, it uses the pinned `pandoc/extra` image. Runtime
evidence is optional at build time: the report records a missing artifact as
`not collected` rather than manufacturing a result. Use
`scripts/collect-evidence.sh` to collect it against a running stack.

Mermaid rendering needs a locally available Chrome or Chromium executable;
`CHROME_PATH` can select it explicitly. The repository's docs workflow supplies
the render environment.

## Contents

- `chapters/` — the ten report chapters required by the subject;
- `appendices/` — traceability, diagrams, indexes, OpenAPI and evidence;
- `defence/` — demo script, track sheets and oral-exam questions;
- `evidence/` — generated artifacts, never committed;
- `diagrams/` — generated SVG/PNG renders, never committed.
