## 1. Clients in octopus-external-systems-client (Decision 14)

- [x] 1.1 Check against our Xray (3.150.37, through `jf xr curl`): `POST /api/v1/artifact/status` exists; a released zip answers `overall` and `details.sca` `DONE`, a pom `NOT_SUPPORTED`, a missing file `{"error": …}`. `summary/artifact` answers a pom with an `errors` entry "not indexed/cached"; `summary/component` returns ids without their scheme.
- [x] 1.1a Record a sample answer of each endpoint as a test fixture, names anonymised: `xray-client/src/test/resources/xray/`; `status-pending.json` is written by hand, as no scan was pending to record.
- [x] 1.2 `artifactory-client`, additive:
  - [x] 1.2.1 Failing tests: an AQL item with `actual_sha1` and `sha256`; a build-info artifact with `path`; `searchBuildsByAQL` reading `build.name`, `build.number`.
  - [x] 1.2.2 `AqlItem.actualSha1`, `AqlItem.sha256`, `Artifact.path`; `AqlBuildSearchResponse` and `searchBuildsByAQL(query)`; `getBuildInfo` with an optional `buildRepo`.
  - [x] 1.2.3 Existing tests green; no field renamed or removed. `:artifactory-client:test` 10 green (3 new). `getBuildInfo(name, number, buildRepo)` is an overload, not an optional parameter: a Feign interface cannot default one.
  - [x] 1.2.4 (added on review) The new methods take `ArtifactoryClient` past detekt's `TooManyFunctions`; a `detekt-baseline.xml` records it, as the Bitbucket, Gitea and TeamCity clients do.
- [x] 1.3 New `xray-client` module:
  - [x] 1.3.1 Failing tests against a stub server: artifact status (done, pending, not supported); artifact summary with licenses and components, and an error per path; component summary for a batch.
  - [x] 1.3.2 `XrayClient`, `XrayClassicClient` on `ClientParametersProvider`, DTOs, error decoder, as `artifactory-client` is built.
  - [x] 1.3.3 `settings.gradle.kts` and both publication allowlists. No README line: the README covers only the TeamCity test data. `:xray-client:test` 9 green; `qualityStatic` green.
  - [x] 1.3.4 (added on review) A status the client does not know reads as `UNKNOWN` (`@JsonEnumDefaultValue`), so a new Xray status cannot fail a scan.
- [ ] 1.4 Release both (branch `add-xray-client` in octopus-external-systems-client, two commits; published locally as `2.0.0-xray-SNAPSHOT` meanwhile); pin `artifactory-client.version` and `xray-client.version` in the service's `gradle.properties`.

## 2. `explicit` on the license target (Decision 4)

- [x] 2.1 Failing tests in `LicenseTargetDescriberTest`: explicit and implicit component, one per new spec scenario.
- [x] 2.2 `RegistryComponent.explicit` from `distribution.explicit`; `LicenseTarget.explicit`.
- [x] 2.3 Confirm `:license-target-registry:test` and `:license-service:test` green: 49 and 57.

## 3. The `license-inventory` module and its model (Decisions 1, 3)

- [x] 3.1 `settings.gradle.kts`: `:license-inventory` at `engine/inventory`; build file with `:license-target-registry`, `artifactory-client`, `xray-client`, JUnit; `feign-core` `compileOnly`. Confirm with `dependencies`: no Spring, no `:core`, no `:license-policy-registry`. Confirmed: runtime classpath is `:license-target-registry`, the two clients and the Kotlin stdlib. The clients come from `mavenLocal()` in the module's build file until 1.4.
- [x] 3.2 Failing tests for `PackageId` parsing and rendering:
  - [x] 3.2.1 `pkg:maven/g/a@v`, `pkg:npm/@scope/name@1.0`, `pkg:golang/golang.org/x/net@0.20.0` round-trip.
  - [x] 3.2.2 `generic://sha256:<64 hex>/name.jar` is `ChecksumOnly`.
  - [x] 3.2.3 A string that would not render back the same, and an unknown scheme, are `Unrecognized`, verbatim. `PackageIdTest` 14 green.
