# Tests and evidence

The project follows a test pyramid: pure business rules are covered by unit
tests, HTTP contracts by Spring tests, persistence paths by Testcontainers,
frontend behavior by Vitest and Testing Library, and the release journey by
Playwright. The scenario catalogue in
[`docs/09-testing.md`](../../docs/09-testing.md) maps nominal and alternative
paths to test classes.

The evidence collector produces a timestamped manifest containing:

- backend JaCoCo coverage and the normal Maven test result;
- a generated `test-scenarios.tsv` summary from the Surefire XML results;
- the OpenAPI JSON exported from the running API;
- KPI timing and `explain()` output from the explicit performance integration
  test;
- k6 summary data for the 100-VU mixed scenario;
- mobile/desktop screenshots for configured Role accounts;
- Grafana panel captures and the restore-drill log.

Missing external services are recorded as `not collected` by default. A release
claim is made only when the corresponding artifact exists. This distinction is
important for an oral defence: checked-in code proves the procedure, while
runtime artifacts prove the particular deployment run.
