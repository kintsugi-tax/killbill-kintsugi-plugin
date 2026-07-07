#!/usr/bin/env bash
# One-command local workflow for Kill Bill maintainers.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ ! -f "${SCRIPT_DIR}/.env" ]]; then
  echo "Create docker/.env first:" >&2
  echo "  cp docker/.env.example docker/.env" >&2
  echo "  # edit KINTSUGI_HMAC_SECRET and tenant credentials" >&2
  exit 1
fi

# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"

require_cmd docker curl

echo "==> Start Kill Bill stack"
docker compose -f "${COMPOSE_FILE}" up -d
wait_for_killbill

"${SCRIPT_DIR}/install-plugin.sh"
"${SCRIPT_DIR}/setup-tenant.sh"
"${SCRIPT_DIR}/verify-plugin.sh"
"${SCRIPT_DIR}/smoke-invoice.sh"

echo
echo "E2E smoke passed."
