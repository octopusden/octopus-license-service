## Purpose

How far the library list of a component version, and of each released file, can be trusted to be complete: its level, its reasons and their fixes. Trust never judges a license and never fails a build.

## ADDED Requirements

### Requirement: Every reason code has one level, title, meaning and fix

Two things are graded: the component version as a whole, and each released file on its own. They share one set of reason codes. Each code SHALL have the same level, title, meaning and fix wherever it is raised.

Some codes are raised only for the component, and none only for a file. The table shows where each code is raised and what triggers it there.

| Code | Level | Raised for the component when | Raised for a file when |
| --- | --- | --- | --- |
| `NO_ARTIFACT_IN_REGISTRY` | `NONE` | no file is covered, and the scan found `NO_DISTRIBUTION` or `UNSUPPORTED_DISTRIBUTION_ENTRY` | the file is `UNSUPPORTED` |
| `ARTIFACT_NOT_FOUND` | `NONE` | no file is covered, and the scan found `ARTIFACT_NOT_FOUND` | the file is `NOT_FOUND` |
| `NOT_SCANNABLE` | `NONE` | no file is covered, and the scan found `NOT_IN_XRAY` or `NO_BUILD_INFO`, or found none of the codes above | the file is `NOT_READABLE` |
| `PARTIAL_ARTIFACTS` | `LOW` | some files are covered, but not all | — |
| `NO_THIRD_PARTY_FOUND` | `LOW` | files were read, and the component has no third-party library at all | the file was read and has no third-party library in it |
| `ECOSYSTEM_NOT_DETECTED` | `LOW` | a language label has no library of its kind | — |
| `FULL_LIST_UNAVAILABLE` | `LOW` | a type only an SBOM can list completely was found, and no SBOM was seen | — |
| `BUILD_LIST_ONLY` | `LOW` | some read file was not seen by the binary scanner | the file was read without a binary scan |
| `SBOM_UNREADABLE` | `LOW` | the scan could not read an SBOM the build published | — |
| `UNIDENTIFIED_BINARIES` | `MEDIUM` | the share of checksum-only libraries crosses the threshold, among all third-party libraries | the same, among the file's own third-party libraries |
| `UNKNOWN_LICENSES` | `MEDIUM` | the share of libraries with no known license crosses the threshold, among all third-party libraries | the same, among the file's own third-party libraries |
| `NO_BUILD_RECORD` | `MEDIUM` | some read file has no build record | the file was read without a build record |

"Covered" means read, or a plain file.

#### Scenario: A reason's level comes from its code

- **WHEN** a component is given `NO_BUILD_RECORD`
- **THEN** that reason's level is `MEDIUM`, and its fix says to publish build-info for the build that produces the file

### Requirement: The component's level is its lowest reason

A component version's trust level SHALL be the lowest level among its reasons, in the order `NONE` < `LOW` < `MEDIUM` < `HIGH`, or `HIGH` when it has no reason. A released file's level SHALL NOT lower the component's level.

#### Scenario: No reason

- **WHEN** no rule gives a reason
- **THEN** the level is `HIGH` with no reasons

#### Scenario: Mixed reasons

- **WHEN** the reasons are `PARTIAL_ARTIFACTS` and `UNKNOWN_LICENSES`
- **THEN** the level is `LOW`

#### Scenario: A file at NONE

- **WHEN** one declared file is `NOT_FOUND`, the other three are covered, and no other reason applies
- **THEN** that file is `NONE` and the component is `LOW` (`PARTIAL_ARTIFACTS`)

### Requirement: File reasons do not roll up into the component

The component's reasons SHALL come only from the component rules over the whole assessment. They SHALL NOT be copied from the files' reasons. The same fact can raise a reason at both levels, but differently. For each code a file can have, the component shows this:

| A file has | The component shows |
| --- | --- |
| `NO_ARTIFACT_IN_REGISTRY`, `ARTIFACT_NOT_FOUND` or `NOT_SCANNABLE` | `PARTIAL_ARTIFACTS` when another file is covered; otherwise, the same code from the scan's findings |
| `BUILD_LIST_ONLY` | `BUILD_LIST_ONLY`, with that file among its entries |
| `NO_BUILD_RECORD` | `NO_BUILD_RECORD`, with that file among its entries |
| `NO_THIRD_PARTY_FOUND` | `NO_THIRD_PARTY_FOUND` only if no other file has a third-party library either |
| `UNIDENTIFIED_BINARIES` or `UNKNOWN_LICENSES` | the same code only if the threshold is crossed over all third-party libraries; a file can cross it while the component does not, and the other way round |

The component's own-only codes (`PARTIAL_ARTIFACTS`, `ECOSYSTEM_NOT_DETECTED`, `FULL_LIST_UNAVAILABLE`) are never given to a file.

#### Scenario: Image without third-party libraries

- **WHEN** an image holds only OS packages and the zip beside it holds 42 third-party libraries
- **THEN** the image has `NO_THIRD_PARTY_FOUND` and the component does not

