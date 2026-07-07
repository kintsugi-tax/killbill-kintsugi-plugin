#!/usr/bin/env bash
# Post an external charge and check whether the plugin adds TAX invoice items.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"

require_cmd curl python3
require_killbill

echo "==> Create account (${SMOKE_TAX_STATE} ship-to)"
ACCOUNT_HEADERS="$(mktemp)"
trap 'rm -f "${ACCOUNT_HEADERS}"' EXIT
curl -sf -D "${ACCOUNT_HEADERS}" -o /dev/null "${kb_auth[@]}" "${kb_tenant[@]}" \
  -H "Content-Type: application/json" \
  -H "X-Killbill-CreatedBy: kintsugi-smoke-invoice" \
  -d "$(SMOKE_TAX_STATE="${SMOKE_TAX_STATE}" python3 <<'PY'
import json, os, time
state = (os.environ.get("SMOKE_TAX_STATE") or "TX").strip().upper()
print(json.dumps({
    "name": "Kintsugi Plugin Smoke",
    "email": f"smoke-{int(time.time())}@example.com",
    "currency": "USD",
    "country": "US",
    "state": state,
    "city": "Austin",
    "postalCode": "78701",
    "address1": "1 Congress Ave",
}))
PY
)" \
  "${KILLBILL_URL}/1.0/kb/accounts"
ACCOUNT_ID="$(grep -i '^Location:' "${ACCOUNT_HEADERS}" | sed -E 's|.*/accounts/||' | tr -d '\r')"
echo "accountId=${ACCOUNT_ID}"

echo "==> Add external charge \$100"
curl -sf "${kb_auth[@]}" "${kb_tenant[@]}" \
  -H "Content-Type: application/json" \
  -H "X-Killbill-CreatedBy: kintsugi-smoke-invoice" \
  -d '[{
    "amount": 100.00,
    "currency": "USD",
    "description": "Smoke test taxable charge",
    "itemType": "EXTERNAL_CHARGE"
  }]' \
  "${KILLBILL_URL}/1.0/kb/invoices/charges/${ACCOUNT_ID}" >/dev/null

echo "==> Fetch invoice lines"
INVOICES="$(curl -sf "${kb_auth[@]}" "${kb_tenant[@]}" \
  "${KILLBILL_URL}/1.0/kb/accounts/${ACCOUNT_ID}/invoices")"
INVOICE_ID="$(python3 -c "import json,sys; print(json.load(sys.stdin)[0]['invoiceId'])" <<<"${INVOICES}")"
ITEMS_FILE="$(mktemp)"
curl -sf "${kb_auth[@]}" "${kb_tenant[@]}" \
  "${KILLBILL_URL}/1.0/kb/invoices/${INVOICE_ID}" > "${ITEMS_FILE}"

python3 - "${ITEMS_FILE}" "${SMOKE_TAX_STATE}" <<'PY'
import json, sys
path, state = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as fh:
    inv = json.load(fh)
lines = inv.get("items") or []
print("invoiceId", inv.get("invoiceId"), "status", inv.get("status"))
for line in lines:
    print(" -", line.get("itemType"), line.get("amount"), line.get("description"))
tax = [l for l in lines if l.get("itemType") == "TAX"]
external = [l for l in lines if l.get("itemType") == "EXTERNAL_CHARGE"]
if not external:
    sys.exit("FAIL: no EXTERNAL_CHARGE lines")
if tax:
    print(f"OK: plugin added {len(tax)} TAX item(s)")
else:
    print("WARN: no TAX lines — check:", file=sys.stderr)
    print("  - ./docker/setup-tenant.sh was run", file=sys.stderr)
    print("  - KINTSUGI_URL is reachable from the Kill Bill container", file=sys.stderr)
    print("  - Kintsugi connection HMAC matches KINTSUGI_HMAC_SECRET", file=sys.stderr)
    print(f"  - tax engine enabled + {state} registration in Kintsugi", file=sys.stderr)
    sys.exit(1)
PY
rm -f "${ITEMS_FILE}"
