## 1. The `license-target-registry` module

- [x] 1.1 Create the module:
  - [x] 1.1.1 `settings.gradle.kts`: include `:license-target-registry` with `projectDir = target-registry`.
  - [x] 1.1.2 `target-registry/build.gradle.kts`: `components-registry-service-client` `3.2.0` and `versions-api` `2.0.14`, versions pinned in `gradle.properties`; JUnit for tests. No Spring, no `core`.
  - [x] 1.1.3 Confirmed with `:license-target-registry:dependencies --configuration runtimeClasspath`: no Spring, no `:core`; Jackson 2.16 and Feign 12.2 come with the registry client.
  - [x] 1.1.4 (added on review) `feign-core` `12.2` is `compileOnly`, pinned as `components-registry-feign.version`: the resolver needs `FeignException` to tell an unreachable registry from other failures, and the client already brings Feign at runtime, so declaring it there would mix two Feign versions in production.
- [x] 1.2 Write failing unit tests for `LicenseTargetDescriber` and its parts (pure, no HTTP):
  - [x] 1.2.1 Exclusion, one test per rule: archived, internal, `WHISKEY`, label `test-component`, `doc_`/`doc-` names in four casings, configured entry with its own reason, configured match is exact.
  - [x] 1.2.2 Exclusion order: archived, `WHISKEY` and a `doc_` name together give `Archived component`.
  - [x] 1.2.3 `docker-tools`, `doc`, `mydoc_x`, `documentation-service` are not excluded.
  - [x] 1.2.4 `GAV`: entry with extension; with classifier; two entries with spaces around the comma; two-part entry is `Unsupported` and keeps its neighbour; `file:`, `http:`, `https:` entries are `Unsupported`; absent, empty, blank and `" , "` fields declare nothing.
  - [x] 1.2.5 `docker`: image without tag; with tag; registry host with port and tag; registry host with port and no tag; `GAV` entries come before docker images.
  - [x] 1.2.6 `DEB` and `RPM` are never read: `RegistryComponent` carries no field for them, so the parser cannot see them.
  - [x] 1.2.7 Maven artifact patterns: one of two ranges holds the version; none holds it (empty, still resolved); comma-separated patterns split and trimmed; two overlapping entries both kept.
  - [x] 1.2.8 Artifact versions: build version differs, kept second; equal, listed once; absent, requested version only.
- [x] 1.3 Implement, as separate units:
  - [x] 1.3.1 `LicenseTarget.kt` — `LicenseTarget`, `DistributionSpec`, `DeclaredArtifact`, `ArtifactPattern`, `ComponentVersion`, `TargetResolution`, `ConfiguredExclusion`.
  - [x] 1.3.2 `ExclusionRules` — the ordered rules and their fixed reasons; `internal`.
  - [x] 1.3.3 `DistributionParser` — `GAV` and `docker` fields to `DeclaredArtifact`s; `internal`.
  - [x] 1.3.4 `LicenseTargetDescriber` — registry facts in, `LicenseTarget` out; selects pattern entries with `VersionRangeFactory` and `NumericVersionFactory`. `RegistryComponent` and `RegistryArtifactPattern` hold the facts in the module's own terms, so the rules and the parser never see the client's DTOs.
- [x] 1.4 Write failing tests for `RegistryLicenseTargetResolver` against a stub HTTP server (JDK `HttpServer`):
  - [x] 1.4.1 Resolved component: three calls, target as expected. The fixtures are real answers recorded from the components registry (`dmp-bundle` 1.4.12, its `maven-artifacts`, `version-names`), with owners, VCS URL, group ids and security groups replaced, and a build version and docker field set so every field is exercised.
  - [x] 1.4.2 Excluded component: exactly one call.
  - [x] 1.4.3 `404` from the registry: `ComponentVersionNotFoundException`.
  - [x] 1.4.4 `503` from the registry, and a closed port: `ComponentsRegistryUnavailableException`.
  - [x] 1.4.5 (added on review) The recorded `maven-artifacts` key is `(,0),[0,)`, a union of two ranges; `VersionRangeFactory` parses it and the pattern is selected. The prototype's own range matcher handled only single ranges.
