#!/usr/bin/env bash
# Renders every ```mermaid block in *.md files with the official Mermaid CLI.
# Fails (exit 1) on the first syntax error and prints file + block index.
# Usage: scripts/check-diagrams.sh [--export DIR]   (--export also keeps the SVGs, used for the report)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXPORT=""
[[ "${1:-}" == "--export" ]] && EXPORT="${2:?--export needs a directory}"
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
MMDC_VERSION="11.4.2"

echo '{"args":["--no-sandbox"]}' > "$WORK/puppeteer.json"
if [[ -n "${CHROME_PATH:-}" ]]; then
  echo "{\"executablePath\":\"$CHROME_PATH\",\"args\":[\"--no-sandbox\"]}" > "$WORK/puppeteer.json"
fi

python3 - "$ROOT" "$WORK" <<'PY'
import pathlib, re, sys
root, work = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
skip = {"node_modules", ".git", ".claude", ".agents", "target", "dist"}
n = 0
for md in sorted(root.rglob("*.md")):
    if skip & set(md.relative_to(root).parts):
        continue
    for i, m in enumerate(re.finditer(r"```mermaid\n(.*?)```", md.read_text(), re.S)):
        name = str(md.relative_to(root)).replace("/", "__")[:-3] + f"__{i}.mmd"
        (work / name).write_text(m.group(1)); n += 1
print(f"{n} mermaid blocks found")
PY

fail=0
for f in "$WORK"/*.mmd; do
  [[ -e "$f" ]] || continue
  out="${f%.mmd}.svg"
  if ! log=$(npx -y "@mermaid-js/mermaid-cli@$MMDC_VERSION" -p "$WORK/puppeteer.json" -i "$f" -o "$out" 2>&1); then
    echo "::error::Mermaid render failed: $(basename "$f" .mmd)"
    echo "$log" | grep -m5 -iE "error|expect|parse" || echo "$log" | tail -5
    fail=1
  fi
done
if [[ -n "$EXPORT" ]]; then mkdir -p "$EXPORT"; cp "$WORK"/*.svg "$EXPORT"/ 2>/dev/null || true; fi
[[ $fail -eq 0 ]] && echo "All diagrams render." || exit 1
