# 10 — Deployment, Backups & Monitoring (subject §V)

Target: a Linux VPS (Ubuntu Server 24.04 LTS, 2 vCPU / 4 GB RAM / 40 GB SSD is enough) running **Docker Compose**, reachable at `https://<domain>` (or `http://<IP>` for the demo, as the subject allows).

## 1. Deployment diagram

```mermaid
flowchart TB
    subgraph Internet
        BR[Browsers<br/>phones / desktops]
        DEV[Developers<br/>SSH key only]
    end
    subgraph VPS["VPS — Ubuntu 24.04 (ufw: 22, 80, 443 open)"]
        subgraph DockerNet["Docker network 'front'"]
            NGINX["nginx:1.27-alpine<br/>:80 → 301 https, :443 TLS<br/>/ → SPA static<br/>/api → api:8080<br/>/grafana → grafana:3000 (admin IPs)<br/>/status → uptime-kuma"]
        end
        subgraph BackNet["Docker network 'back' (internal: true)"]
            API["oliveconnect-api<br/>eclipse-temurin:21-jre<br/>:8080, actuator :8081"]
            MONGO[("mongo:7.0<br/>replica set rs0<br/>volume mongo-data")]
            REDIS[("redis:7-alpine<br/>requirepass, RDB<br/>volume redis-data")]
            SIM["data-simulator<br/>(cron-like loop)"]
        end
        subgraph MonNet["Docker network 'monitoring'"]
            PROM[prometheus]
            GRAF[grafana]
            LOKI[loki]
            PT[promtail]
            NE[node-exporter]
            CA[cadvisor]
            ME[mongodb-exporter]
            RE[redis-exporter]
            KUMA[uptime-kuma]
        end
        VOL1[/"volume inbox<br/>/data/inbox"/]
        BK["backup.sh (host cron 02:30)<br/>/var/backups/oliveconnect"]
    end
    OFF[("Off-site storage<br/>rclone → Google Drive / S3")]

    BR -- HTTPS 443 --> NGINX
    DEV -- SSH 22 --> VPS
    NGINX --> API
    NGINX --> GRAF
    NGINX --> KUMA
    API --> MONGO
    API --> REDIS
    API --> VOL1
    SIM --> VOL1
    PROM --> API
    PROM --> NE
    PROM --> CA
    PROM --> ME
    PROM --> RE
    PT --> LOKI
    GRAF --> PROM
    GRAF --> LOKI
    KUMA --> NGINX
    BK --> MONGO
    BK --> OFF
```

## 2. Build & release pipeline

```mermaid
flowchart LR
    A[git push main] --> B[CI: tests]
    B --> C["Build images<br/>api: multi-stage Maven → JRE<br/>web: node build → nginx"]
    C --> D[Push to GHCR<br/>tag = git sha + latest]
    D --> E["deploy.sh on VPS (ssh):<br/>docker compose pull<br/>docker compose up -d<br/>healthcheck wait"]
    E --> F{/actuator/health UP?}
    F -- no --> G[Rollback: previous tag]
    F -- yes --> H[Done]
```

**Backend Dockerfile (multi-stage)**
```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /src/target/oliveconnect-*.jar app.jar
EXPOSE 8080 8081
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]
```

**Frontend Dockerfile**: `node:20-alpine` runs `npm ci && npm run build`, and the `dist/` output is copied into `nginx:1.27-alpine` together with `nginx.conf`.

## 3. `docker-compose.yml` (outline)

