# Deployment, monitoring and operations

The production-shaped Compose stack contains the web gateway, API, MongoDB
replica set, Redis, Prometheus, Grafana, Loki/Promtail, exporters, Alertmanager
and Uptime Kuma. MongoDB and Redis stay on the internal backend network; only
Nginx publishes HTTP/HTTPS. The API uses MongoDB authentication, a generated
replica-set keyfile and a Redis password in production.

Nginx provides TLS, security headers, request limits, public-map caching,
SSE-friendly proxying and IP allow-lists for `/intranet` and `/grafana`.
GitHub Actions runs backend, frontend, Docker and Mermaid checks; a successful
push to `main` can trigger the SSH deployment workflow.

The backup job writes MongoDB, Redis, Grafana and Uptime Kuma archives using
temporary files and rotation. The restore drill restores a Mongo archive into
a disposable MongoDB container with `--oplogReplay`, prints the collection list
and user count, and removes the scratch container. The operational procedure,
rights model and monitoring dashboard are maintained in
[`docs/10-deployment.md`](../../docs/10-deployment.md).
