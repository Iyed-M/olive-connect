# OliveConnect load test

The default run is the documented T-PERF-1 scenario: 100 VUs, 2 minutes ramp-up, 6 minutes at peak and 2 minutes ramp-down.

```sh
BASE_URL=https://staging.example.tn \
K6_EMAIL=farmer@example.tn K6_PASSWORD='a-farmer-password' \
k6 run load-tests/mixed-scenario.js
```

`BASE_URL` defaults to `http://localhost`; `K6_BASE_URL` is accepted as an alias. The account is optional for a local smoke run: without `K6_EMAIL` and `K6_PASSWORD`, the second request is the public system-status endpoint. With a Farmer account, the mixed scenario also exercises `GET /api/harvest-requests?mine=true`.

The script writes a JSON summary to `load-tests/k6-summary.json`. Set `K6_SUMMARY_FILE` to store evidence elsewhere. Short smoke runs can override `K6_VUS`, `K6_RAMP_DURATION`, `K6_HOLD_DURATION` and `K6_DOWN_DURATION`; the thresholds remain unchanged.
