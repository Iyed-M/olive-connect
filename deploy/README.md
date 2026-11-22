# OliveConnect production operations

The production stack is declared in `docker-compose.yml`. On a VPS, keep the
repository at `/opt/oliveconnect`, copy `.env.example` to `.env`, replace every
secret, and set `FORCE_HTTPS=true`, `TLS_DOMAIN` and `LETSENCRYPT_DIR`.

The first start creates a MongoDB keyfile in a named volume and initializes the
application, exporter and backup users. MongoDB and Redis are only attached to
the internal `back` network. The Nginx container exposes ports 80 and 443;
local runs create a temporary self-signed certificate, while a VPS run copies
the certificate from the mounted Let's Encrypt directory.

## Monitoring

Prometheus scrapes the API Actuator endpoint and the node, cAdvisor, MongoDB and
Redis exporters. Grafana provisions the Prometheus and Loki data sources and an
overview dashboard. Promtail ships API and Nginx rolling log files to Loki.
Uptime Kuma is reachable only through the Docker network; create monitors for
the public `/` endpoint and the internal API health endpoint after first login.

The Prometheus alert rules cover availability, API errors and latency, host
resources, database health and backup age. Configure the `operations` receiver
in `monitoring/alertmanager.yml` with the team's notification destination on
the VPS.

## Backups and restore drill

Before installing the cron entry, create the deploy-owned directories used by
the backup script and node-exporter textfile collector:

```sh
sudo install -d -o deploy -g deploy -m 700 /var/backups/oliveconnect
sudo install -d -o deploy -g deploy -m 755 /var/lib/node_exporter/textfile
```

Install `backup/oliveconnect-backup.cron` as `/etc/cron.d/oliveconnect-backup`.
It runs at 02:30, writes MongoDB, Redis, Grafana and Kuma archives under
`/var/backups/oliveconnect`, optionally copies them with `rclone` to
`OFFSITE_REMOTE`, and publishes `backup_last_success_timestamp` for Prometheus.

Run a restore drill without touching the production volumes:

```bash
scripts/backup-restore-drill.sh restore
```

The command restores the newest Mongo archive into a disposable MongoDB
container, replays the oplog, prints the restored collection list and user
count, and removes the container automatically. Pass a specific archive name
to repeat an older drill.

## Deployment

`.github/workflows/deploy.yml` runs only for `main` (or manual dispatch). It
connects with the `OLIVECONNECT_HOST`, `OLIVECONNECT_DEPLOY_USER`,
`OLIVECONNECT_SSH_KEY` and optional `OLIVECONNECT_SSH_PORT` secrets, pulls
`main`, backs up an existing stack, rebuilds the compose services, and checks
the API health endpoint.
