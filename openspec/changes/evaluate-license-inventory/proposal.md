## Why

The service can now list every library a component version ships (`license-inventory`), and it can judge one named library (`license-policy-registry`). Nothing connects the two: the server never calls the scan, and every report still answers zero libraries and no violations.

The missing step is stage 11 of the prototype (`xray_licence_scan.py`, lines 840–871): drop what is not a library of ours to account for (docker layers, OS packages, base-image Python), mark our own libraries, and judge the rest against the license policy. Run 14 of the prototype is the baseline for the rollout, so this step must give **the same verdict for the same inventory and the same registry**, as the inventory already gives the same list.

This change ports that step, wires the scan and the evaluation into the server, and fills the report's counts and violations. It is phase 3 of the component design; trust levels (phase 4) and the license files come in their own changes.

## What Changes

**Part A: evaluation, a new pure module (prototype stage 11)**

- New `engine/evaluation` (`:license-evaluation`): `LicenseEvaluator.evaluate(inventory, policy)` gives a `LicenseAssessment`. It reads no system; the inventory and the policy are pushed in.
- Docker layers (`docker` packages) are neither counted nor listed.
- OS packages (`rpm`, `deb`, `alpine`), and Python packages found only in images, are skipped: counted per released file and in total, not judged.
- Every other library gets one verdict, first match wins:
  - ours → `INTERNAL`;
  - a file known only by its checksum → `UNIDENTIFIED`;
  - otherwise `LicensePolicy.judge(licenses, coordinates)`: `ALLOWED`, `NOT_ALLOWED` or `UNKNOWN`, with the registry's override applied to Maven libraries.
- The assessment keeps the inventory, so grading (phase 4) reads the files, findings and verdicts from one value.

**Part B: the inventory records what is ours**

- `FoundLibrary` gains `ours`. "Ours" needs Artifactory (a checksum-only file stored in one of our repositories), so it is decided in the scan, where `Ownership` and the lookup already are; evaluation only reads it.
- The rule is the prototype's `is_internal`, unchanged: a Maven group starting with an internal group; a checksum-only file whose name starts with an internal group and `.`, or whose checksum is stored in one of our repositories.

**Part C: the server generates a real report**

- `EvaluatingLicenseReportGenerator` replaces `EmptyLicenseReportGenerator`: scan, evaluate, map into the `core` summary.
- The summary counts third-party libraries per verdict and lists every third-party library that is not `ALLOWED`, with its licenses and the released files it ships in. Trust stays `null` until grading.
- Artifactory or Xray unreachable fails the report (`FAILED`), as the prototype's component ended in `ERROR`. Each scan is counted in `license.inventory.scans{outcome}`.
- New settings: the JFrog URL and token (Vault), the internal groups and our repository prefixes (Spring Cloud Config), the Xray wait and poll interval (defaults 5 min and 10 s).

**Part D: parity, end to end**

- `./gradlew :license-evaluation:parityTest` resolves, scans and evaluates each component of a fresh prototype run against the real systems and the real license registry, and reports every difference in the inventory and in the evaluation.
- The inventory's parity view moves to test fixtures of `:license-inventory`, so one run checks both phases.

### A note on parity of the verdicts

- The verdict comes from `LicensePolicy.judge`, which follows the license plugins' rules, not the prototype's `verdict()`; the two differ in three small ways (design, Decision 6).
- Re-judging run 14's 49,767 judged libraries with the service's rules against the same registry gives one different verdict (`pkg:maven/jtidy/jtidy@r872-jdk5`, a license name the registry spells in two casings). The service keeps one judging rule for every caller instead of a second, prototype-only one.
- License names in the report are the policy's canonical spelling; the prototype printed them as found. Parity compares the verdict, whether an override applied, and the licenses as found, which together decide the names.

## Affected areas

- New module `engine/evaluation` (`:license-evaluation`): depends on `:license-inventory` and `:license-policy-registry`; never on `core`, `server`, Spring or a client.
- `:license-inventory`: `FoundLibrary.ours` (additive); `Ownership` answers for a package id; `ChecksumNamer` records the own-repository answer it already asks for. Its parity view and diff move to `testFixtures`.
- `server`: `EvaluatingLicenseReportGenerator` and `SummaryMapper`; `EmptyLicenseReportGenerator` is removed; new properties and beans; the `license.inventory.scans` counter; depends on `:license-inventory` and `:license-evaluation`.
- `docs/architecture.md`: the evaluation module, the request flow and the scan counter.
- **Behaviour change for callers:**
  - `DONE` reports now carry real `libraryCounts` and `violations`; before, they were always empty.
  - A report can now end `FAILED` because Artifactory or Xray is unreachable.
  - A report takes as long as the scan: seconds to minutes, up to 5 minutes more while Xray finishes scanning.
  - The runner and the TeamCity step stay report-only, so no build fails on a verdict.
- **Rollout:** the service needs the new JFrog and ownership settings in Spring Cloud Config and Vault before it starts; without them it does not start.

## Out of scope

- Trust levels and their reasons (phase 4, `assess_confidence`): a change of its own; `trust` stays `null`.
- The license files in the zip (`THIRD-PARTY.txt`, `licenses.xml`, texts): a change of its own; the zip stays empty.
- Per-file counts in the summary (third party, allowed, hash only per released file): grading reads them from the assessment; the wire contract does not carry them yet.
- The prototype's `only_in_build_info`, `only_in_xray` and `registered_artifacts` columns: grading inputs, not verdicts.
- Changing a verdict rule of the license policy: the policy already decides it, and a change there moves every caller.
- Revisiting the scan's odd rules: still TD-002, after parity.