- [x] 1.5 Implement `LicenseTargetResolver`, `ComponentVersionNotFoundException`, `ComponentsRegistryUnavailableException` and `RegistryLicenseTargetResolver(registryUrl, exclusions)`. A `FeignException` with status `>= 500`, or `-1` (no answer at all), is unavailable; any other is rethrown.
- [x] 1.6 `:license-target-registry:test`: 43 tests, 0 failures, 0 skipped (ExclusionRules 16, DistributionParser 16, LicenseTargetDescriber 6, RegistryLicenseTargetResolver 5). No infrastructure needed.

## 2. `core` and `client`: service unavailable

- [x] 2.1 Client test: a `503` with the error envelope raises `ServiceUnavailableException`.
- [x] 2.2 `ServiceUnavailableException` added to `LicenseServiceException`; `503` mapped in `LicenseServiceErrorDecoder`.
- [x] 2.3 `:client:test`: 5 tests, 0 failures.
- [x] 2.4 (added on review) detekt's `SwallowedException` flagged the server re-throwing the module's exceptions without their cause. `LicenseServiceException`, `NotFoundException` and `ServiceUnavailableException` take an optional `cause`; it stays on the server side, only the message crosses the wire.

## 3. `server`: resolve at start

- [x] 3.1 API tests in `LicenseReportApiTest`, with a `FakeTargetResolver` bean:
  - [x] 3.1.1 Not excluded: the generator receives the resolved target.
  - [x] 3.1.2 Excluded: `EXCLUDED` with the reason in the start answer and on `GET`; the generator never sees it; `GET …/files` raises `LicenseFilesNotAvailableException`.
  - [x] 3.1.3 Not found: `404` envelope, and `NotFoundException` in the client.
  - [x] 3.1.4 Unavailable: `ServiceUnavailableException` in the client.
  - [x] 3.1.5 Blank component or version: the existing test still answers `400`; validation runs before the resolver.
- [x] 3.2 `LicenseServiceConfigTest` (`ApplicationContextRunner`): without `license-service.components-registry-url` the context fails naming the property; with it the resolver is wired; `application.yaml` seeds 11 exclusions.
- [x] 3.3 Implement:
  - [x] 3.3.1 `LicenseServiceProperties` — `componentsRegistryUrl` (nullable so its absence is reported by name), `exclusions`.
  - [x] 3.3.2 `LicenseServiceConfig` — a `LicenseTargetResolver` bean; `requireNotNull` on the URL fails startup.
  - [x] 3.3.3 `LicenseReportServiceImpl.start` — resolves before registering; `Excluded` stored finished as `EXCLUDED`, no executor call; module exceptions mapped to `NotFoundException` and `ServiceUnavailableException`, with their cause.
  - [x] 3.3.4 `LicenseReportGenerator.generate(target: LicenseTarget)`; `EmptyLicenseReportGenerator` takes it and ignores it.
  - [x] 3.3.5 `ExceptionInfoHandler` — `ServiceUnavailableException` to `503`.
  - [x] 3.3.6 `LicenseReportStore` unchanged: `finish` alone stores a report that never ran, already finished.
  - [x] 3.3.7 `application.yaml` — the seeded `exclusions`; `bootstrap-dev.yml` — the URL from `COMPONENTS_REGISTRY_URL`; tests set the URL as a property.
- [x] 3.4 `:license-service:test`: 26 tests, 0 failures (API 18, config 3, store 5).

## 4. Documentation

- [x] 4.1 `docs/architecture.md` — the module in the diagram and the table, its rule and why; the request flow resolves the target before the background step.
- [x] 4.2 `CLAUDE.md` — the module in "Shape"; the components registry under "Reads".

## 5. Finalization

- [x] 5.1 `./gradlew build qualityCheck` green: 85 tests across all modules, 0 failures, 0 skipped; detekt and ktlint clean.
- [x] 5.2 Out of scope holds: no Artifactory, Xray or release management call (grep finds only two KDoc mentions); `DEB`/`RPM` have no field to be read through; nothing is cached.
- [x] 5.3 `target-registry/src` imports neither Spring nor `org.octopusden.octopus.license.core` (grep: none).
- [x] 5.4 The design's risks hold as written.
- [x] 5.5 The runner needs no change: a start failure becomes `RunOutcome.Unavailable` with the server's message, and an `EXCLUDED` report goes through the existing "finished without download" path, both already tested.
- [ ] 5.6 (added on review) Before deploying, Spring Cloud Config must hold `license-service.components-registry-url` for QA and Prod; without it the service does not start (Decision 9).
