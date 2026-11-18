## 1. The `license-trust` module and its model (Decisions 1, 2, 11)

- [x] 1.1 Set up the module:
  - `settings.gradle.kts`: `:license-trust` at `engine/trust`;
  - build file: `api(project(":license-evaluation"))` and JUnit.

  Confirm with `dependencies` that no Spring, no `:core` and no client is on the compile classpath. Confirmed: `:license-evaluation`, `:license-inventory`, `:license-target-registry`, `:license-policy-registry` and the Kotlin stdlib only.
- [x] 1.2 Failing tests for the model:
  - [x] 1.2.1 `TrustLevel.lowest`:
    - empty gives `HIGH`;
    - `[MEDIUM, LOW]` gives `LOW`;
    - `[NONE, HIGH]` gives `NONE`.
  - [x] 1.2.2 `TrustReasonCode`: each code's level matches the prototype's `REASONS` table (eleven codes).
  - [x] 1.2.3 `TrustGrade.level` and `FileTrust.level` are derived from the codes.
- [x] 1.3 Implement the model:
  - `model/TrustLevel.kt`;
  - `model/TrustReasonCode.kt`: the level, title, meaning and fix texts from the prototype, with Decision 2's spelling and wording changes;
  - `model/TrustReason.kt`, `model/FileTrust.kt`, `model/TrustGrade.kt`;
  - the `TrustGrader` signature: moved to 4.3, written with its body, so no stub lands without one (added on review).
- [x] 1.4 `PackageDependencyTest`:
  - `model` imports nothing from `impl`;
  - nothing imports a client, Spring, `core`, `java.io` or `java.net`;
  - nothing imports the `impl` of the evaluation, the inventory or the policy.
- [x] 1.5 Confirm: `:license-trust:test` 31 green (`TrustReasonCodeTest` 24, `PackageDependencyTest` 2, `TrustLevelTest` 2, `DerivedLevelTest` 3); `ktlintCheck` and `detekt` clean.

## 2. The facts every rule reads (Decision 3)

- [x] 2.1 Failing tests for `GradingSubject.of(assessment, labels)`:
  - [x] 2.1.1 Selected files leave out `NOT_SELECTED` and `METADATA`.
  - [x] 2.1.2 Read files are `READ`. Covered files are read files plus `PLAIN_FILE`, in listing order.
  - [x] 2.1.3 Third-party libraries are the counted libraries without `INTERNAL`. Skipped packages are never among them.
  - [x] 2.1.4 `sbomSeen`:
    - an `INTERNAL` library with `SBOM` evidence counts;
    - a skipped OS package with `SBOM` evidence does not.
  - [x] 2.1.5 `thirdPartyIn(entry)`: a file's own third-party libraries, the ones whose `foundIn` holds it; `FileGrader` reads it (added on review).
- [x] 2.2 Implement `impl/GradingSubject.kt`. It also carries the findings, so `CoverageRule` reads one value like every other rule.
- [x] 2.3 Confirm: `GradingSubjectTest` 5 green, `:license-trust:test` 36; `ktlintCheck` and `detekt` clean.

## 3. Component rules (Decisions 4, 6, 7)

- [x] 3.1 Failing tests for `CoverageRule`, one per scenario of "Nothing covered…" and "Partly covered…":
  - [x] 3.1.1 `NO_DISTRIBUTION`, nothing selected: `NO_ARTIFACT_IN_REGISTRY` with `"{subject}: {detail}"`.
  - [x] 3.1.2 `UNSUPPORTED_DISTRIBUTION_ENTRY`: `NO_ARTIFACT_IN_REGISTRY`. `ARTIFACT_NOT_FOUND`: `ARTIFACT_NOT_FOUND`.
  - [x] 3.1.3 `NOT_READABLE` with `NOT_IN_XRAY` and `NO_BUILD_INFO`: two `NOT_SCANNABLE` reasons, in the order found.
  - [x] 3.1.4 Nothing covered and no mapped finding: `NOT_SCANNABLE`, "no registered artifact could be scanned".
  - [x] 3.1.5 Notes (`NOT_SCANNABLE` from a plain file, `BUILD_NAME_DIFFERS`) are never mapped.
  - [x] 3.1.6 3 of 4 covered: `PARTIAL_ARTIFACTS`, "3 of 4 registered artifacts checked".
  - [x] 3.1.7 All covered, with a plain file among them: no reason.
- [x] 3.2 Failing tests for `EmptyContentRule`:
  - [x] 3.2.1 Read files with only `INTERNAL` libraries: "2 file(s) checked, 0 outside libraries".
  - [x] 3.2.2 Label `c` or `cpp`: the C/C++ note. Neither: no note, whatever the build name.
  - [x] 3.2.3 Nothing read (only plain files): no reason.
  - [x] 3.2.4 One third-party library: no reason.
