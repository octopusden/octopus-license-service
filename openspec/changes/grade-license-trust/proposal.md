## Why

A report now lists the libraries of a component version and judges each one, but it does not say whether that list is complete. A list can be clean because nothing is wrong, or because nothing could be read. Some examples:

- a C/C++ binary whose libraries are compiled in;
- a file Xray never indexed;
- a registry entry that points at nothing.

Today a caller cannot tell these cases apart, and `trust` is always `null`.

The prototype answers this in stage 12 (`xray_licence_scan.py`, `assess_confidence()` and `assess_artifacts()`, lines 875–1080). It grades each component and each released file `HIGH`, `MEDIUM`, `LOW` or `NONE`, gives the reasons, and gives a recommended fix for each reason. Run 14 is the rollout's baseline. The inventory and the evaluation already give the same results as that run, and grading must give **the same level and the same reasons for the same assessment**.

This change ports stage 12 and serves its result in the summary. It is phase 4 of the component design. The license files come in a change of their own.

## What Changes

**Part A: trust, a new pure module (prototype stage 12)**

- New `engine/trust` (`:license-trust`): `TrustGrader.grade(assessment, labels)` returns a `TrustGrade`. It reads no system: the assessment and the component's registry labels are pushed in.
- A `TrustGrade` has:
  - the component's level, which is the lowest level among its reasons, or `HIGH` when there are none;
  - the reasons, each with a code and a detail;
  - one `FileTrust` per selected released file, holding that file's level and its reason codes.
- Each reason code has a fixed level, title, meaning and fix, the prototype's `REASONS` table. A reason's level is never computed anywhere else.
- The component rules run in the prototype's order:
  1. **Coverage:** nothing could be read, so the reasons come from the scan's findings; or only some of the released files could be read.
  2. **Empty content:** files were read, but they hold no third-party library.
  3. **Ecosystems:** a language the registry labels is missing, or a package type cannot be listed completely without an SBOM.
  4. **Sources:** a read file has no build record, or only its build record could see inside it.
  5. **Identification:** the share of third-party libraries known only by checksum, or with no known license, reaches its threshold.
- The file rules are the prototype's `assess_artifacts`. They use the same identification thresholds as the component rules, from one place.

**Part B: the summary and the runner carry trust**

- `core`:
  - `Trust` gains `files`;
  - `TrustReason` becomes `code`, `level`, `title`, `detail`, `meaning`, `fix`;
  - new `FileTrust` and `TrustReasonCode`;
  - a finished report's `trust` is never null.
- `server`: `EvaluatingLicenseReportGenerator` grades after it evaluates, using the target's labels, and `SummaryMapper` maps the grade.
- `runner`: logs the level, then each reason with its detail and fix, then each released file graded below `HIGH`. `LOW` and `NONE` are warnings, never failures.

**Part C: parity**

- `./gradlew :license-trust:parityTest` resolves, scans, evaluates and grades each component of a fresh prototype run.
- For each component it compares the inventory and the evaluation, as before, plus the level, the reasons, and each file's level and reason codes.
- The evaluation's parity view moves to the test fixtures of `:license-evaluation`, the way the inventory's moved.

### A note on parity of the grade

- Grading reads only the assessment and the labels. When the assessment is the same as the prototype's, the grade is the same.
- Two known differences in the assessment come from earlier changes and are already listed as such in the parity report:
  - "policy rules": the evaluation design's Decision 6;
  - "deliberate": the image Python the build declared, from the evaluation design's Decision 3.3.

  Either difference can move a count that grading reads. On a component that has one of them, a trust difference is listed under that cause, not as a failure (Decision 10).
- The prototype's `UNKNOWN_LICENCES` code and its "licence" wording are spelled "license" in the service. Parity reads the prototype's spelling as the service's.

## Affected areas

- **New module `engine/trust` (`:license-trust`):** depends on `:license-evaluation`, and through it on the inventory's model. It never depends on `core`, `server`, Spring or a client.
- **`:license-evaluation`:** gets `java-test-fixtures`, and `EvaluationParityView` and `EvaluationParityDiff` move into them, unchanged. Its own `parityTest` keeps working.
- **`core`:** `Trust`, `TrustReason`, `FileTrust`, `TrustReasonCode`. `LicenseReportSummary.trust` is no longer nullable.
- **`server`:**
  - `EvaluatingLicenseReportGenerator` and `SummaryMapper`;
  - the `TrustGrader` bean;
  - depends on `:license-trust`.
- **`runner`:** `BuildLog`.
- **`docs/architecture.md`:** the trust module and the request flow.
- **Behaviour change for callers:**
  - `trust` is filled on every `DONE` report. `TrustReason.reason` is gone; its text is now `title` and `detail`. No caller reads `trust` yet, because it was always null.
  - The runner prints the reasons and the files below `HIGH`. A build still never fails on trust.
- **Rollout:** nothing is required. Grading needs no setting.

## Out of scope

- **Revisiting the prototype's thresholds, levels or texts:** this is a parity port, and a change to the rules is its own story (TD-002 covers the scan's rules; trust rules join it if they need revisiting).
- **The prototype's `ERROR` and `NEVER_BUILT` reasons:** the service can never produce them. A failed scan ends the report `FAILED` with no summary, and a caller always names the version.
- **The prototype's per-file counts in `artifacts.csv`** (`third_party`, `allowed`, `hash_only`, `build_info_only`, `types`): grading computes what it needs, and the wire carries the levels and reasons only.
- **A trust metric** (`license.reports.trust{level}`): nobody has asked for one yet.
- **Failing a build, on trust or on a license violation:** every step only reports until enforcement is switched on per component in its own change. Trust never fails a build, even then: the build outcome is the runner's decision, made from the verdicts.
- **The license files:** a change of their own.
