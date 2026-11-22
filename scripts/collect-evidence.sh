#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
RUN_ID=${EVIDENCE_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}
OUTPUT_DIR=${EVIDENCE_DIR:-$ROOT_DIR/report/evidence/$RUN_ID}
BASE_URL=${BASE_URL:-${PLAYWRIGHT_BASE_URL:-http://localhost}}
OPENAPI_URL=${OPENAPI_URL:-}
STRICT=false
ALL=false
declare -a REQUESTED=()

usage() {
    cat <<'EOF'
Usage: scripts/collect-evidence.sh [options]

Options:
  --all             request every collector
  --screenshots     capture public and configured Role pages in two viewports
  --coverage        run Maven verify and keep the JaCoCo report
  --queries         run KpiPerformanceEvidenceIT and keep its output
  --openapi         download /v3/api-docs from BASE_URL
  --k6              run load-tests/mixed-scenario.js
  --grafana         capture provisioned Grafana panels
  --restore         run the MongoDB restore drill
  --strict          fail if a requested collector cannot run
  --base-url URL    target API/web URL (default: http://localhost)
  --output DIR      evidence output directory

Role screenshot credentials use EVIDENCE_<ROLE>_EMAIL and
EVIDENCE_<ROLE>_PASSWORD. Runtime-only artifacts are never invented: an
unavailable optional collector is recorded as skipped unless --strict is used.
Set OPENAPI_URL when the API port is different from BASE_URL.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --all) ALL=true ;;
        --screenshots|--coverage|--queries|--openapi|--k6|--grafana|--restore)
            REQUESTED+=("${1#--}") ;;
        --strict) STRICT=true ;;
        --base-url) BASE_URL=${2:?--base-url needs a URL}; shift ;;
        --output) OUTPUT_DIR=${2:?--output needs a directory}; shift ;;
        -h|--help) usage; exit 0 ;;
        *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
    esac
    shift
done

if [[ -z "$OPENAPI_URL" ]]; then
    OPENAPI_URL="${BASE_URL%/}/v3/api-docs"
fi

