# AGENTS.md

OliveConnect: smart olive-collection platform (Spring Boot + MongoDB/Redis API, React + Vite + pnpm frontend, Docker/Nginx deployment). Work is planned as vertical slices in `MILESTONES.md`; design reference (names, rules, schemas) lives in `docs/01..11`. All docs and code are written in English.

Keep it simple (KISS): the team must understand and defend every line; prefer plain Spring code and standard conventions over clever abstractions.

## Docs and diagrams must stay in sync with the code

The design docs (`docs/01–11`) and their Mermaid diagrams are graded deliverables. The subject checks consistency between the class model, the NoSQL schema, the API and the deployment.

- Any change to entities, collections, indexes, endpoints, roles, state transitions, business flows, UI routes or infrastructure **must update the matching docs and diagrams in the same PR**. The procedure, the change → doc mapping and the diagram conventions are in `docs/agents/doc-sync.md`. Follow it before every PR.
- Use the names in `docs/11-traceability.md`. A rename is applied everywhere in the same PR.
- Before opening a PR, run the `check-docs` skill, `scripts/check-diagrams.sh` and `./mvnw verify`.

## Agent skills

### Issue tracker

Issues live in the project's GitHub repo (via the `gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
