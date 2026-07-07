# Maven Central handoff (Kill Bill maintainers)

Kintsugi has prepared this plugin for publication under the existing Kill Bill
Maven namespace. Publishing to Maven Central is owned by the Kill Bill project.

## Coordinates

| Field | Value |
|-------|-------|
| groupId | `org.kill-bill.billing.plugin.java` |
| artifactId | `kintsugi-plugin` |
| OSGi plugin name | `killbill-kintsugi` |

## What Kintsugi has done

- Apache 2.0 `LICENSE` with headers on all source files
- `killbill-oss-parent` parent POM (release profile, Central URLs, signing plugins)
- Required Central metadata in `pom.xml`: `licenses`, `developers`, `scm`, `url`, `description`
- `mvn verify` passes on Java 11 in CI
- Healthcheck servlet, unit tests, README install docs
- GitHub Release with JAR attached ([v0.1.0](https://github.com/kintsugi-tax/killbill-kintsugi-plugin/releases/tag/v0.1.0))
- Self-contained docker E2E toolkit in `docker/` (install, tenant setup, smoke test)

## What Kill Bill does

1. **Transfer** the repo to the `killbill` GitHub org (update `scm` URLs in `pom.xml`)
2. **Publish** using existing Kill Bill infrastructure:
   - Sonatype credentials (`MAVEN_USERNAME`, `MAVEN_PASSWORD`)
   - GPG signing key (`GPG_SIGNING_KEY`, `GPG_PASSPHRASE`)
   - `mvn release:clean release:prepare release:perform` (see [`.github/workflows/release.yml`](.github/workflows/release.yml) in this repo)
3. **Docs PR** — draft text in [killbill-docs-pr.md](killbill-docs-pr.md)

## Verify before first Central release

```bash
mvn clean verify
```

Expected artifacts from `sonatype-oss-release` profile (provided by parent):

- `kintsugi-plugin-<version>.jar`
- `kintsugi-plugin-<version>-sources.jar`
- `kintsugi-plugin-<version>-javadoc.jar`

## KPM after Central publish

```bash
kpm install_java_plugin kintsugi
```

## Kintsugi platform prerequisite

The Kintsugi API (`/killbill/tax/estimate`, `/killbill/tax/commit`) must be reachable
from customer Kill Bill deployments. Setup docs: [trykintsugi.com/docs](https://trykintsugi.com/docs).