```yaml
name: oliveconnect
services:
  nginx:
    image: ghcr.io/<org>/oliveconnect-web:${TAG:-latest}
    ports: ["80:80", "443:443"]
    volumes:
      - ./nginx/conf.d:/etc/nginx/conf.d:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro
      - nginx-logs:/var/log/nginx
    depends_on: { api: { condition: service_healthy } }
    networks: [front, back, monitoring]
    restart: unless-stopped

  api:
    image: ghcr.io/<org>/oliveconnect-api:${TAG:-latest}
    env_file: .env                      # JWT_SECRET, MONGO_URI, REDIS_PASSWORD … (never committed)
    environment:
      SPRING_PROFILES_ACTIVE: prod
    volumes: [inbox:/data/inbox, api-logs:/app/logs]
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8081/actuator/health"]
      interval: 15s
      retries: 10
    depends_on: [mongo, redis]
    networks: [back, monitoring]
    deploy: { resources: { limits: { memory: 1g } } }
    restart: unless-stopped

  mongo:
    image: mongo:7.0
    command: ["--replSet", "rs0", "--bind_ip_all", "--keyFile", "/etc/mongo-keyfile", "--wiredTigerCacheSizeGB", "1"]
    environment: { MONGO_INITDB_ROOT_USERNAME: root, MONGO_INITDB_ROOT_PASSWORD_FILE: /run/secrets/mongo_root }
    volumes: [mongo-data:/data/db, ./mongo/init:/docker-entrypoint-initdb.d:ro, ./mongo/keyfile:/etc/mongo-keyfile:ro]
    healthcheck: { test: ["CMD", "mongosh", "--quiet", "--eval", "rs.status().ok"], interval: 10s }
    networks: [back]
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    command: ["redis-server", "--requirepass", "${REDIS_PASSWORD}", "--maxmemory", "256mb",
              "--maxmemory-policy", "allkeys-lru", "--save", "900", "1"]
    volumes: [redis-data:/data]
    networks: [back]
    restart: unless-stopped

  simulator:
    image: ghcr.io/<org>/oliveconnect-simulator:${TAG:-latest}
    volumes: [inbox:/data/inbox]
    networks: [back]

  prometheus: { image: prom/prometheus, volumes: [./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro, prom-data:/prometheus], networks: [monitoring] }
  grafana:    { image: grafana/grafana, volumes: [grafana-data:/var/lib/grafana, ./monitoring/grafana/provisioning:/etc/grafana/provisioning:ro], networks: [monitoring] }
  loki:       { image: grafana/loki, networks: [monitoring] }
  promtail:   { image: grafana/promtail, volumes: [api-logs:/logs/api:ro, nginx-logs:/logs/nginx:ro, ./monitoring/promtail.yml:/etc/promtail/config.yml:ro], networks: [monitoring] }
  node-exporter: { image: prom/node-exporter, pid: host, volumes: ["/:/host:ro,rslave"], command: ["--path.rootfs=/host"], networks: [monitoring] }
  cadvisor:   { image: gcr.io/cadvisor/cadvisor, volumes: ["/:/rootfs:ro", "/var/run:/var/run:ro", "/sys:/sys:ro", "/var/lib/docker/:/var/lib/docker:ro"], networks: [monitoring] }
  mongodb-exporter: { image: percona/mongodb_exporter:0.40, networks: [back, monitoring] }
  redis-exporter:   { image: oliver006/redis_exporter, networks: [back, monitoring] }
  uptime-kuma: { image: louislam/uptime-kuma:1, volumes: [kuma-data:/app/data], networks: [front] }

networks:
  front: {}
  back: { internal: true }
  monitoring: {}
volumes: { mongo-data: {}, redis-data: {}, inbox: {}, api-logs: {}, nginx-logs: {}, prom-data: {}, grafana-data: {}, kuma-data: {} }
```

The image versions above are placeholders. Pin exact versions (for example `mongo:7.0.14`) in the real file.

`mongo/init/01-init.js` initiates the replica set (`rs.initiate({_id:"rs0", members:[{_id:0, host:"mongo:27017"}]})`) and creates the application user with the `readWrite` role on `oliveconnect` only, plus a read-only `exporter` user.

## 4. Nginx configuration (key parts)