if [[ "$OUTPUT_DIR" != /* ]]; then
    OUTPUT_DIR="$ROOT_DIR/$OUTPUT_DIR"
fi

if $ALL; then
    REQUESTED=(screenshots coverage queries openapi k6 grafana restore)
fi
if [[ ${#REQUESTED[@]} -eq 0 ]]; then
    usage >&2
    exit 2
fi

mkdir -p "$OUTPUT_DIR"
: >"$OUTPUT_DIR/manifest.tsv"

record() {
    printf '%s\t%s\t%s\n' "$1" "$2" "$3" >>"$OUTPUT_DIR/manifest.tsv"
}

run_collector() {
    local name=$1
    local command_text=$2
    shift 2
    local log_file="$OUTPUT_DIR/$name.log"
    if "$@" >"$log_file" 2>&1; then
        record "$name" collected "$command_text"
        return 0
    else
        local code=$?
    fi
    printf 'Evidence collector %s failed (exit %s); see %s\n' "$name" "$code" "$log_file" >&2
    if $STRICT; then
        record "$name" failed "$command_text"
        return "$code"
    fi
    record "$name" skipped "$command_text"
    return 0
}

skip_collector() {
    local name=$1
    local reason=$2
    printf '%s\n' "$reason" >"$OUTPUT_DIR/$name.log"
    record "$name" skipped "$reason"
    if $STRICT; then
        return 2
    fi
    return 0
}

collect_screenshots() {
    local screenshots="$OUTPUT_DIR/screenshots"
    (cd "$ROOT_DIR/frontend" && \
        EVIDENCE_SCREENSHOT_DIR="$screenshots" \
        PLAYWRIGHT_BASE_URL="$BASE_URL" \
        pnpm exec playwright test e2e/evidence-screenshots.spec.ts)
}

collect_coverage() {
    if ! (cd "$ROOT_DIR/backend" && ./mvnw -B verify); then
        return 1
    fi
    mkdir -p "$OUTPUT_DIR/jacoco"
    cp -R "$ROOT_DIR/backend/target/site/jacoco/." "$OUTPUT_DIR/jacoco/"
    mkdir -p "$OUTPUT_DIR/test-results"
    cp -R "$ROOT_DIR/backend/target/surefire-reports/." "$OUTPUT_DIR/test-results/"
    python3 - "$OUTPUT_DIR/test-scenarios.tsv" "$ROOT_DIR/backend/target/surefire-reports" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

target = Path(sys.argv[1])
reports = Path(sys.argv[2])
rows = []
for report in sorted(reports.glob('TEST-*.xml')):
    suite = ET.parse(report).getroot()
    attrs = suite.attrib
    rows.append((
        attrs.get('name', report.stem.removeprefix('TEST-')),
        attrs.get('tests', '0'),
        attrs.get('failures', '0'),
        attrs.get('errors', '0'),
        attrs.get('skipped', '0'),
        attrs.get('time', '0'),
    ))
with target.open('w', encoding='utf-8') as stream:
    stream.write('test-suite\ttests\tfailures\terrors\tskipped\tseconds\n')
    for row in rows:
        stream.write('\t'.join(row) + '\n')
PY
}

collect_queries() {
    (cd "$ROOT_DIR/backend" && ./mvnw -B -Dtest=KpiPerformanceEvidenceIT test)
}

collect_openapi() {
    curl --fail --silent --show-error "$OPENAPI_URL" -o "$OUTPUT_DIR/openapi.json"
}

collect_k6() {
    K6_SUMMARY_FILE="$OUTPUT_DIR/k6-summary.json" BASE_URL="$BASE_URL" \
        k6 run "$ROOT_DIR/load-tests/mixed-scenario.js"
}

collect_grafana() {
    "$ROOT_DIR/scripts/capture-grafana.sh" "$OUTPUT_DIR/grafana"
}

collect_restore() {
    BACKUP_DIR=${BACKUP_DIR:-$ROOT_DIR/report/evidence/backups} \
        "$ROOT_DIR/scripts/backup-restore-drill.sh" restore
}

for collector in "${REQUESTED[@]}"; do
    case "$collector" in
        screenshots)
            if command -v pnpm >/dev/null 2>&1; then
                run_collector screenshots 'pnpm exec playwright test e2e/evidence-screenshots.spec.ts' collect_screenshots
            else
                skip_collector screenshots 'pnpm is not installed'
            fi
            ;;
        coverage)
            if [[ -x "$ROOT_DIR/backend/mvnw" ]]; then
                run_collector coverage 'backend/./mvnw -B verify' collect_coverage
            else
                skip_collector coverage 'backend Maven wrapper is missing'
            fi
            ;;
        queries)
            if [[ -x "$ROOT_DIR/backend/mvnw" ]]; then
                run_collector queries 'backend/./mvnw -B -Dtest=KpiPerformanceEvidenceIT test' collect_queries
            else
                skip_collector queries 'backend Maven wrapper is missing'
            fi
            ;;
        openapi)
            if command -v curl >/dev/null 2>&1; then
                run_collector openapi "curl $OPENAPI_URL" collect_openapi
            else
                skip_collector openapi 'curl is not installed'
            fi
            ;;
        k6)
            if command -v k6 >/dev/null 2>&1; then
                run_collector k6 'k6 run load-tests/mixed-scenario.js' collect_k6
            else
                skip_collector k6 'k6 is not installed'
            fi
            ;;
        grafana)
            if [[ -z "${GRAFANA_PASSWORD:-}" ]]; then
                skip_collector grafana 'GRAFANA_PASSWORD is not set'
            elif command -v curl >/dev/null 2>&1; then
                run_collector grafana 'scripts/capture-grafana.sh' collect_grafana
            else
                skip_collector grafana 'curl is not installed'
            fi
            ;;
        restore)
            restore_backup_dir=${BACKUP_DIR:-$ROOT_DIR/report/evidence/backups}
            restore_env_file=${ENV_FILE:-$ROOT_DIR/deploy/.env}
            if ! command -v docker >/dev/null 2>&1; then
                skip_collector restore 'docker is not installed'
            elif [[ ! -r "$restore_env_file" ]]; then
                skip_collector restore "restore environment file is missing: $restore_env_file"
            elif ! compgen -G "$restore_backup_dir/mongo-*.archive.gz" >/dev/null; then
                skip_collector restore "no Mongo archive found in $restore_backup_dir"
            else
                run_collector restore 'scripts/backup-restore-drill.sh restore' collect_restore
            fi
            ;;
        *)
            printf 'Internal error: unknown collector %s\n' "$collector" >&2
            exit 2
            ;;
    esac
done

python3 - "$OUTPUT_DIR/manifest.json" "$RUN_ID" "$BASE_URL" "$OUTPUT_DIR/manifest.tsv" "$OUTPUT_DIR" <<'PY'
import csv
import json
from datetime import datetime, timezone
from pathlib import Path
import sys

target, run_id, base_url, rows_file, output_dir = sys.argv[1:]
rows = []
with Path(rows_file).open(newline='') as stream:
    for name, status, command in csv.reader(stream, delimiter='\t'):
        rows.append({'name': name, 'status': status, 'command': command})
Path(target).write_text(json.dumps({
    'runId': run_id,
    'collectedAt': datetime.now(timezone.utc).isoformat(),
    'baseUrl': base_url,
    'outputDirectory': output_dir,
    'artifacts': rows,
}, indent=2) + '\n')
PY

rm -f "$OUTPUT_DIR/manifest.tsv"
printf 'Evidence manifest: %s\n' "$OUTPUT_DIR/manifest.json"
