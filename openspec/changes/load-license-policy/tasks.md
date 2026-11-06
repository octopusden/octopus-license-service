## 1. The `license-policy-registry` module

- [x] 1.1 Create the module:
  - [x] 1.1.1 `settings.gradle.kts`: include `:license-policy-registry` with `projectDir = engine/policy-registry`.
  - [x] 1.1.2 `engine/policy-registry/build.gradle.kts`: `bitbucket-client` from `octopus-external-systems-client` and `versions-api`, versions pinned in `gradle.properties`; `feign-core` `compileOnly` at the client's version; JUnit for tests. No Spring, no `core`.
  - [x] 1.1.3 Confirm with `:license-policy-registry:dependencies --configuration runtimeClasspath`: no Spring, no `:core`. Feign 12.2 and Jackson 2.14 come with the Bitbucket client `2.0.113`.
- [x] 1.2 Write failing unit tests for `LicenseRegistryParser` (pure, sample registry files as strings):
  - [x] 1.2.1 Whitelist and hidden list: `|`-separated, trimmed, blank and `#` lines ignored.
  - [x] 1.2.2 No `licenses-whitelist.txt`, and an empty one: the load fails.
  - [x] 1.2.3 `merges.txt`: first name canonical; a name on two lines keeps the first line and is a problem.
  - [x] 1.2.4 Overrides, read as Java properties: single version; range; `|` in the value; empty value ignored and not a problem; key without three parts is a problem; range `[1.0,` is a problem; the other lines still apply; an escaped key (`\ `) reads as `Properties` reads it.
  - [x] 1.2.5 Texts, read as Java properties: `licenses/` entry with its file; `http`/`https` entry kept as a URL; entry pointing at a missing file is a problem; any other value is a problem.
  - [x] 1.2.6 `licensesWithoutText`: a whitelisted license with no entry; with an entry whose file is missing; with a URL entry is not listed; a hidden license with no text is not listed.
- [x] 1.3 Write failing unit tests for `LicensePolicy.judge`, one per spec scenario:
  - [x] 1.3.1 Override in range, outside the range, single version, several licenses, two matching entries give the greater key's, empty value keeps the found licenses, no coordinates means no override.
  - [x] 1.3.2 Canonical names: synonym, different casing, whitelist name not in `merges.txt` with another casing, unknown name kept.
  - [x] 1.3.3 Verdicts: no license, only `Unknown`/`unknown license`/blank, dual license with one allowed, hidden only, nothing allowed.
  - [x] 1.3.4 Judgement licenses are canonical, sorted and without unknown markers.
  - [x] 1.3.4a `text(license)`: registry file, URL, case-insensitive lookup on the canonical name, none.
  - [x] 1.3.5 Parity with the prototype: the five rows of the design's worked example.
  - [x] 1.3.5a (added on review) Writing 1.3.5 showed the prototype ignores case for `merges.txt` only: it calls `Borland` against hidden `BORLAND` not allowed. Four rows match the prototype; the fifth is the service's case-insensitive list match (D5). The design's Context and Goals now say so.
  - [x] 1.3.6 (added on review) A library version `versions-api` cannot read is not overridden, rather than failing the judgement.
- [x] 1.4 Implement, as separate units:
  - [x] 1.4.1 `model/` — `LicensePolicy`, `MavenCoordinates`, `Judgement`, `LicenseVerdict`, `LicenseText`, one per file.
  - [x] 1.4.2 `impl/parser/LicenseRegistryParser` — registry files to `LicensePolicy`. Public, not `internal`: the server's tests build policies through it from sample files, the same seam the module's tests use.
  - [x] 1.4.3 `model/OverrideRule` — group, artifact, parsed range, licenses; `internal`. In `model/`, not `impl/parser/`: `LicensePolicy` applies it, and the model does not import from `impl`.
  - [x] 1.4.4 (added on review) `exception/InvalidLicenseRegistryException` — a registry that cannot make a policy (no or empty whitelist), told apart from an unreachable one.
- [x] 1.5 Write failing tests for `BitbucketLicensePolicySource` against a stub HTTP server (JDK `HttpServer`), sample project and repository names:
  - [x] 1.5.1 Load: head commit, `licenses/` listing, every file; the policy carries the commit.
  - [x] 1.5.2 `headCommit()` makes one call and reads no file.
  - [x] 1.5.3 Head moves during a load: starts over; moves three times: fails.
  - [x] 1.5.4 `401`, `503` and a closed port: `LicenseRegistryUnavailableException`. The `503` answers HTML, not Bitbucket's JSON, as a proxy in maintenance would.
  - [x] 1.5.5 (added on review) A registry without a whitelist is `InvalidLicenseRegistryException`, not unavailable.
- [x] 1.6 Implement `LicensePolicySource` (`headCommit()`, `load()`), `LicenseRegistryUnavailableException` and `BitbucketLicensePolicySource(url, token, project, repository)`, reading the default branch. The client's failures are `FeignException`, its own `BitbucketClientException`s, and `IllegalStateException` from its error decoder for any error it cannot name; all three are unavailable. A bearer token, as Bitbucket Server's personal access tokens use.
- [x] 1.7 `:license-policy-registry:test`: 56 tests, 0 failures, 0 skipped (judge 16, overrides 9, texts 8, parser 10, worked example 5, Bitbucket source 8). detekt and ktlint clean. No infrastructure needed.