- [x] 3.3 Failing tests for `EcosystemRule`:
  - [x] 3.3.1 `javascript` with no `npm`: the worked example's detail, character for character.
  - [x] 3.3.2 `objective_c` and `swift` in one group; `c` and `go` in two groups, in sorted label order.
  - [x] 3.3.3 Detected list:
    - count descending;
    - a tie keeps the order of first appearance, by rendered id;
    - "detected nothing" when there is no third-party library.
  - [x] 3.3.4 The label `java`, and an unknown label: no reason. Nothing read: no reason.
  - [x] 3.3.5 `pub` and `cocoapods` without an SBOM: `FULL_LIST_UNAVAILABLE`, "found cocoapods, pub libraries, but no SBOM". With an SBOM on any counted library: no reason.
  - [x] 3.3.6 Only `maven`, `npm`, `generic`: no `FULL_LIST_UNAVAILABLE`.
- [x] 3.4 Failing tests for `SourceRule`:
  - [x] 3.4.1 A read file without `BUILD_RECORD`: `NO_BUILD_RECORD`, its entry.
  - [x] 3.4.2 A read file without `BINARY_SCAN`: `BUILD_LIST_ONLY`.
  - [x] 3.4.3 Two such files: one reason, entries space-separated, in listing order.
  - [x] 3.4.4 Unread files never count.
- [x] 3.5 Failing tests for `IdentificationShare` and `IdentificationRule`:
  - [x] 3.5.1 Unknown thresholds:
    - 5 of 50 crosses;
    - 4 of 10 does not (count);
    - 5 of 51 does not (share).
  - [x] 3.5.2 Unidentified thresholds:
    - 10 of 100 crosses;
    - 9 of 20 does not;
    - 12 of 200 does not.
  - [x] 3.5.3 Details: "6 of 42 outside libraries have no known license" and "{n} of {third} outside files identified by checksum only".
