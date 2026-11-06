# octopus-license-service

Generates the third-party license files of a component version: the libraries in its released files, each license's verdict against the license registry, and the license texts. Replaces the license Maven and Gradle plugins. Stateless: it reads, it writes nowhere.

## Role in the ecosystem
- Called by: TeamCity build, RC and RELEASE templates through the `license-runner` metarunner; later the components management portal through `client`
- Reads: the components registry; as later features land, JFrog Xray, Artifactory build-info, the license registry
- Config: Spring Cloud Config with Vault; deployed to OKD QA and Prod from TeamCity

## Shape
- Kotlin, Gradle, JDK 25, Spring Boot 4
- `core` (DTOs, no dependencies), `client` (Feign), `server` (`:license-service`), `runner` (`:license-runner`, Clikt), `target-registry` (`:license-target-registry`, components registry only)

## Working here
- Module rules and request flow: [docs/architecture.md](docs/architecture.md)
- Build: `./gradlew build` · Quality gate: `./gradlew qualityCheck` · Run: `./gradlew :license-service:bootRun`
- detekt is 2.x and configured in the root build, not by octopus-quality: 1.x cannot run on JDK 25
