#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
REPORT_DIR="$ROOT_DIR/report"
DIAGRAM_DIR="$REPORT_DIR/diagrams"
BUILD_DIR="$REPORT_DIR/.build"
PANDOC_IMAGE=${PANDOC_IMAGE:-pandoc/extra:3.6.3}
MERMAID_CHROME=${CHROME_PATH:-}

if [[ -z "$MERMAID_CHROME" ]]; then
    for candidate in google-chrome-stable google-chrome chromium chromium-browser; do
        if command -v "$candidate" >/dev/null 2>&1; then
            MERMAID_CHROME=$(command -v "$candidate")
            break
        fi
    done
fi

cd "$ROOT_DIR"
mkdir -p "$DIAGRAM_DIR" "$BUILD_DIR" "$REPORT_DIR/evidence"
rm -f "$DIAGRAM_DIR"/*.md "$DIAGRAM_DIR"/*.mmd
rm -f "$DIAGRAM_DIR"/*.svg "$DIAGRAM_DIR"/*.png

create_class_views() {
    python3 - "$ROOT_DIR/docs/04-uml-design.md" "$DIAGRAM_DIR" <<'PY'
from pathlib import Path
import re
import sys

source = Path(sys.argv[1]).read_text()
out_dir = Path(sys.argv[2])
blocks = re.findall(r"```mermaid\n(.*?)```", source, re.S)
class_block = next((block for block in blocks if re.search(r"^classDiagram\s*$", block, re.M)), None)
if class_block is None:
    raise SystemExit("docs/04-uml-design.md has no classDiagram source")

groups = {
    "class-domain-core": {
        "User", "FarmerProfile", "RegistrationRequest", "RegistrationStatus", "Role", "Delegation",
        "Orchard", "GeoPoint", "Plot", "OliveVariety", "HarvestStatus", "HarvestRequest", "OrchardSnapshot",
        "HarvestType", "HarvestRequestStatus", "StatusChange", "Correction",
    },
    "class-collection-resources": {
        "CollectionRound", "RoundStop", "RoundStatus", "StopStatus", "ResourceBooking", "BookingStatus",
        "Worker", "Equipment", "Tractor", "Bin", "Tool", "EquipmentType", "EquipmentStatus", "Unavailability",
        "ResourceKind", "SupplyOrder", "SupplyStatus", "ToolType", "Mill", "MillReception", "QualityAssessment",
        "QualityGrade",
    },
    "class-alerts-data": {
        "FieldAlert", "AlertType", "AlertSeverity", "AlertSource", "AlertStatus", "Notification", "NotificationType",
        "ImportJob", "ImportError", "ImportStatus", "ImportSource", "SystemSettings", "WeatherReading", "MaturityReading",
        "KpiSummaryDto", "YieldPoint", "LeadTimePoint", "FillRatePoint", "MillVolumePoint",
    },
}

def declared_name(line: str) -> str | None:
    match = re.match(r"\s*(?:class|interface|enum)\s+([A-Za-z0-9_]+)", line)
    return match.group(1) if match else None

def names(line: str) -> set[str]:
    return set(re.findall(r"\b[A-Z][A-Za-z0-9_]*\b", line))

for view, members in groups.items():
    lines = class_block.splitlines()
    selected: list[str] = ["classDiagram", "    direction LR", ""]
    inside = False
    for line in lines[2:]:
        declared = declared_name(line)
        if declared in members:
            selected.append(line)
            inside = "{" in line and "}" not in line
            continue
        if inside:
            selected.append(line)
            if "}" in line:
                inside = False
            continue
        if ("--" in line or ".." in line) and names(line) & members and names(line) <= members:
            selected.append(line)
    content = (
        "# Focused class view\n\n"
        "Derived at build time from `docs/04-uml-design.md`; the full source remains the appendix.\n\n"
        "```mermaid\n" + "\n".join(selected).rstrip() + "\n```\n"
    )
    (out_dir / f"{view}.md").write_text(content)
PY
}

run_diagrams() {
    if [[ -n "$MERMAID_CHROME" ]]; then
        CHROME_PATH="$MERMAID_CHROME" scripts/check-diagrams.sh --export "$DIAGRAM_DIR"
    else
        scripts/check-diagrams.sh --export "$DIAGRAM_DIR"
    fi
}

write_build_manifest() {
    python3 - "$REPORT_DIR/evidence/build-manifest.json" "$MERMAID_CHROME" <<'PY'
import json
from datetime import datetime, timezone
from pathlib import Path
import sys

target = Path(sys.argv[1])
target.write_text(json.dumps({
    "builtAt": datetime.now(timezone.utc).isoformat(),
    "mermaidChrome": sys.argv[2] or None,
    "evidencePolicy": "missing runtime evidence is reported as not collected",
}, indent=2) + "\n")
PY
}

mapfile -t INPUTS < <(find report/chapters report/appendices -type f -name '*.md' -print | sort)
if [[ ${#INPUTS[@]} -eq 0 ]]; then
    printf 'No report sources found under %s.\n' "$REPORT_DIR" >&2
    exit 2
fi

create_class_views
run_diagrams
write_build_manifest

PANDOC_ARGS=(
    --from=gfm
    --metadata-file=report/metadata.yaml
    --toc
    --number-sections
    --resource-path=.:report:report/diagrams
    "${INPUTS[@]}"
)

run_pandoc() {
    local output=$1
    shift
    if command -v pandoc >/dev/null 2>&1; then
        pandoc "$@" -o "$output"
        return
    fi
    if ! command -v docker >/dev/null 2>&1; then
        printf 'Pandoc is required. Install Pandoc or Docker, then rerun report/build.sh.\n' >&2
        exit 2
    fi
    docker run --rm --user "$(id -u):$(id -g)" \
        -v "$ROOT_DIR:/data" -w /data "$PANDOC_IMAGE" \
        pandoc "$@" -o "/data/${output#$ROOT_DIR/}"
}

rm -f "$REPORT_DIR/OliveConnect-report.pdf" "$REPORT_DIR/OliveConnect-report.docx"
run_pandoc "$REPORT_DIR/OliveConnect-report.docx" "${PANDOC_ARGS[@]}"
run_pandoc "$REPORT_DIR/OliveConnect-report.pdf" \
    "${PANDOC_ARGS[@]}" --pdf-engine="${PANDOC_PDF_ENGINE:-xelatex}"

printf 'Report built: %s and %s\n' "$REPORT_DIR/OliveConnect-report.pdf" "$REPORT_DIR/OliveConnect-report.docx"
