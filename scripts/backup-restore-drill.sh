#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
COMPOSE_FILE=${COMPOSE_FILE:-"$ROOT_DIR/deploy/docker-compose.yml"}
ENV_FILE=${ENV_FILE:-"$ROOT_DIR/deploy/.env"}
COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME:-oliveconnect}
BACKUP_DIR=${BACKUP_DIR:-/var/backups/oliveconnect}
METRICS_DIR=${METRICS_DIR:-/var/lib/node_exporter/textfile}
MONGO_IMAGE=${MONGO_IMAGE:-mongo:7.0.14}
SCRATCH_CONTAINER=${SCRATCH_CONTAINER:-oliveconnect-restore-drill}

usage() {
    cat <<'EOF'
Usage:
  scripts/backup-restore-drill.sh backup
  scripts/backup-restore-drill.sh restore [mongo-YYYYMMDD-HHMM.archive.gz]

Environment:
  BACKUP_DIR       Backup destination (default: /var/backups/oliveconnect)
  COMPOSE_FILE     Production compose file
  ENV_FILE         Compose secrets file (default: deploy/.env)
  OFFSITE_REMOTE   Optional encrypted rclone destination
  METRICS_DIR      node_exporter textfile collector directory
EOF
}

load_env() {
    if [[ ! -r "$ENV_FILE" ]]; then
        printf 'Missing %s; copy deploy/.env.example to deploy/.env first.\n' "$ENV_FILE" >&2
        exit 2
    fi

    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || {
        printf 'Required command not found: %s\n' "$1" >&2
        exit 2
    }
}

prune_oldest() {
    local pattern=$1
    local keep=$2
    local count=0
    local file

    while IFS= read -r file; do
        count=$((count + 1))
        if (( count > keep )); then
            rm -f -- "$file"
        fi
    done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name "$pattern" -printf '%T@ %p\n' | sort -rn | sed 's/^[^ ]* //')
}

write_backup_metric() {
    local metric_file="$METRICS_DIR/backup.prom"
    local tmp_file

    if ! mkdir -p "$METRICS_DIR" 2>/dev/null; then
        printf 'Warning: cannot write backup metric directory %s\n' "$METRICS_DIR" >&2
        return 0
    fi
    tmp_file=$(mktemp "$METRICS_DIR/.backup.prom.XXXXXX")
    printf '# HELP backup_last_success_timestamp Unix timestamp of the last successful OliveConnect backup.\n' > "$tmp_file"
    printf '# TYPE backup_last_success_timestamp gauge\n' >> "$tmp_file"
    printf 'backup_last_success_timestamp %s\n' "$(date +%s)" >> "$tmp_file"
    mv -f -- "$tmp_file" "$metric_file"
}

backup_file() {
    local final_file=$1
    shift
    local tmp_file
    tmp_file=$(mktemp "${final_file}.tmp.XXXXXX")
    if ! "$@" > "$tmp_file"; then
        rm -f -- "$tmp_file"
        return 1
    fi
    mv -f -- "$tmp_file" "$final_file"
}

backup_volume() {
    local service=$1
    local source_dir=$2
    local final_file=$3
    backup_file "$final_file" "${compose[@]}" exec -T "$service" tar -czf - -C "$source_dir" .
}

