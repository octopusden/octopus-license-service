## 1. The `license-policy-registry` module

- [ ] 1.1 Create the module:
  - [ ] 1.1.1 `settings.gradle.kts`: include `:license-policy-registry` with `projectDir = policy-registry`.
  - [ ] 1.1.2 `policy-registry/build.gradle.kts`: `bitbucket-client` from `octopus-external-systems-client` and `versions-api`, versions pinned in `gradle.properties`; `feign-core` `compileOnly` at the client's version; JUnit for tests. No Spring, no `core`.
  - [ ] 1.1.3 Confirm with `:license-policy-registry:dependencies --configuration runtimeClasspath`: no Spring, no `:core`.
- [ ] 1.2 Write failing unit tests for `LicenseRegistryParser` (pure, sample registry files as strings):
  - [ ] 1.2.1 Whitelist and hidden list: `|`-separated, trimmed, blank and `#` lines ignored.
  - [ ] 1.2.2 No `licenses-whitelist.txt`, and an empty one: the load fails.
  - [ ] 1.2.3 `merges.txt`: first name canonical; a name on two lines keeps the first line and is a problem.
  - [ ] 1.2.4 Overrides, read as Java properties: single version; range; `|` in the value; empty value ignored and not a problem; key without three parts is a problem; range `[1.0,` is a problem; the other lines still apply; an escaped key (`\ `) reads as `Properties` reads it.
  - [ ] 1.2.5 Texts, read as Java properties: `licenses/` entry with its file; `http`/`https` entry kept as a URL; entry pointing at a missing file is a problem; any other value is a problem.
  - [ ] 1.2.6 `licensesWithoutText`: a whitelisted license with no entry; with an entry whose file is missing; with a URL entry is not listed; a hidden license with no text is not listed.
- [ ] 1.3 Write failing unit tests for `LicensePolicy.judge`, one per spec scenario:
  - [ ] 1.3.1 Override in range, outside the range, single version, several licenses, two matching entries give the greater key's, empty value keeps the found licenses, no coordinates means no override.
  - [ ] 1.3.2 Canonical names: synonym, different casing, whitelist name not in `merges.txt` with another casing, unknown name kept.
  - [ ] 1.3.3 Verdicts: no license, only `Unknown`/`unknown license`/blank, dual license with one allowed, hidden only, nothing allowed.
  - [ ] 1.3.4 Judgement licenses are canonical, sorted and without unknown markers.
  - [ ] 1.3.4a `text(license)`: registry file, URL, case-insensitive lookup on the canonical name, none.
  - [ ] 1.3.5 Parity with the prototype: the five rows of the design's worked example.
- [ ] 1.4 Implement, as separate units:
  - [ ] 1.4.1 `model/` — `LicensePolicy`, `MavenCoordinates`, `Judgement`, `LicenseVerdict`, `LicenseText`, one per file.
  - [ ] 1.4.2 `impl/parser/LicenseRegistryParser` — registry files to `LicensePolicy`; `internal`.
  - [ ] 1.4.3 `impl/parser/OverrideRule` — group, artifact, parsed range, licenses; `internal`.
- [ ] 1.5 Write failing tests for `BitbucketLicensePolicySource` against a stub HTTP server (JDK `HttpServer`), sample project and repository names:
  - [ ] 1.5.1 Load: head commit, `licenses/` listing, every file; the policy carries the commit.
  - [ ] 1.5.2 `headCommit()` makes one call and reads no file.
  - [ ] 1.5.3 Head moves during a load: starts over; moves three times: fails.
  - [ ] 1.5.4 `401`, `503` and a closed port: `LicenseRegistryUnavailableException`.
- [ ] 1.6 Implement `LicensePolicySource` (`headCommit()`, `load()`), `LicenseRegistryUnavailableException` and `BitbucketLicensePolicySource(url, token, project, repository)`, reading the default branch.
- [ ] 1.7 Confirm `:license-policy-registry:test`. <Fill in: counts.>

## 2. `server`: hold, refresh and use the policy

- [ ] 2.1 Write failing tests:
  - [ ] 2.1.1 `LicensePolicyRefresher` with a fake source: first load sets the holder; head unchanged reads no file; head changed reloads; failed reload keeps the old policy; failed first load leaves the holder empty and retries.
  - [ ] 2.1.2 API: before the first load, `POST` answers `503` and the client raises `ServiceUnavailableException`; no report is stored.
  - [ ] 2.1.3 API: after a load at `a1b2c3d`, a finished report's `licenseRegistryCommit` is `a1b2c3d`.
  - [ ] 2.1.4 A reload between start and generation does not change the commit the generation records.
  - [ ] 2.1.5 Readiness: down while the holder is empty, up after; details show commit, load time, licenses without text and problems.
  - [ ] 2.1.6 `LicenseServiceConfigTest`: each missing registry setting fails the context, naming the property; defaults for the intervals.
- [ ] 2.2 Implement:
  - [ ] 2.2.1 `LicenseServiceProperties.licenseRegistry` — URL, token, project, repository (nullable so their absence is named), refresh and retry intervals.
  - [ ] 2.2.2 `LicenseServiceConfig` — the `LicensePolicySource` bean; `@EnableScheduling`.
  - [ ] 2.2.3 `LicensePolicyHolder` and `LicensePolicyRefresher`.
  - [ ] 2.2.4 `LicensePolicyHealthIndicator`, in the readiness group.
  - [ ] 2.2.5 `LicenseReportServiceImpl.start` — reads the holder once; empty raises `ServiceUnavailableException`; passes the policy to the generator.
  - [ ] 2.2.6 `LicenseReportGenerator.generate(target, policy)`; `EmptyLicenseReportGenerator` records `policy.commit`.
  - [ ] 2.2.7 `application.yaml` — interval defaults only; `bootstrap-dev.yml` — the registry settings from environment variables.
- [ ] 2.3 Confirm `:license-service:test`. <Fill in: counts.>

## 3. Documentation

- [ ] 3.1 `docs/architecture.md` — the module in the diagram and the table, its rule and why; the request flow checks the policy before it registers a report.
- [ ] 3.2 `CLAUDE.md` — the module in "Shape"; the license registry under "Reads".

## 4. Finalization

- [ ] 4.1 `./gradlew build qualityCheck` green. <Fill in: counts.>
- [ ] 4.2 Out of scope holds: no library is judged in a report; nothing writes license files; `UNIDENTIFIED` is not produced by the module.
- [ ] 4.3 `policy-registry/src` imports neither Spring nor `org.octopusden.octopus.license.core` (grep).
- [ ] 4.4 No project, repository or real component name in the repository: fixtures use sample names.
- [ ] 4.5 The design's risks hold as written; `TD-001` stays open.
- [ ] 4.6 Before deploying, Spring Cloud Config and Vault hold the license registry settings for QA and Prod; without them the service does not start.