#### Scenario: Unknown licenses concentrated in one file

- **WHEN** one file has 5 `UNKNOWN` of its 20 third-party libraries, and the component has 5 of 200
- **THEN** that file has `UNKNOWN_LICENSES` and the component does not

### Requirement: Reasons follow the rule order, each once

Reasons SHALL be given in the order coverage, empty content, ecosystems, sources, identification. The same code with the same detail SHALL appear once.

#### Scenario: Same finding twice

- **WHEN** nothing is covered and two findings map to `NOT_SCANNABLE` with the same subject and detail
- **THEN** one `NOT_SCANNABLE` reason is given

### Requirement: Nothing covered is graded from the scan's findings

When no selected file was read or is a plain file, the component SHALL get one reason per finding of the scan, in the order found:

- `NO_DISTRIBUTION` and `UNSUPPORTED_DISTRIBUTION_ENTRY` give `NO_ARTIFACT_IN_REGISTRY`;
- `ARTIFACT_NOT_FOUND` gives `ARTIFACT_NOT_FOUND`;
- `NOT_IN_XRAY` and `NO_BUILD_INFO` give `NOT_SCANNABLE`.

Each reason's detail SHALL be `"{subject}: {detail}"`. With no such finding, the component SHALL get `NOT_SCANNABLE` with the detail "no registered artifact could be scanned".

#### Scenario: No distribution

- **WHEN** the scan found `NO_DISTRIBUTION` for `sample-portal` and selected no file
- **THEN** the level is `NONE` with `NO_ARTIFACT_IN_REGISTRY`, detail `sample-portal: …` from the finding

#### Scenario: File unreadable

- **WHEN** the only selected file is `NOT_READABLE` with findings `NOT_IN_XRAY` and `NO_BUILD_INFO`
- **THEN** the level is `NONE` with two `NOT_SCANNABLE` reasons, one per finding

### Requirement: Partly covered is LOW

When at least one selected file is covered (read or a plain file) and fewer than all are, the component SHALL get `PARTIAL_ARTIFACTS` with the detail `"{covered} of {selected} registered artifacts checked"`.

#### Scenario: One file missing

- **WHEN** 3 of 4 selected files are covered
- **THEN** `PARTIAL_ARTIFACTS`, "3 of 4 registered artifacts checked"

#### Scenario: Plain file counts as covered

- **WHEN** the selected files are one read zip and one `PLAIN_FILE` yaml
- **THEN** no `PARTIAL_ARTIFACTS`

### Requirement: Read files with no third-party library are LOW

When at least one file was read and no counted library is third party, the component SHALL get `NO_THIRD_PARTY_FOUND` with the detail `"{read} file(s) checked, 0 outside libraries"`. The detail SHALL end with `" (C/C++ build)"` when the labels hold `c` or `cpp`.

#### Scenario: Only internal libraries

- **WHEN** two files were read and every counted library is `INTERNAL`
- **THEN** `NO_THIRD_PARTY_FOUND`, "2 file(s) checked, 0 outside libraries"

#### Scenario: C/C++ label

- **WHEN** the labels hold `c` and two files were read with no third-party library
- **THEN** the detail is "2 file(s) checked, 0 outside libraries (C/C++ build)"

### Requirement: A labelled language with no library of its kind is LOW

For each registry label that names a language (`javascript` and `typescript`: `npm`; `python`: `pypi`; `go`: `golang`; `dart`: `pub`; `swift` and `objective_c`: `cocoapods` or `swift`; `c` and `cpp`: `cpp` or `conan`; `cs`: `nuget`), when some file was read and no third-party library is of its types, the component SHALL get one `ECOSYSTEM_NOT_DETECTED`. Its detail SHALL be `"missing {types} (label {labels}), …; detected {type} {count}, …"`, with the detected types by count descending and ties in the order the types first appear.

#### Scenario: JavaScript missing

- **WHEN** the labels are `java` and `javascript`, and the third-party libraries are 40 `maven` and 2 `generic`
- **THEN** `ECOSYSTEM_NOT_DETECTED`, "missing npm (label javascript); detected maven 40, generic 2"

#### Scenario: Two labels, one ecosystem

- **WHEN** the labels are `objective_c` and `swift`, and no `cocoapods` or `swift` library is found
- **THEN** one missing entry, "cocoapods/swift (label objective_c swift)"

#### Scenario: Label without an ecosystem

- **WHEN** the only label is `java`
- **THEN** no `ECOSYSTEM_NOT_DETECTED`

### Requirement: A type only an SBOM lists completely is LOW without one

When a third-party library's type is not among `maven`, `generic`, `golang`, `npm`, `pypi`, `huggingfaceml`, `model`, `helm`, `docker`, `rpm`, `deb` and `alpine`, and no counted library was seen in an SBOM, the component SHALL get `FULL_LIST_UNAVAILABLE` with the detail `"found {types} libraries, but no SBOM"`, the types sorted.

#### Scenario: Dart without an SBOM