run_backup() {
    local timestamp
    local mongo_file
    local redis_file
    local grafana_file
    local kuma_file
    local weekly_file
    local monthly_file

    require_command docker
    load_env
    mkdir -p "$BACKUP_DIR"
    chmod 700 "$BACKUP_DIR"

    local lock_file="$BACKUP_DIR/.backup.lock"
    exec 9>"$lock_file"
    if command -v flock >/dev/null 2>&1; then
        flock -n 9 || {
            printf 'Another OliveConnect backup is already running.\n' >&2
            exit 1
        }
    fi

    local -a compose=(docker compose --project-name "$COMPOSE_PROJECT_NAME" --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
    timestamp=$(date +%Y%m%d-%H%M)
    mongo_file="$BACKUP_DIR/mongo-$timestamp.archive.gz"
    redis_file="$BACKUP_DIR/redis-$timestamp.rdb"
    grafana_file="$BACKUP_DIR/grafana-$timestamp.tar.gz"
    kuma_file="$BACKUP_DIR/kuma-$timestamp.tar.gz"

    printf 'Backing up MongoDB...\n'
    backup_file "$mongo_file" "${compose[@]}" exec -T mongo mongodump \
        --username "${MONGO_BACKUP_USER:-backup}" \
        --password "${MONGO_BACKUP_PASSWORD:?MONGO_BACKUP_PASSWORD is required}" \
        --authenticationDatabase admin --archive --gzip --oplog

    printf 'Backing up Redis...\n'
    backup_file "$redis_file" "${compose[@]}" exec -T redis redis-cli \
        --no-auth-warning -a "${REDIS_PASSWORD:?REDIS_PASSWORD is required}" --rdb -

    printf 'Backing up Grafana and Uptime Kuma state...\n'
    backup_volume grafana /var/lib/grafana "$grafana_file"
    backup_volume uptime-kuma /app/data "$kuma_file"

    # Keep seven daily snapshots. A weekly and a monthly copy is made from the
    # same successful run so rotation remains deterministic and re-runnable.
    prune_oldest 'mongo-*.archive.gz' 7
    prune_oldest 'redis-*.rdb' 3
    prune_oldest 'grafana-*.tar.gz' 4
    prune_oldest 'kuma-*.tar.gz' 4

    if [[ "$(date +%u)" == 7 ]]; then
        weekly_file="$BACKUP_DIR/weekly-$timestamp.tar.gz"
        tar -czf "$weekly_file" -C "$BACKUP_DIR" \
            "$(basename "$mongo_file")" "$(basename "$redis_file")" \
            "$(basename "$grafana_file")" "$(basename "$kuma_file")"
        prune_oldest 'weekly-*.tar.gz' 4
    fi
    if [[ "$(date +%d)" == 01 ]]; then
        monthly_file="$BACKUP_DIR/monthly-$timestamp.tar.gz"
        tar -czf "$monthly_file" -C "$BACKUP_DIR" \
            "$(basename "$mongo_file")" "$(basename "$redis_file")" \
            "$(basename "$grafana_file")" "$(basename "$kuma_file")"
        prune_oldest 'monthly-*.tar.gz' 3
    fi

    if [[ -n "${OFFSITE_REMOTE:-}" ]]; then
        require_command rclone
        printf 'Copying backups to %s...\n' "$OFFSITE_REMOTE"
        rclone copy "$BACKUP_DIR" "$OFFSITE_REMOTE" --include '*.gz' --include '*.rdb' --include 'backup.prom'
    else
        printf 'Warning: OFFSITE_REMOTE is empty; backups remain on the VPS only.\n' >&2
    fi

    write_backup_metric
    printf 'Backup completed: %s\n' "$timestamp"
}

latest_mongo_archive() {
    find "$BACKUP_DIR" -maxdepth 1 -type f -name 'mongo-*.archive.gz' -printf '%T@ %p\n' \
        | sort -rn | sed -n '1s/^[^ ]* //p'
}

run_restore() {
    local archive=${1:-}
    local archive_dir
    local archive_name
    local existing_container

    require_command docker
    load_env
    if [[ -z "$archive" ]]; then
        archive=$(latest_mongo_archive)
    elif [[ "$archive" != */* ]]; then
        archive="$BACKUP_DIR/$archive"
    fi
    if [[ ! -r "$archive" ]]; then
        printf 'Mongo archive not found: %s\n' "$archive" >&2
        exit 2
    fi

    archive_dir=$(cd "$(dirname "$archive")" && pwd)
    archive_name=$(basename "$archive")
    existing_container=$(docker ps -aq --filter "name=^${SCRATCH_CONTAINER}$")
    if [[ -n "$existing_container" ]]; then
        docker rm -f "$SCRATCH_CONTAINER" >/dev/null
    fi

    printf 'Restoring %s into scratch container %s...\n' "$archive_name" "$SCRATCH_CONTAINER"
    docker run --rm --name "$SCRATCH_CONTAINER" \
        -e ARCHIVE_NAME="$archive_name" \
        -v "$archive_dir:/restore:ro" \
        "$MONGO_IMAGE" sh -ec '
            set -eu
            mkdir -p /data/db
            mongod --dbpath /data/db --bind_ip 127.0.0.1 --port 27017 --fork --logpath /tmp/mongod.log
            cleanup() { mongod --dbpath /data/db --shutdown >/dev/null 2>&1 || true; }
            trap cleanup EXIT
            until mongosh --quiet --host 127.0.0.1 --eval "db.adminCommand({ping: 1})" >/dev/null 2>&1; do
                sleep 1
            done
            mongorestore --host 127.0.0.1 --archive="/restore/$ARCHIVE_NAME" --gzip --drop --oplogReplay
            mongosh --quiet --host 127.0.0.1 --eval "const restored = db.getSiblingDB('oliveconnect'); printjson({collections: restored.getCollectionNames(), users: restored.getCollection('users').countDocuments()});"
        '
    printf 'Restore drill completed successfully.\n'
}

main() {
    local mode=${1:-}
    shift || true
    case "$mode" in
        backup)
            run_backup "$@"
            ;;
        restore)
            run_restore "$@"
            ;;
        -h|--help|"")
            usage
            [[ -n "$mode" ]] || exit 2
            ;;
        *)
            usage >&2
            exit 2
            ;;
    esac
}

main "$@"
