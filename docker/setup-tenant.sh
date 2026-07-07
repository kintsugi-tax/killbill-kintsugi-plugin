#!/usr/bin/env bash
# Enable the Kintsugi invoice plugin and upload per-tenant config.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"

require_cmd curl
require_killbill

require_kintsugi_hmac_secret

echo "==> Upload plugin config (kintsugiUrl + hmacSecret)"
curl -sf "${kb_auth[@]}" "${kb_tenant[@]}" \
  -H "Content-Type: text/plain" \
  -H "X-Killbill-CreatedBy: kintsugi-docker-setup" \
  -d "kintsugiUrl: ${KINTSUGI_URL}
hmacSecret: ${KINTSUGI_HMAC_SECRET}" \
  "${KILLBILL_URL}/1.0/kb/tenants/uploadPluginConfig/${PLUGIN_NAME}"

echo
echo "==> Enable invoice plugin on tenant"
curl -sf "${kb_auth[@]}" "${kb_tenant[@]}" \
  -H "Content-Type: text/plain" \
  -H "X-Killbill-CreatedBy: kintsugi-docker-setup" \
  -d "{\"org.killbill.invoice.plugin\":\"${PLUGIN_NAME}\"}" \
  "${KILLBILL_URL}/1.0/kb/tenants/uploadPerTenantConfig"

echo
echo "==> Plugin healthcheck"
HEALTH="$(curl -sf "${kb_auth[@]}" "${kb_tenant[@]}" \
  "${KILLBILL_URL}/plugins/${PLUGIN_NAME}/healthcheck")"
echo "${HEALTH}" | python3 -m json.tool 2>/dev/null || echo "${HEALTH}"

echo
echo "Tenant setup complete."
echo "  kintsugiUrl=${KINTSUGI_URL}"
echo "  tenant apiKey=${KILLBILL_API_KEY}"
