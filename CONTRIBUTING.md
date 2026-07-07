# Contributing

Thanks for your interest in the Kill Bill Kintsugi tax plugin.

## Development setup

- JDK 11+
- Maven 3.9+
- Optional: Docker for local Kill Bill testing (see [docker/README.md](docker/README.md))

## Build and test

```bash
mvn clean verify
```

CI runs the same `verify` goal on Java 11.

## Pull requests

1. Fork the repository and create a feature branch.
2. Keep changes focused; include tests for behavior changes.
3. Ensure `mvn verify` passes locally.
4. Open a PR with a clear summary and test notes.

## Code style

Match the existing code in this repository: Apache 2.0 headers on new Java files, minimal comments, and Kill Bill plugin conventions from [killbill-hello-world-java-plugin](https://github.com/killbill/killbill-hello-world-java-plugin).

## Releases

Maintainers cut releases from `main` using git tags (`v*`). The release workflow attaches the built JAR to the GitHub release.