```nginx
# /etc/nginx/conf.d/oliveconnect.conf
limit_req_zone $binary_remote_addr zone=api:10m rate=20r/s;
limit_req_zone $binary_remote_addr zone=login:10m rate=5r/m;
proxy_cache_path /var/cache/nginx/map levels=1:2 keys_zone=mapcache:10m max_size=200m inactive=10m;

server { listen 80; server_name oliveconnect.example.tn; return 301 https://$host$request_uri; }

server {
  listen 443 ssl http2;
  server_name oliveconnect.example.tn;
  ssl_certificate     /etc/letsencrypt/live/oliveconnect.example.tn/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/oliveconnect.example.tn/privkey.pem;
  add_header Strict-Transport-Security "max-age=31536000" always;
  add_header X-Content-Type-Options nosniff always;
  add_header Referrer-Policy strict-origin-when-cross-origin always;

  gzip on; gzip_types application/json application/javascript text/css image/svg+xml;
  client_max_body_size 6m;

  root /usr/share/nginx/html;
  location /assets/ { expires 1y; add_header Cache-Control "public, immutable"; }
  location / { try_files $uri /index.html; add_header Cache-Control "no-cache"; }

  location /api/ {
    limit_req zone=api burst=40 nodelay;
    proxy_pass http://api:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
  }
  location = /api/auth/login { limit_req zone=login burst=5; proxy_pass http://api:8080; }
  location /api/events {                      # SSE: no buffering, long timeout
    proxy_pass http://api:8080;
    proxy_buffering off; proxy_cache off;
    proxy_read_timeout 1h;
    proxy_set_header Connection "";
    proxy_http_version 1.1;
  }
  location /api/map/public {                  # micro-cache public map (anonymous)
    proxy_cache mapcache; proxy_cache_valid 200 30s;
    proxy_cache_key $request_uri;
    add_header X-Cache-Status $upstream_cache_status;
    proxy_pass http://api:8080;
  }
  location /intranet/ { allow 10.0.0.0/8; allow <coop-public-ip>; deny all; try_files $uri /index.html; }
  location /grafana/  { allow <admin-ips>; deny all; proxy_pass http://grafana:3000/; }
  location /actuator/ { deny all; }
}
```

The **cache layers** asked for in §V are, from the outside in:
1. Browser cache for hashed static assets (1 year).
2. Nginx micro-cache for the anonymous map (30 s).
3. Redis application cache (KPIs, map tiles, reference data).
4. MongoDB WiredTiger cache.

## 5. Step-by-step deployment procedure (goes into the report)

1. **Provision the VPS.** Create a non-root `deploy` user with SSH key login, set `PasswordAuthentication no`, and run `apt upgrade`. Enable `ufw allow 22,80,443` and `unattended-upgrades`, and install `fail2ban`.
2. **Install Docker** Engine and the Compose plugin, and add `deploy` to the `docker` group.
3. **DNS**: create an A record `oliveconnect.<domain>` pointing to the VPS IP. With no domain, use `http://<IP>` and skip TLS.
4. **TLS**: `certbot certonly --standalone -d oliveconnect.<domain>`, with renewal by cron (`certbot renew --deploy-hook "docker compose exec nginx nginx -s reload"`).
5. **Copy `deploy/`** to `/opt/oliveconnect`. Create `.env` from `.env.example` with strong secrets (`openssl rand -base64 48`). Generate the Mongo keyfile (`openssl rand -base64 756 > mongo/keyfile && chmod 400`).
6. `docker compose pull && docker compose up -d`, then check `docker compose ps` (all healthy) and `curl -k https://localhost/api/actuator/health` (from inside the network).
7. **Bootstrap**: the first start creates the `admin` account from `ADMIN_EMAIL`/`ADMIN_INITIAL_PASSWORD` in the env. The initial password must be changed at first login.
8. **Load reference data**: drop the simulator reference files into the inbox (or `POST /api/imports/run?type=REFERENCE`), then check the import journal.
9. **Monitoring**: open Grafana and import the provisioned dashboards (JVM Micrometer 4701, Node Exporter 1860, MongoDB, Redis, Nginx logs). Add the HTTP monitors in Uptime Kuma.
10. **Smoke test**: log in with each role and run the E2E smoke suite against the public URL.

## 6. User & access-rights management (operations doc)

| Task | How |
|---|---|
| New farmer | Visitor submits `/register`, admin approves in **Admin → Registrations**, the account gets role `FARMER` and a temporary password (displayed once or sent by email/SMS in a later version) |
| New staff member | **Admin → Users → New**: email, name, one or more roles; for `MILL_MANAGER` choose the mill |
| Change rights | **Admin → Users → Roles**. Takes effect at the next token refresh (≤ 15 min). For an immediate effect, use **Revoke sessions**, which deletes the Redis refresh tokens |
| Disable account | **Admin → Users → Disable**: blocks login and revokes all sessions; data is kept (history integrity) |
| Forgotten password | Admin resets it (temporary password, change forced at next login) |
| Server access | Only the `deploy` user via SSH key; MongoDB and Redis are not exposed; Grafana is behind an IP allow-list plus its own login |
| Secrets rotation | Change `JWT_SECRET` in `.env` and run `docker compose up -d api`. All users must log in again |

## 7. Backups & restore

