---
name: check-docs
description: Sync docs/01-11 and their Mermaid diagrams with the code. Use at the end of a milestone, before merging a milestone PR, or when asked to "check docs" / "sync docs". Argument `all` runs the strict whole-repo pass (M14).
---

# Check docs

The design docs are graded on consistency between class model, NoSQL schema, API and deployment. Code is the source of truth for what is built; docs for future milestones may stay as planned. Never edit `docs/agents/doc-sync.md`.

## 1. Scope

- Default: `git diff main...HEAD` plus uncommitted changes (`git status`, `git diff`). Only touched areas are audited.
- Argument `all`: the whole repo, **strict**. Every documented item must exist in code, otherwise remove it from the docs or mark it "future work".

Done when you can list every changed file under its area.

## 2. Classify

Map each change to the docs and diagrams to update with the table in `docs/agents/doc-sync.md` §1, and follow its §2 diagram conventions.

## 3. Compare code against docs

For every touched area, read the code and the doc section side by side. Every item in code must appear in the doc and match exactly. In strict mode, check the reverse too.

| Code | Doc |
|---|---|
| `@Document` / embedded classes, fields | `docs/04` §1 classDiagram (Java names, `+Type name`) |
| Enum constants, allowed transitions | `docs/04` §3 stateDiagrams |
| `@RestController` mappings, roles, status codes | `docs/06` §2 catalogue (one method + path per row, relative to `/api`), §4.3 role matrix |
| `@Indexed`, `@CompoundIndex`, `@GeoSpatialIndexed` | `docs/05` §4 index tables |
| Collection names, relationships | `docs/05` (ER diagram, sample documents), `docs/11` §1 |
| Controllers, services, repositories in flows | `docs/04` §2 sequence participants (`participant X as <JavaClassName>`) |
| `deploy/` compose files, Dockerfiles, Nginx, `.github/workflows` | `docs/10` diagrams and config excerpts |
| Package structure, tech stack | `docs/03` §3, §4 |
| Frontend routes | `docs/08` §3 |
| Any name used in more than one place | `docs/11` (rename everywhere) |

Done when every row of this table has been checked for each touched area and each mismatch is either fixed (step 4) or queued as a question (step 6).

## 4. Fix

Edit the docs on the current branch. Update sample documents, volume tables and `docs/11` rows along with the diagrams.

## 5. Render

Run `scripts/check-diagrams.sh` (needs node, mermaid CLI via npx). Fix every render failure and rerun until it exits 0.

## 6. Report

Output two parts:

1. **Docs updated**: one line per doc file changed and why, ready to paste into the PR's "Docs updated" section.
2. **Questions**: each case where the code looks wrong against the design, or you cannot decide which side is right. Leave the design as is for those and ask the human.
