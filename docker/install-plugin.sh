#!/usr/bin/env bash
# Build the plugin and copy the JAR into the local docker compose Kill Bill container.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
VERSION="0.1.0"
JAR_NAME="kintsugi-plugin-${VERSION}.jar"
PLUGIN_PATH="/var/lib/killbill/bundles/plugins/java/killbill-kintsugi/${VERSION}/${JAR_NAME}"

if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="${JAVA_HOME}/bin:${PATH}"
elif [[ -d /opt/homebrew/opt/openjdk@11 ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@11"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

echo "==> mvn package in ${REPO_ROOT}"
(cd "${REPO_ROOT}" && mvn -q package -DskipTests)

CONTAINER="$(docker compose -f "${SCRIPT_DIR}/docker-compose.yml" ps -q killbill)"
if [[ -z "${CONTAINER}" ]]; then
  echo "Start the stack first: docker compose -f docker/docker-compose.yml up -d" >&2
  exit 1
fi

echo "==> docker cp → ${CONTAINER}:${PLUGIN_PATH}"
docker exec "${CONTAINER}" mkdir -p "$(dirname "${PLUGIN_PATH}")"
docker cp "${REPO_ROOT}/target/${JAR_NAME}" "${CONTAINER}:${PLUGIN_PATH}"

echo "==> restart Kill Bill"
docker compose -f "${SCRIPT_DIR}/docker-compose.yml" restart killbill

cat <<EOF

Done. After Kill Bill restarts:
1. Enable org.killbill.invoice.plugin=killbill-kintsugi on your tenant
2. Upload plugin config (kintsugiUrl + hmacSecret)
3. Check GET http://localhost:8080/plugins/killbill-kintsugi/healthcheck

EOF
