#!/usr/bin/env bash
# Shared helpers for Kill Bill + Kintsugi plugin docker scripts.
set -euo pipefail

DOCKER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${DOCKER_DIR}/.." && pwd)"
COMPOSE_FILE="${DOCKER_DIR}/docker-compose.yml"
ENV_FILE="${KILLBILL_ENV_FILE:-${DOCKER_DIR}/.env}"

# OSGi plugin name (must match KintsugiActivator.PLUGIN_NAME).
export PLUGIN_NAME="${PLUGIN_NAME:-killbill-kintsugi}"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  set -a && source "${ENV_FILE}" && set +a
fi

export KILLBILL_URL="${KILLBILL_URL:-http://127.0.0.1:8080}"
export KILLBILL_API_KEY="${KILLBILL_API_KEY:-bob}"
export KILLBILL_API_SECRET="${KILLBILL_API_SECRET:-lazar}"
export KILLBILL_ADMIN_USER="${KILLBILL_ADMIN_USER:-admin}"
export KILLBILL_ADMIN_PASSWORD="${KILLBILL_ADMIN_PASSWORD:-password}"
export KINTSUGI_URL="${KINTSUGI_URL:-https://api.trykintsugi.com}"
export SMOKE_TAX_STATE="${SMOKE_TAX_STATE:-TX}"

require_kintsugi_hmac_secret() {
  if [[ -z "${KINTSUGI_HMAC_SECRET:-}" ]]; then
    echo "Set KINTSUGI_HMAC_SECRET in docker/.env (must match your Kintsugi Kill Bill connection)" >&2
    exit 1
  fi
  case "${KINTSUGI_HMAC_SECRET}" in
    change-me-to-match-kintsugi-connection|local-dev-hmac-secret)
      echo "Set KINTSUGI_HMAC_SECRET in docker/.env to your real connection secret" >&2
      exit 1
      ;;
  esac
}

kb_auth=(-u "${KILLBILL_ADMIN_USER}:${KILLBILL_ADMIN_PASSWORD}")
kb_tenant=(-H "X-Killbill-ApiKey: ${KILLBILL_API_KEY}" -H "X-Killbill-ApiSecret: ${KILLBILL_API_SECRET}")

require_cmd() {
  for cmd in "$@"; do
    command -v "${cmd}" >/dev/null 2>&1 || {
      echo "Missing required command: ${cmd}" >&2
      exit 1
    }
  done
}

maven_project_prop() {
  local expression="$1"
  (cd "${REPO_ROOT}" && mvn -q help:evaluate -Dexpression="${expression}" -DforceStdout)
}

plugin_jar_name() {
  local version artifact
  version="$(maven_project_prop project.version)"
  artifact="$(maven_project_prop project.artifactId)"
  echo "${artifact}-${version}.jar"
}

plugin_install_path() {
  local version jar
  version="$(maven_project_prop project.version)"
  jar="$(plugin_jar_name)"
  echo "/var/lib/killbill/bundles/plugins/java/${PLUGIN_NAME}/${version}/${jar}"
}

killbill_container_id() {
  docker compose -f "${COMPOSE_FILE}" ps -q killbill 2>/dev/null || true
}

wait_for_killbill() {
  local attempts="${1:-60}"
  local i
  echo "==> Waiting for Kill Bill at ${KILLBILL_URL}"
  for ((i = 1; i <= attempts; i++)); do
    if curl -sf "${KILLBILL_URL}/1.0/healthcheck" >/dev/null 2>&1; then
      echo "Kill Bill is up"
      return 0
    fi
    sleep 5
  done
  echo "Kill Bill did not become healthy in time" >&2
  return 1
}

require_killbill() {
  curl -sf "${KILLBILL_URL}/1.0/healthcheck" >/dev/null || {
    echo "Kill Bill not healthy at ${KILLBILL_URL} — run: docker compose -f docker/docker-compose.yml up -d" >&2
    exit 1
  }
}

plugin_container_path() {
  local version
  version="$(maven_project_prop project.version)"
  echo "/var/lib/killbill/bundles/plugins/java/${PLUGIN_NAME}/${version}"
}
