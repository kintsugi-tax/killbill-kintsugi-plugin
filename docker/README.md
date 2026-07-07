# Docker development stack

Run Kill Bill, Kaui, and MariaDB locally for plugin testing.

## Start

```bash
docker compose -f docker/docker-compose.yml up -d
```

- Kill Bill: http://localhost:8080
- Kaui: http://localhost:9090

## Install the plugin

```bash
chmod +x docker/install-plugin.sh
./docker/install-plugin.sh
```

Then configure the tenant per the main [README](../README.md).

## Healthcheck

After install and tenant configuration:

```bash
curl -u admin:password \
  -H 'X-Killbill-ApiKey: <tenant-api-key>' \
  -H 'X-Killbill-ApiSecret: <tenant-api-secret>' \
  http://localhost:8080/plugins/killbill-kintsugi/healthcheck
```

Point `kintsugiUrl` at a reachable Kintsugi API host. For local platform development, use a tunnel (for example ngrok) to your API if Kill Bill runs in Docker.