- [x] 3.3 Model: `LicenseInventory`, `ReleasedFile`, `FileKind`, `FileStatus`, `SelectionReason`, `BuildRef`, `BuildLookup`, `FoundPackage`, `PackageId`, `Evidence`, `Finding`, `FindingCode`, `OwnershipRules`; `LicenseInventoryScanner`; `InventorySourceUnavailableException`.

## 4. Pure decisions

- [x] 4.1 Failing tests, then implement each:
  - [x] 4.1.1 `ConventionBuilds`: explicit, implicit, `_docker`.
  - [x] 4.1.2 `PublishedFileName`: plain, classifier, other artifact id in the module, `.tar.gz`, name without `-{version}`.
  - [x] 4.1.3 `ArtifactPatternMatcher`: full match only; group and artifact from the same entry; `[\w-\.]`; invalid pattern equals literally.
  - [x] 4.1.4 `CandidateSelector`: declared, pattern, both, neither; metadata by extension and by classifier; declared metadata is read.
  - [x] 4.1.5 `CandidateExtractor`: Maven entries; docker `manifest.json` to `image:tag`; `list.manifest.json` and layers dropped; declared by image name.
  - [x] 4.1.6 `FileStatusJudge`: one test per spec scenario of "A located file's status".
  - [x] 4.1.7 `ShippingScopes`: each runtime scope; Android release and embed; debug and test variants; custom configuration.
  - [x] 4.1.8 `BuildDependencyReader`: producing module by sha1 and by name; none; npm `prod` only; Gradle-supplied skipped; local file; third-party archive is a package; our archive under runtime is followed; under a custom configuration only with Xray proof; test scope never.
  - [x] 4.1.9 `CycloneDxParser`: purl without qualifiers; license id, name, expression; component without purl skipped.
  - [x] 4.1.10 `PackageTable`: merge by id; union of licenses, evidence, files; rename drops the unnamed licenses and merges into an existing row.
  - [x] 4.1.11 `Ownership`: internal group prefix; internal file name (`group.` prefix); own repository by prefix and `local` suffix.
  - [x] 4.1.12 `XrayComponentIds`: `gav://..` cleanup; `go` to `golang`; each listed scheme; unknown kept; Maven and npm back to Xray ids.
  - [x] 4.1.13 `RepositoryPreference`: release local, local, other, then name.
- [x] 4.1.14 (added on review) A docker module's file with no recorded path names no image and is not listed; the prototype would stop the component with an error there. Build-info records the path with the file name (`tds/mpi/1.2.484-8315/manifest.json`, checked on a run-14 build), which the client's KDoc first had wrong.
- [x] 4.1.15 (added on review) `Ownership` is checked before an archive is followed (`isOwnArchive`) and by `shipsByProof`, both with the prototype's id prefix test.
- [x] 4.2 Confirm `:license-inventory:test` green; record the count: 117 green across 14 classes; ktlint and detekt clean.

## 5. Orchestration over ports (Decisions 2, 5–9)

- [x] 5.1 Ports `BuildRecords`, `ArtifactStore`, `BinaryScanner`, `LicenseDatabase` and their data (`BuildRecord`, `BuildModule`, `BuiltFile`, `BuildDependency`, `StoredFile`); in-memory fakes in the test source set.
- [x] 5.2 Failing tests with fakes, one per spec scenario of the listing requirements, then `ReleasedFileLister` and `BuildLocator`.
- [x] 5.3 Failing tests with fakes, one per spec scenario of "A read file's packages…", "Only shipped dependencies…" and "Our own archives…", then `FileContentReader` and `SbomReader`.
- [x] 5.4 Failing tests with fakes for checksum naming and license fill (batches of 100, a failed batch), then `ChecksumNamer` and `LicenseFiller`.
- [x] 5.5 `JfrogLicenseInventoryScanner`: the worked example of the design end to end with fakes; `NO_DISTRIBUTION`; findings distinct; memoized ports ask each build, file and scan once per report.
- [x] 5.6 (added on review) Finding subjects and details: one test per row of the table in Decision 3; listing order of Decision 5; the four extra odd rules of Decision 10 (8–11).
- [x] 5.7 (added on review) The declared-leftover route is its own class, `DeclaredLeftoverLocator`: kept inside `ReleasedFileLister` it took the lister past detekt's function limit, and it is a second route with its own rules.
- [x] 5.8 (added on review) Within one released file, findings of nested archives and of dependencies come out in a different interleaving than the prototype's (it follows an archive in the middle of its dependency loop). Each finding is still there once; the parity check compares findings as sets.
- [x] 5.9 (added on review) Rule D10.10 (a package Xray lists under no license is not found) lives in the Xray adapter, tested in 6.1.3; rule D10.11 (a URL `docker` entry) is the target's parsing, already covered there.
- [x] 5.10 Confirm: `:license-inventory:test` 151 green; ktlint and detekt clean.

