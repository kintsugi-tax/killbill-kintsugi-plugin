# Kill Bill Kintsugi Tax Plugin

Kill Bill **invoice plugin** that delegates sales tax to [Kintsugi](https://trykintsugi.com) during invoice generation.

| | |
|---|---|
| **Repository** | [github.com/kintsugi-tax/killbill-kintsugi-plugin](https://github.com/kintsugi-tax/killbill-kintsugi-plugin) |
| **Platform setup** | [Kintsugi Kill Bill integration guide](https://github.com/kintsugi-tax/kintsugi-platform/blob/main/docs/killbill/integration.md) |
| **API wire format** | [tax-provider.md](https://github.com/kintsugi-tax/kintsugi-platform/blob/main/docs/killbill/tax-provider.md) |

## What it does

On each invoice (dry-run or commit), the plugin:

1. Maps Kill Bill invoice line items and account ship-to address → Kintsugi tax estimate JSON
2. Calls `POST /killbill/tax/estimate` or `/commit` on your Kintsugi platform API
3. Maps tax lines → Kill Bill `TAX` invoice items linked to taxable lines

Authentication on every call:

| Header | Value |
|--------|--------|
| `X-Killbill-ApiKey` | Kill Bill tenant API key (must match the Kintsugi connection) |
| `X-Killbill-Kintsugi-Signature` | HMAC-SHA256 hex digest of the **raw JSON body** |

The HMAC secret is configured in both places: Kill Bill plugin tenant config **and** the Kintsugi Kill Bill connection in Import.

## Prerequisites

- JDK 11+
- Maven 3.9+ (build from source) or a release JAR
- Kill Bill **0.24.x** with invoice plugin support
- An active **Kintsugi** account with Kill Bill connected and tax engine enabled

### Kill Bill compatibility

| Plugin version | Kill Bill |
|----------------|-----------|
| 0.1.x          | 0.24.x    |

## Build

```bash
mvn clean test package
```

Artifact: `target/killbill-kintsugi-plugin-0.1.0.jar`

## Install on Kill Bill

### Option A: KPM (recommended when a release is published)

```bash
kpm install_java_plugin killbill-kintsugi --from-source-file=target/killbill-kintsugi-plugin-0.1.0.jar
```

Or install a published artifact once available on Maven Central / your artifact repository — see [Kill Bill KPM](https://docs.killbill.io/latest/kpm).

### Option B: Manual copy

1. Copy the JAR into Kill Bill's Java plugin layout, e.g.  
   `/var/lib/killbill/bundles/plugins/java/killbill-kintsugi/0.1.0/killbill-kintsugi-plugin-*.jar`
2. Set the default version symlink if your deployment uses one.
3. Restart Kill Bill and confirm the plugin is `RUNNING` with `InvoicePluginApi` in `GET /1.0/kb/pluginsInfo`.

See [Kill Bill plugin installation](https://docs.killbill.io/latest/plugin_installation).

## Tenant configuration

### 1. Enable the invoice plugin

Kill Bill 0.24+ expects JSON for per-tenant config:

```bash
curl -u '<killbill-admin-user>:<killbill-admin-password>' \
  -H 'X-Killbill-ApiKey: <tenant-api-key>' \
  -H 'X-Killbill-ApiSecret: <tenant-api-secret>' \
  -H 'Content-Type: text/plain' \
  -H 'X-Killbill-CreatedBy: setup' \
  -d '{"org.killbill.invoice.plugin":"killbill-kintsugi"}' \
  'https://<killbill-host>/1.0/kb/tenants/uploadPerTenantConfig'
```

### 2. Upload plugin config

```bash
curl -u '<killbill-admin-user>:<killbill-admin-password>' \
  -H 'X-Killbill-ApiKey: <tenant-api-key>' \
  -H 'X-Killbill-ApiSecret: <tenant-api-secret>' \
  -H 'Content-Type: text/plain' \
  -H 'X-Killbill-CreatedBy: setup' \
  -d 'kintsugiUrl: https://api.trykintsugi.com
hmacSecret: <shared-hmac-secret>' \
  'https://<killbill-host>/1.0/kb/tenants/uploadPluginConfig/killbill-kintsugi'
```

YAML POJO form is also supported — see `KintsugiConfigurationHandler` in this repo.

| Config key | Required | Description |
|------------|----------|-------------|
| `kintsugiUrl` | Yes | Kintsugi platform API base URL (no trailing slash), e.g. `https://api.trykintsugi.com` |
| `hmacSecret` | Yes | Shared secret; must match **HMAC secret** on the Kintsugi Kill Bill connection |

`kintsugiUrl` must be reachable from the Kill Bill JVM (network/firewall/DNS). Use your Kintsugi account's API host if different from the example above.

## Kintsugi platform setup

Complete these steps in the Kintsugi app (Import → Kill Bill):

1. Connect Kill Bill (base URL, tenant API key/secret, admin credentials).
2. Set the connection **HMAC secret** to the same value as plugin `hmacSecret`.
3. Run initial import, then enable **Tax Engine** on the connection.

The connection's `external_id` must equal the Kill Bill tenant API key — Kintsugi resolves the org from `X-Killbill-ApiKey` on tax calls.

Full walkthrough: [integration.md](https://github.com/kintsugi-tax/kintsugi-platform/blob/main/docs/killbill/integration.md).

## Verify

After configuration, generate or dry-run an invoice for an account with taxable line items. The invoice should include `TAX` rows linked to taxable `EXTERNAL_CHARGE` or subscription lines when Kintsugi returns non-zero tax for the ship-to jurisdiction and your registrations.

Check Kill Bill logs for `Kintsugi returned N tax line(s)` from `KintsugiInvoicePluginApi`.

## Behavior notes

- **HTTP/1.1**: outbound calls to Kintsugi use HTTP/1.1 so request bodies match HMAC signatures reliably.
- **External charges**: lines without a plan name are categorized as `Physical` / `General Physical` for Kintsugi product resolution.
- **Retries**: transient failures raise `InvoicePluginApiRetryException` (1m / 5m / 15m backoff).
- **Zero tax**: `$0` tax lines are not added to the invoice.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