## 2. `server`: hold, refresh and use the policy

- [x] 2.1 Write failing tests:
  - [x] 2.1.1 `LicensePolicyRefresher` with a fake source: first load sets the holder; head unchanged reads no file; head changed reloads; failed reload keeps the old policy; failed first load leaves the holder empty and retries.
  - [x] 2.1.2 API: before the first load, `POST` answers `503` and the client raises `ServiceUnavailableException`; no report is stored.
  - [x] 2.1.3 API: after a load at `a1b2c3d`, a finished report's `licenseRegistryCommit` is `a1b2c3d`.
  - [x] 2.1.4 A reload between start and generation does not change the commit the generation records.
  - [x] 2.1.5 Readiness: down while the holder is empty, up after; details show commit, load time, licenses without text and problems. Before the first load `/actuator/health/readiness` answers `503` and `/actuator/health/liveness` `200`; after it both answer `200`.
  - [x] 2.1.6 `LicenseServiceConfigTest`: each missing registry setting fails the context, naming the property; defaults for the intervals.
- [x] 2.2 Implement:
  - [x] 2.2.1 `LicenseServiceProperties.licenseRegistry` — URL, token, project, repository (nullable so their absence is named), refresh and retry intervals.
  - [x] 2.2.2 `LicenseServiceConfig` — the `LicensePolicySource`, `LicensePolicyHolder` and `LicensePolicyRefresher` beans; each missing registry setting fails startup naming it. `@EnableScheduling` was already there for the report evictor.
  - [x] 2.2.3 `LicensePolicyHolder` and `LicensePolicyRefresher`.
  - [x] 2.2.4 `LicensePolicyHealthIndicator` (`licenseRegistry`); `management.endpoint.health.show-details: always`, so the details show without credentials.
  - [x] 2.2.5 `LicenseReportServiceImpl.start` — reads the holder once; empty raises `ServiceUnavailableException`; passes the policy to the generator.
  - [x] 2.2.6 `LicenseReportGenerator.generate(target, policy)`; `EmptyLicenseReportGenerator` records `policy.commit`.
  - [x] 2.2.7 `application.yaml` — interval defaults only; `bootstrap-dev.yml` — the registry settings from `BITBUCKET_URL`, `BITBUCKET_TOKEN`, `LICENSE_REGISTRY_PROJECT`, `LICENSE_REGISTRY_REPOSITORY`.
  - [x] 2.2.8 (added on review) The refresher ticks every retry interval and skips a tick until the refresh interval has passed since its last check, so one `@Scheduled` method serves both intervals; tested with a hand-moved `Clock`.
- [x] 2.3 `:license-service:test`: 47 tests, 0 failures, 0 skipped (API 23, not loaded 3, config 8, refresher 6, health 2, store 5).
- [x] 2.3a (added on review) `LicensePolicyHealthIndicatorTest`: readiness follows the policy in memory, never Bitbucket. Bitbucket going down after the first load leaves `licenseRegistry` UP on the loaded commit; down from the start leaves it DOWN.
- [x] 2.4 (added on review) OKD's stand-wide `default.yml` uses `/actuator/health` as the startup probe, `/actuator/health/liveness` as liveness, and no readiness probe. Decided as components-registry-service does: probes enabled, the readiness group is `readinessState` and `licenseRegistry`, the liveness group stays `livenessState` only, so a Bitbucket outage never gets the pod restarted by liveness.

## 3. Documentation

- [x] 3.1 `docs/architecture.md` — the `engine/` license registry slot becomes `engine/policy-registry` (`:license-policy-registry`): solid in the diagram, Bitbucket client and `versions-api` in its table row; the request flow checks the policy before it registers a report and records its commit.
- [x] 3.2 `CLAUDE.md` — `policy-registry` in the `engine/` line of "Shape"; the license registry under "Reads".
- [x] 3.3 `README.md` — a `license-policy-registry` row next to `license-target-registry`; the `503` of `POST` until the first load.
- [x] 3.4 (added on review) The architecture page's "license rules" is now "license policy", the term the openspec and the code use.

## 4. Finalization

- [x] 4.1 `./gradlew build qualityCheck` green: 163 tests across all modules, 0 failures, 0 skipped; detekt and ktlint clean.
- [x] 4.2 Out of scope holds: no library is judged in a report (`server` never calls `judge`); nothing writes license files; `LicenseVerdict` has no `UNIDENTIFIED`.
- [x] 4.3 `engine/policy-registry/src` imports neither Spring nor `org.octopusden.octopus.license.core` (grep).
- [x] 4.4 No project, repository or real component name in the repository: fixtures use `SAMPLE`, `sample-license-registry`, `org.example`; grep finds no internal host or project.
- [x] 4.5 The design's risks hold as written; `TD-001` stays open.
- [ ] 4.7 (added on review) Later, in the service's deployment values: startup probe on `/actuator/health/liveness` and a readiness probe on `/actuator/health/readiness`, as components-registry-service has. Until then the stand-wide startup probe reads the aggregate `/actuator/health`, which includes `licenseRegistry`, so a Bitbucket outage at deploy keeps a new pod from starting. Not part of this change.
- [ ] 4.6 Before deploying, Spring Cloud Config and Vault hold the license registry settings for QA and Prod; without them the service does not start.
