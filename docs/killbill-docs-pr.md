# Draft text for Kill Bill documentation PR

Add to https://docs.killbill.io/latest/plugin_introduction (tax plugins section):

## Kintsugi

The [killbill-kintsugi-plugin](https://github.com/kintsugi-tax/killbill-kintsugi-plugin) delegates sales tax calculation to [Kintsugi](https://trykintsugi.com) during invoice generation.

Install:

```bash
kpm install_java_plugin kintsugi --from-source-file=target/kintsugi-plugin-0.1.0.jar
```

Per-tenant configuration:

```properties
org.killbill.invoice.plugin=killbill-kintsugi
```

Upload plugin config via `uploadPluginConfig/killbill-kintsugi` with `kintsugiUrl` and `hmacSecret`. See the plugin README for setup and verification steps.

Healthcheck: `GET /plugins/killbill-kintsugi/healthcheck`
