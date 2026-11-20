## Purpose

When a library's license sources disagree in a way that matters, and what that changes. The verdict itself is the `license-policy` spec's.

## ADDED Requirements

### Requirement: The scan keeps each source's license names

The inventory SHALL keep, for each library, the license names each source gave: Xray's binary scan, an SBOM, Xray's license database. The joined names SHALL stay the union of them.

#### Scenario: Two sources

- **WHEN** Xray's scan names `GPL-3.0` and an SBOM names `MIT` for one library
- **THEN** the library holds `BINARY_SCAN: GPL-3.0` and `SBOM: MIT`, and its joined names are both

### Requirement: A verdict that depends on the source is reported, never changed

Evaluation SHALL mark a third-party library whose sources' names, each judged on their own, do not all get the same verdict, with each source's canonical names. It SHALL NOT mark one whose names were replaced by an override, our own library, or a file nobody could name. The library's verdict SHALL stay the one on all its names together.

#### Scenario: Copyleft against permissive

- **WHEN** Xray's scan names `GPL-3.0` and an SBOM names `MIT`, and only `MIT` is allowed
- **THEN** the verdict is `ALLOWED` and the library is marked, with both sources' names

#### Scenario: Two names for one license

- **WHEN** one source names `Apache-2.0` and the other a synonym the registry merges into it
- **THEN** the library is not marked

#### Scenario: Two allowed licenses

- **WHEN** one source names `Apache-2.0` and the other `MIT`, both allowed
- **THEN** the library is not marked

### Requirement: The summary and the runner show each disagreement

A `DONE` report's summary SHALL list each marked third-party library in `licenseDisagreements`, with its verdict, each source's names and its released files. The runner SHALL print one warning for each, and its exit code SHALL NOT depend on them.

#### Scenario: One disagreement

- **WHEN** `pkg:npm/left-pad@1.3.0` is marked
- **THEN** the runner prints `WARNING: pkg:npm/left-pad@1.3.0 is ALLOWED, but its sources disagree: BINARY_SCAN (GPL-3.0), SBOM (MIT) in app.jar` and its exit code is unchanged
