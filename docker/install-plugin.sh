#!/usr/bin/env bash
# Build the plugin JAR and install it into the local Kill Bill docker container.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"

require_cmd docker curl mvn

if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="${JAVA_HOME}/bin:${PATH}"
elif [[ -d /opt/homebrew/opt/openjdk@11 ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@11"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

CONTAINER="$(killbill_container_id)"
if [[ -z "${CONTAINER}" ]]; then
  echo "Start the stack first: docker compose -f docker/docker-compose.yml up -d" >&2
  exit 1
fi

JAR_NAME="$(plugin_jar_name)"
PLUGIN_PATH="$(plugin_install_path)"
VERSION_DIR="$(plugin_container_path)"

echo "==> mvn package in ${REPO_ROOT}"
(cd "${REPO_ROOT}" && mvn -q package -DskipTests)

echo "==> Install ${JAR_NAME} → ${PLUGIN_PATH}"
docker exec "${CONTAINER}" mkdir -p "${VERSION_DIR}"
docker cp "${REPO_ROOT}/target/${JAR_NAME}" "${CONTAINER}:${PLUGIN_PATH}"

echo "==> Set default plugin version (SET_DEFAULT → ${VERSION_DIR##*/})"
docker exec "${CONTAINER}" ln -sfn "${VERSION_DIR##*/}" \
  "/var/lib/killbill/bundles/plugins/java/${PLUGIN_NAME}/SET_DEFAULT"

echo "==> Restart Kill Bill"
docker compose -f "${COMPOSE_FILE}" restart killbill
wait_for_killbill

echo "==> Verify plugin in pluginsInfo"
PLUGINS_FILE="$(mktemp)"
trap 'rm -f "${PLUGINS_FILE}"' EXIT
curl -sf "${kb_auth[@]}" "${KILLBILL_URL}/1.0/kb/pluginsInfo" > "${PLUGINS_FILE}"
python3 - "${PLUGIN_NAME}" "${PLUGINS_FILE}" <<'PY'
import json, sys
plugin_name, path = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as fh:
    plugins = json.load(fh)
match = [p for p in plugins if p.get("pluginName") == plugin_name]
if not match:
    print(f"FAIL: plugin {plugin_name} not found in pluginsInfo", file=sys.stderr)
    sys.exit(1)
p = match[0]
state = p.get("pluginState")
services = p.get("services") or []
print(f"pluginName={p.get('pluginName')} state={state}")
print("services:", ", ".join(services) or "(none)")
if state != "RUNNING":
    print("WARN: plugin is not RUNNING — check Kill Bill logs", file=sys.stderr)
if "InvoicePluginApi" not in services:
    print("FAIL: InvoicePluginApi not registered", file=sys.stderr)
    sys.exit(1)
print("OK: plugin installed")
PY

echo
echo "Next: ./docker/setup-tenant.sh   # configure tenant + plugin"
echo "      ./docker/verify-plugin.sh # healthcheck (no Kintsugi API needed)"
echo "      ./docker/smoke-invoice.sh # full invoice tax test (needs Kintsugi)"
