# Doc sync procedure (for agents)

The design docs in `docs/01–11` are graded deliverables. The subject checks the **consistency between the class model, the NoSQL schema, the Spring Boot API and the deployment**. Code and docs must therefore never disagree on `main`.

**Rule: a PR that changes behaviour, data or infrastructure updates the matching docs and diagrams in the same PR.** No "docs later" PRs.

## 1. What to update, by type of change

| You changed… | Update | Diagram(s) |
|---|---|---|
| An entity or embedded class: add, rename or remove a field | `docs/04` §1 class diagram; `docs/05` sample document of that collection; `docs/11` §1 row | classDiagram in `docs/04` |
| A relationship (reference ⇄ embed, cardinality) | `docs/04` §1; `docs/05` §3 (rule ID cited) and the collection section | classDiagram, erDiagram in `docs/05` §2 |
| A new collection | `docs/05` §2 ER diagram + volume table, new §4.x section (sample doc, indexes, justification, rule IDs); `docs/11` §1 | erDiagram |
| An index | `docs/05` index table of the collection; `docs/05` §6 if it serves a key query | — |
| An endpoint: add, rename, change method, roles or status codes | `docs/06` §2 catalogue (**one endpoint per row**); `docs/06` §4.3 role matrix if access changed; `docs/11` §2 | — |
| An enum state or allowed transition | `docs/04` §3 state machine | stateDiagram |
| A business flow or a new alternative branch (new 4xx case) | `docs/04` §2 sequence diagram of that flow; `docs/09` §2 scenario row | sequenceDiagram |
| Requirements scope (feature added or dropped) | `docs/02` requirement tables + use-case diagram | flowchart (use case) |
| A UI route or page | `docs/08` §3 navigation | flowchart |
| Data import format or rule | `docs/07` | flowcharts |
| Docker, Nginx, CI, monitoring, backups | `docs/10` (diagrams + config excerpts must match the real files) | deployment / pipeline flowcharts |
| A name used in more than one place | rename it **everywhere** in the same PR (code, `docs/`, DTOs, React) — `docs/11` is the naming contract | all affected |

## 2. Diagram conventions (keep them machine-checkable)

The `check-docs` skill (see §3) parses the docs. Keep these formats:

- **Class diagram (`docs/04` §1)**: one `class Name { ... }` block per class, using the **Java class name**. Each attribute is on one line as `+Type name`, with the Java field name. Methods end with `()`. Enumerations carry `<<enumeration>>` and list the Java enum constants.
- **State machines (`docs/04` §3)**: state names are exactly the Java enum constants. Each `A --> B` arrow is one allowed transition in code.
- **Sequence diagrams**: participants are declared as `participant X as <JavaClassName>` for controllers, services and repositories. Messages that call the API start with the HTTP method and the full path (`POST /api/rounds`).
- **Endpoint catalogue (`docs/06` §2)**: one HTTP method and one path per row. The path is relative to `/api`, wrapped in backticks, with `{param}` placeholders.
- **Index tables (`docs/05` §4)**: the key spec goes in backticks exactly as MongoDB shows it (`{ status: 1, priorityScore: -1 }`).
- Never use `;` inside sequence diagram messages (Mermaid treats it as a line break). Quote node labels that start with `/`.

## 3. Automated checks

| Check | Where | What it catches |
|---|---|---|
| `scripts/check-diagrams.sh` | CI job `docs` (every PR) | Any Mermaid block that fails to render |
| `check-docs` skill (`.claude/skills/check-docs/SKILL.md`) | Run by the agent at the end of each milestone, before merging | Compares the branch diff with the docs (classes and fields ⇄ class diagram, enums and transitions ⇄ state machines, endpoints ⇄ `docs/06` catalogue, indexes ⇄ `docs/05` tables, collections ⇄ `docs/05` + `docs/11`, sequence participants ⇄ real classes). Fixes the docs on the same branch and prints the "Docs updated" lines for the PR |

`check-docs` modes:
- **default (per milestone)**: the branch diff must match the docs. Things that are in the docs but not yet implemented are allowed, because they belong to future milestones.
- **`check-docs all`** (strict, used in M14): the whole repo is checked, and docs and code must match **both ways**, so anything documented but never built must be removed from the docs.

## 4. Before opening a PR (agent checklist)

1. List the changes in the diff using the categories in §1.
2. Update every doc and diagram listed for those categories.
3. Run the `check-docs` skill, `scripts/check-diagrams.sh` and `./mvnw verify`.
4. In the PR description, fill in the "Docs updated" section: one line per doc file changed and why.
