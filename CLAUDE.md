# octopus-license-service

Generates the third-party license files of a component version: the libraries in its released files, each license's verdict against the license registry, and the license texts. Replaces the license Maven and Gradle plugins. Stateless: it reads, it writes nowhere.

## Role in the ecosystem
- Called by: TeamCity build, RC and RELEASE templates through the `license-runner` metarunner; later the components management portal through `client`
- Reads: the components registry, the license registry (through Bitbucket); JFrog Artifactory and Xray
- Config: Spring Cloud Config with Vault; deployed to OKD QA and Prod from TeamCity

## Shape
- Kotlin, Gradle, JDK 25, Spring Boot 4
- `core` (DTOs, no dependencies), `client` (Feign), `server` (`:license-service`), `runner` (`:license-runner`, Clikt)
- `engine/`: the modules that produce a report, one per system read — `target-registry` (`:license-target-registry`, components registry only), `policy-registry` (`:license-policy-registry`, license registry only), `inventory` (`:license-inventory`, Artifactory and Xray only: the license scan), `evaluation` (`:license-evaluation`, no system: which libraries count and their verdicts)

## Working here
- Module rules and request flow: [docs/architecture.md](docs/architecture.md)
- Deferred work: [docs/tech-debt/](docs/tech-debt/README.md), `TD-NNN`, cited from code instead of `TODO`
- Build: `./gradlew build` · Quality gate: `./gradlew qualityCheck` · Run: `./gradlew :license-service:bootRun`
- Parity with the prototype: `./gradlew :license-evaluation:parityTest` (scan and evaluation) or `:license-inventory:parityTest` (scan only), real systems, never in `check` (see `PrototypeParityTest`)
- detekt is 2.x and configured in the root build, not by octopus-quality: 1.x cannot run on JDK 25