| What | How | When | Retention |
|---|---|---|---|
| MongoDB (incl. GridFS photos) | `docker compose exec -T mongo mongodump --archive --gzip --oplog` → `/var/backups/oliveconnect/mongo-YYYYMMDD-HHmm.archive.gz` | daily 02:30 (host cron) + before each deployment | 7 daily, 4 weekly, 3 monthly |
| Redis | copy `dump.rdb` (tokens only, non-critical) | daily | 3 days |
| Config | `deploy/` is in Git; `.env` is encrypted with `age` and kept off-site | on change | — |
| Grafana / Kuma data | volume tarball | weekly | 4 |
| Off-site | `rclone copy` to cloud storage (encrypted remote) | after each backup | same as above |

```bash
#!/usr/bin/env bash
# deploy/backup/backup.sh — called by cron: 30 2 * * * /opt/oliveconnect/backup/backup.sh
set -euo pipefail
TS=$(date +%Y%m%d-%H%M); DIR=/var/backups/oliveconnect; mkdir -p "$DIR"
cd /opt/oliveconnect
docker compose exec -T mongo mongodump --username backup --password "$MONGO_BACKUP_PWD" \
  --authenticationDatabase admin --archive --gzip --oplog > "$DIR/mongo-$TS.archive.gz"
docker compose exec -T redis redis-cli -a "$REDIS_PASSWORD" --rdb - > "$DIR/redis-$TS.rdb" 2>/dev/null
find "$DIR" -name 'mongo-*' -mtime +7 ! -name '*-01-*' -delete   # simple rotation (refine for weekly/monthly)
rclone copy "$DIR" offsite:oliveconnect-backups --max-age 25h
echo "backup_last_success_timestamp $(date +%s)" > /var/lib/node_exporter/textfile/backup.prom
```

The last line exposes a metric, so Grafana can **alert when no backup has succeeded in the last 26 h**.

**Restore drill** (done once and documented with screenshots): `mongorestore --archive=mongo-….archive.gz --gzip --drop --oplogReplay` into a scratch container, then check document counts and log in to the restored instance. Target RPO is 24 h and target RTO is under 1 h.

## 8. Monitoring

| Need (§V) | Tool | Metrics / view | Alert rule (Grafana / Alertmanager) |
|---|---|---|---|
| Availability rate | Uptime Kuma (HTTP probe `/`, `/api/actuator/health` via internal route, 60 s) + Prometheus `up` | uptime % over 24 h / 30 d, status page `/status` | down > 2 min → email/Telegram |
| Resource consumption | node-exporter, cAdvisor | CPU, RAM, disk, network per host and per container | disk > 80 %, RAM > 90 % for 5 min |
| Application | Spring Actuator + Micrometer (`/actuator/prometheus`) | HTTP p95 per endpoint, error rate, JVM heap/GC, Mongo driver pool, cache hit ratio (`cache_gets_total{result="hit"}`), custom metrics: `oliveconnect_import_rejected_total`, `oliveconnect_alerts_created_total`, `oliveconnect_rounds_in_progress` | p95 > 2 s, 5xx rate > 1 % |
| Database | mongodb-exporter, redis-exporter | opcounters, connections, replication lag, slow queries; Redis memory, hit rate, evictions | connections > 80 % |
| Logs | Log4j2 JSON → file → Promtail → Loki; Nginx access/error logs | search by `traceId`, user, level; 4xx/5xx panels | > 20 ERROR/min |
| Backups | textfile metric | time since last backup | > 26 h |

**Log management**: Log4j2 rolling file appender (daily, 100 MB, 14 files kept). Each log line has a JSON layout with `traceId`, `userId` and `endpoint` (set through MDC by a filter). Docker logs use the `json-file` driver with `max-size: 10m, max-file: 3`.

```mermaid
flowchart LR
    API[Spring Boot<br/>Micrometer + Log4j2 JSON] -- /actuator/prometheus --> PROM[Prometheus]
    NE[node-exporter] --> PROM
    CA[cAdvisor] --> PROM
    ME[mongodb-exporter] --> PROM
    RE[redis-exporter] --> PROM
    API -- log files --> PT[Promtail] --> LOKI[Loki]
    NG[Nginx logs] --> PT
    PROM --> GRAF[Grafana dashboards + alerts]
    LOKI --> GRAF
    KUMA[Uptime Kuma] -- probes --> NG2[Public URL]
    GRAF -- alert --> MAIL[Email / Telegram]
    KUMA -- alert --> MAIL
```