- [x] 3.6 Implement, as separate units, under `impl/`:
  - [x] 3.6.1 `ComponentRule.kt`: the fun interface.
  - [x] 3.6.2 `CoverageRule.kt`: owns the finding-to-reason mapping (the prototype's `NOT_ANALYSED_TYPES`).
  - [x] 3.6.3 `EmptyContentRule.kt`: owns `NATIVE_LABELS`.
  - [x] 3.6.4 `EcosystemRule.kt`: owns `LABEL_ECOSYSTEMS` and `COMPLETE_TYPES`.
  - [x] 3.6.5 `SourceRule.kt`.
  - [x] 3.6.6 `IdentificationShare.kt` and `IdentificationRule.kt`.
- [x] 3.7 Confirm the tests are green. Record the suite and the count. `:license-trust:test` 76 green: `CoverageRuleTest` 7, `EmptyContentRuleTest` 6, `EcosystemRuleTest` 10, `SourceRuleTest` 4, `IdentificationShareTest` 9, `IdentificationRuleTest` 4; `ktlintCheck` and `detekt` clean. The rules are stateless, so each is an `object` the grader lists, not a class it builds. `EcosystemRuleTest`'s tie order was checked against Python's `Counter.most_common` on the same ids.

## 4. File grading and the grader (Decisions 4, 5)

- [x] 4.1 Failing tests for `FileGrader`, one per scenario of "Each selected released file…":
  - [x] 4.1.1 Status codes: `UNSUPPORTED`, `NOT_FOUND`, `NOT_READABLE`, and `READ` without a binary scan.
  - [x] 4.1.2 `READ`:
    - without a build record;
    - with no third-party library;
    - with 5 of 20 `UNKNOWN`;
    - with 10 of 50 `UNIDENTIFIED`.
  - [x] 4.1.3 `READ` from its build record only, with third-party libraries under the thresholds: `BUILD_LIST_ONLY` alone. A file with neither source is `NOT_READABLE`, so `BUILD_LIST_ONLY` and `NO_BUILD_RECORD` never meet.
  - [x] 4.1.4 The image with only skipped OS packages: `NO_BUILD_RECORD`, `NO_THIRD_PARTY_FOUND`.
  - [x] 4.1.5 `PLAIN_FILE`: no codes.
- [x] 4.2 Failing tests for `TrustGrader`:
  - [x] 4.2.1 The worked example, end to end: level `LOW`, the four reasons in order, the four files.
  - [x] 4.2.2 Duplicate `(code, detail)` dropped. The same code with another detail is kept.
  - [x] 4.2.3 A `NONE` file does not lower the component.
  - [x] 4.2.4 `NOT_SELECTED` and `METADATA` files have no `FileTrust`.
  - [x] 4.2.5 An empty assessment with `NO_DISTRIBUTION` gives `NONE`.
  - [x] 4.2.6 File reasons do not roll up:
    - an image with only OS packages beside a zip with libraries: `NO_THIRD_PARTY_FOUND` on the image, not on the component;
    - 5 `UNKNOWN` of 20 in one file, and 5 of 200 overall: `UNKNOWN_LICENSES` on the file only.
- [x] 4.3 Implement `impl/FileGrader.kt` and `TrustGrader.kt`.
- [x] 4.4 Confirm `:license-trust:test` is green and Kover covers the module. Record the counts. 94 green (`FileGraderTest` 10, `TrustGraderTest` 8 new); line coverage 99.5 %, `koverVerify`, `ktlintCheck` and `detekt` clean.

## 5. Wire contract, server and runner (Decisions 8, 9)

- [x] 5.1 `core`:
  - `Trust(level, reasons, files)`;
  - `TrustReason(code, level, title, detail, meaning, fix)`;
  - `FileTrust(file, level, reasons)`;
  - `TrustReasonCode`;
  - `LicenseReportSummary.trust` non-null.

  Update the JSON serialization tests in `core` and `client`, if any exist. `ClassicLicenseServiceClientTest` decoded `"trust":null`, which the non-null contract now rejects; it decodes a full trust object instead.
- [x] 5.2 Failing tests for `SummaryMapper`:
  - [x] 5.2.1 The grade is mapped one to one, texts included.
  - [x] 5.2.2 Every engine `TrustReasonCode` has a `core` counterpart of the same name, tested over `entries`.
  - [x] 5.2.3 Counts and violations are unchanged.
- [x] 5.3 Failing tests for `EvaluatingLicenseReportGenerator`: the target's labels reach the grader; the summary carries the grade.
- [x] 5.4 Implement:
  - [x] 5.4.1 The `TrustGrader` bean. detekt's `TooManyFunctions` stopped it joining `LicenseServiceConfig`; it and `LicenseEvaluator` moved to a new `AssessmentConfig`: the steps that read no system and take no setting, so neither can fail the start (added on review).
  - [x] 5.4.2 `EvaluatingLicenseReportGenerator`: grade after evaluate.
  - [x] 5.4.3 `SummaryMapper`: map the grade.
- [x] 5.5 Failing tests for `BuildLog`, then implement:
  - [x] 5.5.1 `LOW`: the level, each reason line, files below `HIGH`, all warnings.
  - [x] 5.5.2 `HIGH` with no reasons: one line, not a warning.
  - [x] 5.5.3 `MEDIUM`: lines are not warnings. The "not computed" line is removed.
- [x] 5.6 Confirm `:core`, `:client`, `:license-service` and `:license-runner` tests are green. `:client:test` 9, `:license-service:test` 96, `:license-runner:test` 40, all green; `:core:build`, `:client:build`, `ktlintCheck` and `detekt` of the server and runner clean. The runner's exit code reads neither `trust` nor `violations`.

## 6. Parity (Decision 10)

- [x] 6.1 `:license-evaluation`:
  - add `java-test-fixtures`;
  - move `EvaluationParityView` and `EvaluationParityDiff` into `testFixtures`, unchanged.

  Its `parityTest` and unit tests still compile and pass. Confirmed: `:license-evaluation:check` green, 41 tests, `parityTest` compiles.
- [x] 6.2 Failing tests for `TrustParityView` and `TrustParityDiff` (unit, prototype JSON fixtures):
  - [x] 6.2.1 `UNKNOWN_LICENCES` and "licence" read in the service's spelling.
  - [x] 6.2.2 Reasons compared as a set; levels and file maps exactly.
  - [x] 6.2.3 Files with an empty prototype `level` (not selected) are left out on both sides.
  - [x] 6.2.4 A prototype component with `error`, or without `version`, is skipped and named.
  - [x] 6.2.5 Confirm: `TrustParityTest` 4 green; the view and diff live in `:license-trust`'s test sources, as nothing else reads them.
- [x] 6.3 `:license-trust`: `parityTest` source set and task, outside `check`, with Kover disabled for it. `PrototypeParityTest` runs resolve, scan, evaluate, grade, and reports the trust column and "follows from evaluation".
- [x] 6.4 Run the prototype locally, then `./gradlew :license-trust:parityTest`. Record:
  - the run directory, the registry commit and the component count;
  - the components that differ, and why;
  - the reference components named in the ticket, explicitly.
  - [x] 6.4.1 The six components of the evaluation's targeted run, prototype at `e380518` run just before, license registry `32f0966`. Trust is the same on all six: level, every reason and every file's level and codes. No "follows from evaluation" line: none of the six has a policy-rule or deliberate difference. Both reference components are `HIGH` on both sides.
  - [x] 6.4.2 Two components failed in the scan, not in trust. Artifactory's checksum lookup for seven of our jars in one component and one in another answered differently from one minute to the next. In the first run the prototype found a stored copy and named them while the service did not; in a rerun of those two a few minutes later it was the other way round; asked directly in between, Artifactory had no copy under either token. One of the two was the same on the rerun. Trust was the same in both runs regardless. The algorithms agree; the parity check reads a moving Artifactory, as the evaluation's 6.4.2a saw with Xray.
  - [ ] 6.4.3 The full run over every external component: optional, before rollout, as in the evaluation's 6.4.3.

## 6a. Group the module by role (added on review)

- [x] 6a.1 `impl/` grouped as the inventory's is: `shared/` (`GradingSubject`, `IdentificationShare`), `component/` (`ComponentRule`, the new `ComponentGrader`) with the five rules under `component/rule/`, and `file/` (`FileGrader`). Tests moved with their classes. `GradingSubject` and `ComponentRule` stay under `impl/` and `internal`: outside it is the module's public surface, and no caller needs them.
- [x] 6a.2 `ComponentGrader` takes the rule order and the duplicate removal out of `TrustGrader`, which now only wires the subject and the two graders. `ComponentGraderTest`: the order and the duplicates; the duplicates test moved there from `TrustGraderTest`.
- [x] 6a.3 `PackageDependencyTest`: `component` and `file` never import each other; `shared` imports neither.
- [x] 6a.4 Confirm: `:license-trust:check` green, 100 tests; `parityTest` compiles; the server, which uses only `TrustGrader` and `model`, compiles unchanged.

## 6b. Hold the seams with the scan and the evaluation (added on review)

- [x] 6b.1 Checked against the scan's and the evaluation's changes: trust reads "third party", `UNIDENTIFIED`, the package types, `READ` and its evidence, and the findings exactly as they define them; nothing contradicts them.
- [x] 6b.2 `EvaluatedTrustTest`: the real `LicenseEvaluator`, then `TrustGrader`, with a `python` label. Python only the image scan found is skipped, so `ECOSYSTEM_NOT_DETECTED`; Python declared in an SBOM or a build record is counted, so no reason. 2 green; recorded in design Decision 4.
- [x] 6b.3 Design risk: a regular build's `CHECK` report does not wait for Xray, so a freshly uploaded file is often `BUILD_LIST_ONLY` (LOW). Accepted, with where the fix would go if it turns out to be noise.

## 6c. Merge the evaluation's review changes (added on review)

- [x] 6c.1 Merged the latest evaluation branch, which brought the scan's and the evaluation's review fixes and `PARITY_ACCEPTED`. No textual conflict.
- [x] 6c.2 Two follow-ups: the trust parity check reads `PARITY_ACCEPTED` as the evaluation's does, and passes the resolver its new lookup timeout; `LicenseReportServiceImplTest`, new on that branch, built a summary with `trust = null`, which the contract no longer allows, so it uses a `HIGH` trust with no reasons.
- [x] 6c.3 Confirm: `./gradlew build qualityCheck` green on the merged branch, 661 tests, 0 skipped.

## 7. Finalization

- [x] 7.1 Run `./gradlew build qualityCheck` and confirm it is green. Green, every module.
- [x] 7.2 Update `docs/architecture.md`:
  - the trust module in the diagram and the table, with its rule;
  - the request flow (grade after evaluate);
  - the `SummaryMapper` sentence.
- [x] 7.3 Update the `CLAUDE.md` module list and the parity command (`:license-trust:parityTest`). Also the module table in `README.md`.
- [x] 7.4 Check the out-of-scope boundaries by grep:
  - no `ERROR` or `NEVER_BUILT` code;
  - no per-file counts in `core`;
  - no trust metric;
  - the runner's exit code reads neither `trust` nor `violations`: report only;
  - no `native-builds` setting and no build name in the code.

  Confirmed by grep over `engine/trust`, `core`, `server` and `runner`: none of the five found.
- [x] 7.5 Check that the risks in `design.md` still hold. Any rule found odd during parity goes to TD-002 or a new `TD-NNN`, never a `TODO`. All five risks stand as written. Parity found no odd trust rule; the moving checksum lookup of 6.4.2 is the parity check's own risk ("parity depends on a fresh prototype run", evaluation design), not a rule to revisit.
