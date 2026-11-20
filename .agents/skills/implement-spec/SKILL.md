---
name: implement-spec
description: "Implement one OliveConnect milestone (e.g. `2`, `M2`, `M3a`) end to end as a single PR, using parallel implementer subagents."
disable-model-invocation: true
---

# Implement a milestone

The argument is a milestone id from `MILESTONES.md` (`2`, `M2`, `M3a`). The goal is **one draft PR on one branch** that ships the whole milestone, built by parallel **implementer subagents**.

Talk to subagents through **context pointers** (file paths, section headings, commit SHAs). Do not paste content they can read themselves.

## 1. Read the brief

Read, in this order:

- `AGENTS.md`, `CONTEXT.md`, `docs/adr/`
- `MILESTONES.md`: "How we work", "Keeping docs & diagrams in sync", and the milestone section (Goal, Demo, Scope, Done when, Refs)
- every doc section in **Refs**, plus `docs/11-traceability.md` (naming contract) and `docs/agents/doc-sync.md`
- the existing code the milestone builds on (`backend/src`, `frontend/src`)

Check that `main` already contains the milestones this one depends on (look at `git log main --oneline` and the overview table in `MILESTONES.md`). If a dependency is missing, stop and tell the user.

Done when you can explain every Scope bullet and every "Done when" check in terms of classes, endpoints and pages.

## 2. Ask only what the docs cannot answer

List every open question. Then **drop** each one that is answered by the docs, the code, `CONTEXT.md`, an ADR, or an obvious convention already used in the codebase.

- If no questions remain, do not ask anything. Go on to step 3.
- Otherwise ask the remaining ones **in one message**. Give each question your recommended answer so the user can reply "ok".
- A question is worth asking only if a wrong guess changes the data model, an endpoint, a role rule or the demo.

Record every answer that is a design decision in the matching doc, in this PR (see `docs/agents/doc-sync.md`). If it adds or changes a domain term, or is a hard-to-reverse choice, update `CONTEXT.md` or add an ADR with the `domain-modeling` skill.

## 3. Write the tickets

Write a task graph to `../.oliveconnect-work/<branch>/tickets.md`. This folder is outside the repo, so every subagent can read it. One section per ticket:

```
## T1 — <title>
Blocked by: none | T1, T2
Area: backend | frontend | docs
Files: the packages / folders it owns
Done when: concrete checks (tests, behaviour)
Pointers: MILESTONES.md §Mx, docs/05 §4.2, ...
```

Rules:

- Each ticket is a slice that can be tested on its own. A typical milestone has 2 to 5 tickets: backend domain + API with tests, frontend pages, then extras such as seed data or Redis caching.
- Two tickets that can run at the same time must not edit the same files. If they would, chain them with `Blocked by`.
- The frontend ticket can start in parallel with the backend ticket when the API contract in `docs/06` is already fixed.
- Docs are synced once at the end (step 6), not by each ticket.

## 4. Branch and draft PR

- Branch from up-to-date `main`: `mNN-<slug>` (e.g. `m02-orchards`, `m03a-...`).
- Push the branch, then `gh pr create --draft` with the title `Mx: <milestone title>`. Use `.github/pull_request_template.md` as the body.

## 5. Run implementer subagents

For every ticket on the **frontier** (all its blockers merged), start an implementer subagent **in the background**. Run as many at the same time as the frontier allows.

- Each subagent works in its own git worktree, on its own branch:
  `git worktree add ../.oliveconnect-work/<branch>/wt-T1 -b <branch>-t1 <branch>`
- The prompt is short: "Run the `implement` skill for ticket T1 in `../.oliveconnect-work/<branch>/tickets.md`. Worktree: `<path>`. Base branch: `<branch>`."

When a subagent finishes:

1. Merge its branch into the milestone branch: `git merge --no-ff <branch>-t1 -m "Merge T1: <title>"`. Resolve conflicts with the `resolving-merge-conflicts` skill. Run the tests of the touched area.
2. Mark the ticket done in `tickets.md`.
3. Start subagents for tickets that are now unblocked.

## 6. Finish the milestone

When every ticket is merged:

1. Run the `check-docs` skill, then `scripts/check-diagrams.sh`. Commit the doc changes as `Mx: docs sync`.
2. Run the `code-review` skill against `main`. The spec is the milestone section of `MILESTONES.md`. Give all findings to **one** implementer subagent to fix, then merge its work.
3. Run the full checks: `cd backend && ./mvnw verify`, and `cd frontend && pnpm lint && pnpm test && pnpm build`. All must pass.
4. Push. Tick the PR checklist items you verified. Leave the human-only items unticked (demo on the production compose, 360 px check) and say so in the PR body.
5. Mark the PR ready for review: `gh pr ready`.
6. Remove the worktrees (`git worktree remove`) and delete the merged ticket branches.

## 7. Report

Tell the user, briefly:

- the PR link
- the decisions you made without asking, and where they are documented
- what the human must still check by hand: the demo script, the phone layout, and that the team understands the diff
