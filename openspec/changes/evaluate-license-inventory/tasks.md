## 1. The inventory records what is ours (Decision 2)

- [x] 1.1 Failing tests for `Ownership.isOurs(id, copyInOwnRepository)`, one per scenario of "Each library records whether it is ours":
  - [x] 1.1.1 Maven id of an internal group; of another group; group equal to an internal group.
  - [x] 1.1.2 Checksum-only file named `{internal group}.…`: ours, `copyInOwnRepository` not called.
  - [x] 1.1.3 Checksum-only file with a copy in an own repository (prefix and `local` ending); with a copy only in `…-cache`; with a copy in `rnd-releases` (no `local` ending).
  - [x] 1.1.4 `npm`, `pypi`, `Unrecognized`: never ours, `copyInOwnRepository` not called.
- [x] 1.2 Failing tests for `ChecksumNamer` and `LibraryTable`:
  - [x] 1.2.1 A checksum-only file with a copy in our repository stays unnamed and its row is ours.
  - [x] 1.2.2 A checksum-only file renamed to Maven coordinates of a third-party group is not ours; of an internal group, ours.
  - [x] 1.2.3 `libraries()` gives `ours` for Maven rows by group, without asking the store.
  - [x] 1.2.4 The scanner over in-memory fakes: the worked example's ours and not-ours rows (`portal-plugins` ours, the rest not).
- [x] 1.3 Implement:
  - [x] 1.3.1 `model/FoundLibrary.kt` — `ours: Boolean`.
  - [x] 1.3.2 `impl/shared/Ownership.kt` — `isOurs(id, copyInOwnRepository)`; the three existing checks stay its building blocks.
  - [x] 1.3.3 `impl/completing/ChecksumNamer.kt` — records the own-repository answer in the table instead of discarding it.
  - [x] 1.3.4 `impl/shared/LibraryTable.kt` — keeps the recorded checksums; `libraries()` fills `ours` through `Ownership.isOurs`. The table takes `Ownership` in its constructor rather than as a `libraries` argument: eight constructions against 24 calls.
- [x] 1.4 Confirm: `:license-inventory:test` green, `PackageDependencyTest` unchanged and green; the inventory's parity view ignores `ours` (the prototype has no such column before stage 11). `:license-inventory:test` 201 green (8 new).

## 2. The `license-evaluation` module and its model (Decisions 1, 12)

- [x] 2.1 `settings.gradle.kts`: `:license-evaluation` at `engine/evaluation`; build file with `:license-inventory`, `:license-policy-registry`, JUnit. Confirm with `dependencies`: no Spring, no `:core`, no client on the compile classpath. Confirmed: `:license-inventory`, `:license-target-registry`, `:license-policy-registry` and the Kotlin stdlib only.
- [x] 2.2 Model: `LicenseAssessment`, `AssessedLibrary`, `LibraryVerdict`, `SkippedPackages`; `LicenseEvaluator` signature.
- [x] 2.3 `PackageDependencyTest`: `model` imports nothing of `impl`; no import of a client, Spring, `core`, `java.io`/`java.net`, or the inventory's and the policy's `impl`.

## 3. Pure decisions (Decisions 3, 4)

- [x] 3.1 Failing tests for `PackageScope`, one per scenario:
  - [x] 3.1.1 `docker` dropped.
  - [x] 3.1.2 `rpm`, `deb`, `alpine` skipped as OS packages.
  - [x] 3.1.3 `pypi` only in images skipped; in an image and a zip counted; only in a zip counted.
  - [x] 3.1.4 `maven`, `npm`, `generic`, `golang`, `pub`: counted.
- [x] 3.2 Failing tests for `LibraryJudge`, one per scenario:
  - [x] 3.2.1 Ours: `INTERNAL`, even when checksum-only; overridden still reported.
  - [x] 3.2.2 `ChecksumOnly` not ours, with and without licenses: `UNIDENTIFIED`.
  - [x] 3.2.3 `Unrecognized` `generic://…` not ours: `UNIDENTIFIED`; `pkg:generic/…`: judged.
  - [x] 3.2.4 Synonym, override (Maven only), no license, not allowed: the policy's verdict, canonical names, overridden.
  - [x] 3.2.5 Non-Maven id: judged without coordinates.
- [x] 3.3 Implement `impl/PackageScope.kt` and `impl/LibraryJudge.kt`.
- [x] 3.4 Confirm tests green. Tests build the policy with the real `LicenseRegistryParser`, so the judge is checked against the policy's own rules, not a stub.

## 4. `LicenseEvaluator` (Decisions 5, 8)

