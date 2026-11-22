#!/usr/bin/env bash
set -Eeuo pipefail

OUTPUT_DIR=${1:?Usage: scripts/capture-grafana.sh OUTPUT_DIR}
GRAFANA_URL=${GRAFANA_URL:-http://localhost/grafana}
GRAFANA_USER=${GRAFANA_USER:-admin}
GRAFANA_PASSWORD=${GRAFANA_PASSWORD:-}
PANEL_IDS=${GRAFANA_PANEL_IDS:-1,2,3,4,5,6,7,8,9}

if [[ -z "$GRAFANA_PASSWORD" ]]; then
    printf 'GRAFANA_PASSWORD is required to capture Grafana panels.\n' >&2
    exit 2
fi

mkdir -p "$OUTPUT_DIR"
IFS=',' read -r -a panels <<< "$PANEL_IDS"
for panel in "${panels[@]}"; do
    curl --fail --silent --show-error \
        --user "$GRAFANA_USER:$GRAFANA_PASSWORD" \
        "$GRAFANA_URL/render/d-solo/oliveconnect-overview/oliveconnect-overview?panelId=$panel&from=now-6h&to=now&width=1600&height=900" \
        -o "$OUTPUT_DIR/grafana-panel-$panel.png"
done

printf 'Grafana panels captured in %s\n' "$OUTPUT_DIR"
