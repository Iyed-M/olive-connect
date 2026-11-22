#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
TEMP_ENV=$(mktemp)
trap 'rm -f "$TEMP_ENV"' EXIT

cp "$ROOT_DIR/deploy/.env.example" "$TEMP_ENV"

printf 'Checking production Compose interpolation...\n'
docker compose --env-file "$TEMP_ENV" -f "$ROOT_DIR/deploy/docker-compose.yml" config --quiet

printf 'Checking shell scripts...\n'
bash -n "$ROOT_DIR/scripts/backup-restore-drill.sh"
bash -n "$ROOT_DIR/deploy/backup/backup.sh"
bash -n "$ROOT_DIR/deploy/nginx/entrypoint.sh"

printf 'Checking monitoring dashboard JSON...\n'
node -e 'JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"))' \
    "$ROOT_DIR/deploy/monitoring/grafana/dashboards/oliveconnect-overview.json"

printf 'Checking deployment workflow and Compose-owned YAML with Python...\n'
python3 - "$ROOT_DIR/.github/workflows/ci.yml" "$ROOT_DIR/.github/workflows/deploy.yml" "$ROOT_DIR/deploy/docker-compose.yml" <<'PY'
import sys

try:
    import yaml
except ImportError:
    print("PyYAML is not installed; skipping structural YAML parse")
else:
    for path in sys.argv[1:]:
        with open(path, encoding="utf-8") as stream:
            yaml.safe_load(stream)
        print(f"valid YAML: {path}")
PY

printf 'Deployment validation passed.\n'