## 6. Adapters (Decisions 12, 13)

- [x] 6.1 Failing tests against a stub HTTP server, with the fixtures of 1.1:
  - [x] 6.1.1 `ArtifactoryBuildRecords`: found; missing and `500` are no build; builds by sha1.
  - [x] 6.1.2 `ArtifactoryArtifactStore`: by sha1, path and sha256; preferred copy; `503` is `InventorySourceUnavailableException`.
  - [x] 6.1.3 `XrayBinaryScanner`: pending then done; not supported and not scanned are not waited for; still pending after the budget reads the summary anyway, and the budget is shared by the scan's files; an error for the path is not indexed; reads `details.sca`, not `overall`; a package listed under no license is not found (D10.10).
  - [x] 6.1.4 `XrayLicenseDatabase`: batches; scheme-less ids in the answer; a failed batch.
- [x] 6.2 Implement the four adapters and the public `JfrogLicenseInventoryScanner(url, token, ownership, xrayWait)` constructor.
- [x] 6.3 (added on review) The status only decides whether to wait; the summary is always read, as the prototype reads it. A status other than `DONE` alone no longer means "not indexed", which could have lost a list the prototype read (Decision 12, spec updated).
- [x] 6.4 (added on review) `JfrogCalls` retries three times, 1 s then 2 s apart, as the prototype did; the Artifactory client reports every error but `404` as a server error, so a client error is retried too (Decision 13).
- [x] 6.5 (added on review) Ports are built per scan (`JfrogPorts.create`), so the Xray wait budget is one report's.
- [x] 6.6 Confirm: `:license-inventory:test` 172 green, against a stub HTTP server for the adapters; ktlint and detekt clean.

## 7. Docs

- [x] 7.1 `docs/architecture.md`: the license scan module and its dependency rules; `CLAUDE.md` module list. Server wiring is the evaluation change's (Decision 15).

## 8. Parity check (Decision 16)

- [x] 8.1 `parityTest` source set and task, not part of `check`; inputs from environment variables (`PROTOTYPE_RUN_DIR`, `COMPONENTS_REGISTRY_URL`, `JFROG_URL`, `JFROG_TOKEN`, `INTERNAL_GROUPS`, `OWN_REPOSITORY_PREFIXES`, optional `PARITY_COMPONENTS`). A missing variable fails the task by name. The report is `engine/inventory/build/parity/report.md`.
- [x] 8.2 The projection of both sides and the diff, with unit tests on two hand-made component JSONs (same, and one difference of each kind): `ParityView`, `ParityDiff` in the test source set, so `ParityDiffTest` runs in every build; 6 green. Packages are compared after the prototype's stage-11 drops (docker layers, OS packages, Python seen only in images); findings as sets, without the prototype's verdict violations, with "licence" read as "license".
- [ ] 8.3 Run the prototype locally with `--only w4web3,dmp-bundle` and then the parity check; record the differences here, each fixed or explained by Decision 11.
- [ ] 8.4 Same for all components of run 14's scope; record the result here.

## 9. Finalization

- [x] 9.1 `./gradlew build` and `qualityCheck` green.
  - [x] 9.1.1 (added on review) Kover ran every `Test` task for its report, `parityTest` included, which fails without the real systems; `disabledForTestTasks` takes it out.
- [x] 9.2 Out of scope holds: grep shows no `LicensePolicy`, verdict or trust code in `:license-inventory`; `server` is unchanged. Checked: no match in `engine/inventory/src/main`; `git diff` against `license-registry-verdicts` shows no change under `server`, `runner`, `client` or `core` main sources.
- [x] 9.3 Every risk in the design still stands or says why not; deferred work has a `TD-NNN`: TD-002, revisiting the rules of Decision 10 after parity. The clients' release is task 1.4, still open.
