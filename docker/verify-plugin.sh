#!/usr/bin/env bash
# Verify the plugin bundle is running (no Kintsugi API required).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"

require_cmd curl
require_killbill

echo "==> pluginsInfo"
PLUGINS_FILE="$(mktemp)"
trap 'rm -f "${PLUGINS_FILE}" /tmp/kb-kintsugi-health.json' EXIT
curl -sf "${kb_auth[@]}" "${KILLBILL_URL}/1.0/kb/pluginsInfo" > "${PLUGINS_FILE}"
python3 - "${PLUGIN_NAME}" "${PLUGINS_FILE}" <<'PY'
import json, sys
plugin_name, path = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as fh:
    plugins = json.load(fh)
match = [p for p in plugins if p.get("pluginName") == plugin_name]
if not match:
    print(f"FAIL: {plugin_name} not in pluginsInfo", file=sys.stderr)
    sys.exit(1)
p = match[0]
print("pluginState:", p.get("pluginState"))
print("version:", p.get("version"))
print("services:", ", ".join(p.get("services") or []))
if p.get("pluginState") != "RUNNING":
    sys.exit("FAIL: plugin not RUNNING")
if "InvoicePluginApi" not in (p.get("services") or []):
    sys.exit("FAIL: InvoicePluginApi missing")
print("OK")
PY

echo
echo "==> healthcheck (tenant-scoped; run setup-tenant.sh first for healthy status)"
CODE="$(curl -s -o /tmp/kb-kintsugi-health.json -w "%{http_code}" \
  "${kb_auth[@]}" "${kb_tenant[@]}" \
  "${KILLBILL_URL}/plugins/${PLUGIN_NAME}/healthcheck")"
echo "HTTP ${CODE}"
cat /tmp/kb-kintsugi-health.json | python3 -m json.tool 2>/dev/null || cat /tmp/kb-kintsugi-health.json
echo

if [[ "${CODE}" == "200" ]]; then
  echo "OK: plugin verified"
else
  echo "FAIL: healthcheck returned ${CODE} — run ./docker/setup-tenant.sh" >&2
  exit 1
fi
