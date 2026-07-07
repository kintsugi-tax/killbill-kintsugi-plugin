# Aviate integration (0.1.0)

Kill Bill feedback on [PR #1](https://github.com/killbill/killbill-kintsugi-plugin/pull/1) asks for enterprise parity with Aviate + Avalara patterns. Shipped in **0.1.0** on `kintsugi-tax`; upstream `killbill/killbill-kintsugi-plugin` syncs separately.

## Deployment paths (Kill Bill team guidance)

Pierre confirmed the plugin must support **both** deployment models — not only Aviate:

| Deployment | Tax registrations & exemptions | Product tax codes |
|------------|-------------------------------|-------------------|
| **Aviate** | Aviate Billing Accounts (`taxRegistrations[]`, address on BA) | Aviate Catalog (`products:` map) |
| **Non-Aviate** (e.g. AvaTax-style) | Kill Bill account **custom fields** (`taxExempt`, `customerUsageType`) | Per invoice-item **custom fields** (`taxCode`) |

Resolution order:

1. **Plugin properties** (primary — Aviate plugin will pass these automatically; AvaTax-compatible names)
2. **Custom fields** (non-Aviate fallback)
3. **Aviate billing account HTTP** (optional gap-fill when `aviateIdToken` is set and ship-to was not passed as a property)

## Plugin property contract

Documented in `InvoicePluginPropertyNames` for coordination with the Aviate plugin:

| Property | Purpose |
|----------|---------|
| `customerUsageType` | Entity use code (AvaTax) |
| `taxExempt` | `true` when tax-exempt |
| `taxRegistrationNumber` / `trn` | TRN / VAT / EIN |
| `companyName` | Customer display name |
| `shipToLine1` … `shipToPostalCode` | Ship-to address (when set, skips BA HTTP lookup) |
| `taxCode_<invoiceItemId>` | Per-line product tax code (AvaTax pattern) |

## Shipped in 0.1.0

* **Idempotency** — skip Kintsugi when every taxable line already has a linked `TAX` item.
* **Custom fields** — `customerUsageType`, `taxExempt`, per-line `taxCode`.
* **Plugin properties** — AvaTax-aligned names; properties override custom fields.
* **Aviate billing account HTTP** — optional gap-fill via `aviateIdToken` + `killbillUrl`.

## Planned follow-ups

* **Aviate Catalog tax codes** — resolve plan → product → Aviate tax code from tenant config or API.
* **Repairs and return documents** — `ITEM_ADJ` / `REPAIR_ADJ` as return lines; stable document IDs.

## References

* [Aviate Billing Accounts](https://docs.killbill.io/latest/aviate-billing-account)
* [Aviate Tax](https://docs.killbill.io/latest/aviate-tax)
* [AvaTax plugin](https://docs.killbill.io/latest/avatax-plugin)
