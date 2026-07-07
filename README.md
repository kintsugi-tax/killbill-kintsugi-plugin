# Kill Bill Kintsugi Tax Plugin

> **Canonical repository:** development has moved to [killbill/killbill-kintsugi-plugin](https://github.com/killbill/killbill-kintsugi-plugin). See [PR #1](https://github.com/killbill/killbill-kintsugi-plugin/pull/1) for the upstream handover.

Kill Bill **invoice plugin** that delegates sales tax to [Kintsugi](https://trykintsugi.com) during invoice generation.

## What it does

On each invoice (dry-run or commit), the plugin:

1. Maps Kill Bill invoice line items and account ship-to address to a tax estimate request
2. Calls `POST /killbill/tax/estimate` or `/commit` on your Kintsugi API
3. Maps tax lines to Kill Bill `TAX` invoice items linked to taxable lines

Authentication on every call:

| Header | Value |
|--------|--------|
| `X-Killbill-ApiKey` | Kill Bill tenant API key (must match your Kintsugi Kill Bill connection) |
| `X-Killbill-Kintsugi-Signature` | HMAC-SHA256 hex digest of the **raw JSON body** |

The HMAC secret must match on both the Kill Bill plugin config and your Kintsugi Kill Bill connection.

## Prerequisites

- JDK 11+
- Maven 3.9+ (build from source) or a release JAR
- Kill Bill **0.24.x** with invoice plugin support
- A Kintsugi account with Kill Bill connected and the tax engine enabled

### Kill Bill compatibility

| Plugin version | Kill Bill |
|----------------|-----------|
| 0.1.x          | 0.24.x    |

## Build and test

```bash
mvn clean verify
```

**Docker E2E** (recommended for maintainers): see [docker/README.md](docker/README.md).

Artifact: `target/kintsugi-plugin-0.1.0.jar`

## Install on Kill Bill

### Option A: KPM (recommended when a release is published)

```bash
kpm install_java_plugin kintsugi --from-source-file=target/kintsugi-plugin-0.1.0.jar
```

Or install a published artifact from Maven Central once the Kill Bill project publishes
`org.kill-bill.billing.plugin.java:kintsugi-plugin` — see [Kill Bill KPM](https://docs.killbill.io/latest/kpm).
Until then, use the [GitHub release JAR](https://github.com/kintsugi-tax/killbill-kintsugi-plugin/releases) or build from source.

### Option B: Manual copy

1. Copy the JAR into Kill Bill's Java plugin layout, e.g.  
   `/var/lib/killbill/bundles/plugins/java/killbill-kintsugi/0.1.0/kintsugi-plugin-0.1.0.jar`
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
| `kintsugiUrl` | Yes | Kintsugi API base URL (no trailing slash), e.g. `https://api.trykintsugi.com` |
| `hmacSecret` | Yes | Shared secret; must match the HMAC secret on your Kintsugi Kill Bill connection |
| `killbillUrl` | No | Kill Bill base URL for optional Aviate billing-account lookup (default `http://127.0.0.1:8080`) |
| `aviateIdToken` | No | Aviate JWT ([Aviate auth](https://docs.killbill.io/latest/aviate-authentication)). When set, the plugin reads [billing accounts](https://docs.killbill.io/latest/aviate-billing-account) before falling back to custom fields. Omit for non-Aviate deployments. |

`kintsugiUrl` must be reachable from the Kill Bill JVM (network/firewall/DNS).

## Kintsugi setup

In your Kintsugi account:

1. Connect Kill Bill (base URL, tenant API key/secret, admin credentials).
2. Set the connection HMAC secret to the same value as plugin `hmacSecret`.
3. Enable the tax engine on the connection.

See [Kintsugi documentation](https://trykintsugi.com/docs) for the full setup guide.

## Verify

After configuration, generate or dry-run an invoice for an account with taxable line items. The invoice should include `TAX` rows linked to taxable `EXTERNAL_CHARGE` or subscription lines when Kintsugi returns non-zero tax.

Check Kill Bill logs for `Kintsugi returned N tax line(s)` from `KintsugiInvoicePluginApi`.

### Healthcheck

```bash
curl -u '<killbill-admin-user>:<killbill-admin-password>' \
  -H 'X-Killbill-ApiKey: <tenant-api-key>' \
  -H 'X-Killbill-ApiSecret: <tenant-api-secret>' \
  'https://<killbill-host>/plugins/killbill-kintsugi/healthcheck'
```

Returns healthy when the plugin is loaded and tenant config is present.

## Local development

Maintainers: use the self-contained docker toolkit (no Kintsugi platform repo required):

```bash
cp docker/.env.example docker/.env
./docker/run-e2e.sh
```

See [docker/README.md](docker/README.md) for step-by-step scripts, configuration, and troubleshooting.

## Troubleshooting

| Symptom | Likely cause |
|---------|----------------|
| No `TAX` lines on invoice | Tax engine not enabled in Kintsugi, or ship-to address has no tax obligation |
| `401` / `403` from Kintsugi | HMAC mismatch — verify the same secret on plugin config and Kintsugi connection |
| `Kintsugi plugin not configured` in logs | Missing `uploadPluginConfig/killbill-kintsugi` for the tenant |
| Connection timeout | Kill Bill cannot reach `kintsugiUrl` (DNS, firewall, or Docker networking) |
| Healthcheck unhealthy | Plugin config missing `kintsugiUrl` or `hmacSecret` for the tenant |

## Behavior notes

- **Dual deployment**: Aviate tenants — the Aviate plugin will pass [plugin properties](docs/aviate-integration.md#plugin-property-contract) on invoice generation; optional `aviateIdToken` fills gaps via billing-account HTTP. Non-Aviate tenants use custom fields only.
- **HTTP/1.1**: outbound calls use HTTP/1.1 so request bodies match HMAC signatures reliably.
- **External charges**: lines without a plan name use a default product category.
- **Retries**: transient failures raise `InvoicePluginApiRetryException` (1m / 5m / 15m backoff).
- **Zero tax**: `$0` tax lines are not added to the invoice.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Security reports: [SECURITY.md](SECURITY.md).

Maven Central publishing is handled by the Kill Bill project after upstream transfer;
see [docs/maven-central-handoff.md](docs/maven-central-handoff.md).

Local testing: [docker/README.md](docker/README.md).

## License

Apache License 2.0 — see [LICENSE](LICENSE).
