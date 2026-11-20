---
name: implement
description: "Implement one ticket of an OliveConnect milestone in its own worktree. Usually started by the implement-spec skill."
disable-model-invocation: true
---

# Implement a ticket

You get a ticket id, the path to `tickets.md`, a worktree path and a base branch. Work **only** inside that worktree.

1. Read the ticket and every pointer it lists. Read `AGENTS.md`. Use the names from `docs/11-traceability.md` exactly.
2. Build the ticket with the `tdd` skill: Controller → Service → Repository → Entity with constructor injection, plain Spring code (KISS). The API tests use Testcontainers and cover at least one nominal and one alternative scenario.
3. Run the checks for the area you touched, often:
   - backend: `cd backend && ./mvnw test -Dtest=<TestClass>`, and `./mvnw verify` once at the end
   - frontend: `cd frontend && pnpm lint && pnpm test`, and `pnpm build` once at the end
   - if a failure is not obvious after one attempt, use the `diagnosing-bugs` skill instead of guessing
4. Do not edit `docs/` unless the ticket says so. The docs are synced once for the whole milestone.
5. Run the `code-review` skill against the base branch, with the ticket as the spec. Fix what it finds.
6. Commit on your branch with messages like `M2: <what changed>`. Do not push and do not merge.

Reply in under 100 words: your branch, the commits, and any deviation from the ticket or any decision the docs did not cover.