- **WHEN** third-party libraries of type `pub` and `cocoapods` were found and none came from an SBOM
- **THEN** `FULL_LIST_UNAVAILABLE`, "found cocoapods, pub libraries, but no SBOM"

#### Scenario: Dart with an SBOM

- **WHEN** the same libraries were found and an internal library came from an SBOM
- **THEN** no `FULL_LIST_UNAVAILABLE`

### Requirement: Read files missing a source lower the grade

A read file without a build record SHALL give the component `NO_BUILD_RECORD` (`"no build for: {entries}"`). A read file the binary scanner did not see SHALL give `BUILD_LIST_ONLY` (`"not indexed in Xray: {entries}"`). Entries are space-separated, in listing order.

#### Scenario: Image without build

- **WHEN** the image `sample/portal:2.4.0-118` was read by the binary scanner only
- **THEN** `NO_BUILD_RECORD`, "no build for: sample/portal:2.4.0-118"

#### Scenario: Not indexed

- **WHEN** a zip was read from its build record only
- **THEN** `BUILD_LIST_ONLY`, "not indexed in Xray: …"

### Requirement: An SBOM the scan could not read lowers the grade

Each `SBOM_UNREADABLE` finding of the scan SHALL give the component `SBOM_UNREADABLE` (`LOW`), with the detail `"{subject}: {detail}"`. No released file SHALL be graded for it: the finding names the SBOM, not a file.

#### Scenario: Unparsable SBOM

- **WHEN** the scan found `SBOM_UNREADABLE` for `rnd-maven-dev-local/org/example/portal/2.4.0/portal-2.4.0.cdx.json`, "Unexpected end-of-input"
- **THEN** the component has `SBOM_UNREADABLE` with that subject and detail, and is at most `LOW`

### Requirement: Too many unnamed or unlicensed libraries is MEDIUM

Among the third-party libraries:

- at least 10 `UNIDENTIFIED`, and at least 10 % of them, SHALL give `UNIDENTIFIED_BINARIES`: `"{n} of {third} outside files identified by checksum only"`;
- at least 5 `UNKNOWN`, and at least 10 % of them, SHALL give `UNKNOWN_LICENSES`: `"{n} of {third} outside libraries have no known license"`.

The same thresholds SHALL apply to each released file over its own third-party libraries.

#### Scenario: Above both thresholds

- **WHEN** 6 of 42 third-party libraries are `UNKNOWN`
- **THEN** `UNKNOWN_LICENSES`, "6 of 42 outside libraries have no known license"

#### Scenario: Count below the minimum

- **WHEN** 4 of 10 are `UNKNOWN`
- **THEN** no `UNKNOWN_LICENSES`

#### Scenario: Share below 10 %

- **WHEN** 12 of 200 are `UNIDENTIFIED`
- **THEN** no `UNIDENTIFIED_BINARIES`

### Requirement: Each selected released file is graded on its own

Each released file that is not `NOT_SELECTED` or `METADATA` SHALL get a level and reason codes, in this order:

1. By status: `UNSUPPORTED` gives `NO_ARTIFACT_IN_REGISTRY`, `NOT_FOUND` gives `ARTIFACT_NOT_FOUND`, `NOT_READABLE` gives `NOT_SCANNABLE`, and `READ` without a binary scan gives `BUILD_LIST_ONLY`.
2. For a `READ` file:
   - no build record gives `NO_BUILD_RECORD`;
   - no third-party library in it gives `NO_THIRD_PARTY_FOUND`;
   - the identification thresholds over its own third-party libraries give `UNIDENTIFIED_BINARIES` and `UNKNOWN_LICENSES`.

A `PLAIN_FILE` SHALL be `HIGH` with no codes. Files not selected SHALL NOT be graded.

#### Scenario: Image with OS packages only

- **WHEN** an image was read by the binary scanner only and holds only skipped OS packages
- **THEN** it is `LOW` with `NO_BUILD_RECORD`, `NO_THIRD_PARTY_FOUND`

#### Scenario: Plain file

- **WHEN** a `PLAIN_FILE` yaml is selected
- **THEN** it is `HIGH` with no codes

#### Scenario: Metadata

- **WHEN** a pom is `METADATA`
- **THEN** it has no file trust

### Requirement: Grading is the same as the prototype's

For the same assessment and labels, grading SHALL give the prototype's (run 14, `e380518`):

- level;
- reasons, as a set of codes and details;
- level and codes of each selected file.

The prototype's `UNKNOWN_LICENCES` is read as `UNKNOWN_LICENSES`, and "licence" as "license" in a detail.

#### Scenario: Parity run

- **WHEN** `./gradlew :license-trust:parityTest` runs right after a prototype run
- **THEN** no component differs in trust, except one that already has a "policy rules" or "deliberate" evaluation difference, where the trust difference is listed under it

#### Scenario: Reference components

- **WHEN** the parity run includes the reference components named in the ticket
- **THEN** they have the prototype's level, reasons and per-file levels