- [x] 4.1 Failing tests:
  - [x] 4.1.1 The worked example, whole: verdicts, licenses, overridden, totals, per-file counts.
  - [x] 4.1.2 Order kept; inventory kept unchanged.
  - [x] 4.1.3 OS package in two files: total 1, each file 1.
  - [x] 4.1.4 No library: empty assessment, zero totals, no per-file entry.
- [x] 4.2 Implement `LicenseEvaluator.kt`.
- [x] 4.3 Confirm `:license-evaluation:test` green: 22; `ktlintCheck` and `detekt` clean.

## 5. The server (Decisions 9, 10)

- [ ] 5.1 Failing tests for `SummaryMapper`, one per scenario of `license-report-summary`:
  - [ ] 5.1.1 Counts per verdict, zeros included; internal and skipped not counted.
  - [ ] 5.1.2 Violations: not allowed, unknown, unidentified listed in order with files sorted; internal and allowed not listed.
  - [ ] 5.1.3 Trust null; inputs carried.
- [ ] 5.2 Failing tests for `EvaluatingLicenseReportGenerator` with a fake scanner and the real evaluator:
  - [ ] 5.2.1 Scanned: summary from the assessment, `scanned` counted.
  - [ ] 5.2.2 `InventorySourceUnavailableException`: rethrown, `unavailable` counted.
  - [ ] 5.2.3 Any other exception: rethrown, `failed` counted.
- [ ] 5.3 Failing tests for configuration: the service does not start without `jfrog.url`, `jfrog.token`, `ownership.internal-groups`, `ownership.own-repository-prefixes`; each message names the property.
- [ ] 5.4 Failing test, end to end through the controller with a fake scanner: a report ends `DONE` with counts; an unavailable scan ends `FAILED` with the message.
- [ ] 5.5 Implement:
  - [ ] 5.5.1 `configuration/LicenseServiceProperties.kt` — `jfrog`, `ownership`.
  - [ ] 5.5.2 `configuration/LicenseServiceConfig.kt` — `JfrogLicenseInventoryScanner` and `LicenseEvaluator` beans, required settings checked by name.
  - [ ] 5.5.3 `service/impl/SummaryMapper.kt`, `service/impl/EvaluatingLicenseReportGenerator.kt`; delete `EmptyLicenseReportGenerator.kt`.
  - [ ] 5.5.4 `server/build.gradle.kts` — `:license-inventory`, `:license-evaluation`.
  - [ ] 5.5.5 `application.yaml` defaults and comments; `bootstrap-dev.yml` placeholders.
- [ ] 5.6 Confirm `:license-service:test` green.

## 6. Parity, end to end (Decision 11)

- [ ] 6.1 `:license-inventory`: `java-test-fixtures`; move `ParityView` and `ParityDiff` into `testFixtures`, unchanged; its `test` and `parityTest` still compile and pass.
- [ ] 6.2 Failing tests for the evaluation parity view and diff (unit, no systems): prototype `REVIEW_HASH_ONLY` reads as `UNIDENTIFIED`; `LICENCE_NOT_ALLOWED` derived from `UNKNOWN` and `NOT_ALLOWED`; a verdict difference with equal found licenses and override is filed under "policy rules"; skipped totals and per-artifact `os_skipped` compared.
- [ ] 6.3 `:license-evaluation` `parityTest` source set and task, outside `check`, Kover disabled for it; `PrototypeParityTest` with the environment of Decision 11.
- [ ] 6.4 Run: a fresh local prototype run over the components of run 14, then `./gradlew :license-evaluation:parityTest`. Record the result here: components compared, differences outside "policy rules" (expected 0), the "policy rules" list.

## 7. Docs

- [ ] 7.1 `docs/architecture.md`: the evaluation module in the module graph and table; `license-inventory` records ours (Decision 2); the request flow runs the scan and the evaluation; the scan counter under Health and metrics; "Boundaries" no longer says the generator returns an empty summary.
- [ ] 7.2 `CLAUDE.md` and `README.md`: the new module, the new settings, `:license-evaluation:parityTest`.
- [ ] 7.3 The component design page: phase 3 specified, 3B reads `ours` from the inventory.

## 8. Finalization

- [ ] 8.1 `./gradlew build qualityCheck` green.
- [ ] 8.2 Out of scope holds, checked by grep: no trust computation (`Trust(` built nowhere but in tests); no license file written (the zip is the empty one); no prototype-shaped judging in `:license-evaluation` (it calls `LicensePolicy.judge` only).
- [ ] 8.3 Every risk in the design still stands or says why not; anything deferred has a `TD-NNN`.
