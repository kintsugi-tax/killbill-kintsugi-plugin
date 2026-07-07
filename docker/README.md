# Docker testing (Kill Bill maintainers)

Self-contained scripts to build, install, and test the Kintsugi invoice plugin
against a local Kill Bill stack. No dependency on the Kintsugi platform repo.

## Prerequisites

- Docker + Docker Compose
- JDK 11+ and Maven 3.9+
- `curl`, `python3`
- A Kintsugi test account with Kill Bill connected and tax engine enabled (for full smoke only)

## Quick start

```bash
cp docker/.env.example docker/.env
# Edit KINTSUGI_HMAC_SECRET (must match Kintsugi Kill Bill connection)
# Edit KILLBILL_API_KEY / KILLBILL_API_SECRET for your tenant

docker compose -f docker/docker-compose.yml up -d
./docker/run-e2e.sh
```

Or step by step:

```bash
./docker/install-plugin.sh    # build JAR, install, verify pluginsInfo
./docker/setup-tenant.sh      # uploadPluginConfig + enable invoice plugin
./docker/verify-plugin.sh     # pluginsInfo + healthcheck (no Kintsugi API call)
./docker/smoke-invoice.sh     # external charge → expect TAX lines
```

## Services

| URL | Service |
|-----|---------|
| http://localhost:8080 | Kill Bill |
| http://localhost:9090 | Kaui |

Default admin: `admin` / `password`

## Scripts

| Script | Purpose |
|--------|---------|
| `install-plugin.sh` | `mvn package`, copy JAR, `SET_DEFAULT` symlink, restart, verify `pluginsInfo` |
| `setup-tenant.sh` | Upload `kintsugiUrl` + `hmacSecret`, enable `killbill-kintsugi` invoice plugin |
| `verify-plugin.sh` | Confirm plugin is `RUNNING` with `InvoicePluginApi` (no Kintsugi API) |
| `smoke-invoice.sh` | Create account, post external charge, assert `TAX` invoice items |
| `run-e2e.sh` | Runs the full sequence above |
| `lib.sh` | Shared helpers (do not run directly) |

## Configuration (`docker/.env`)

Kintsugi provisions a **test org** for Kill Bill maintainers. You do not put `organization_id` or a Kintsugi API key in these env vars — the plugin identifies your org via the Kill Bill tenant API key on each tax call.

| Variable | Description |
|----------|-------------|
| `KILLBILL_API_KEY` / `KILLBILL_API_SECRET` | Kill Bill tenant credentials (create in Kaui). **Must equal** the Kintsugi Kill Bill connection `external_id`. |
| `KINTSUGI_URL` | Kintsugi API base URL reachable **from the Kill Bill container** |
| `KINTSUGI_HMAC_SECRET` | HMAC secret from the Kintsugi Kill Bill connection (not an API key) |
| `SMOKE_TAX_STATE` | US state for smoke ship-to (default `TX`; needs tax registration in the test org) |

**Test org checklist (Kintsugi side):**

1. Kill Bill connection created with tax engine enabled
2. Connection HMAC secret → `KINTSUGI_HMAC_SECRET`
3. Connection tenant API key → `KILLBILL_API_KEY` (and matching Kill Bill tenant in Kaui)

For a Kintsugi API running on the host machine:

```bash
KINTSUGI_URL=http://host.docker.internal:8000
```

## Plugin layout (manual reference)

Kill Bill docker images use `/var/lib/killbill/bundles` as the plugin root:

```
plugins/java/killbill-kintsugi/
  0.1.0/kintsugi-plugin-0.1.0.jar
  SET_DEFAULT -> 0.1.0
```

`install-plugin.sh` creates this layout automatically. Version and artifact name are read from `pom.xml`.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Plugin not in `pluginsInfo` | Re-run `install-plugin.sh`; check `docker logs` for OSGi errors |
| Healthcheck unhealthy | Run `setup-tenant.sh`; verify `kintsugiUrl` and `hmacSecret` |
| No `TAX` lines on invoice | Confirm Kintsugi tax engine + state registration; check Kill Bill logs for `Kintsugi tax estimate failed` |
| Connection timeout to Kintsugi | Use `host.docker.internal` or a public API URL reachable from Docker |

## Tear down

```bash
docker compose -f docker/docker-compose.yml down -v
```
