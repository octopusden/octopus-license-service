## Purpose

When the license runner fails a build. Only the runner's exit code is governed here; the service never fails a build and has no notion of one.

## ADDED Requirements

### Requirement: Every fail flag is off unless given

The runner SHALL accept `--fail-on-not-allowed`, `--fail-on-unknown`, `--fail-on-unidentified` and `--fail-on-disagreement`, each off unless given. With none given it SHALL exit 0 whatever the outcome.

#### Scenario: No flag

- **WHEN** a report lists a `NOT_ALLOWED` and an `UNKNOWN` library and no flag is given
- **THEN** the runner warns about both and exits 0

#### Scenario: No flag and no answer

- **WHEN** the service is unavailable and no flag is given
- **THEN** the runner warns and exits 0

### Requirement: A violation fails the build only when its flag is on

With a flag on, the runner SHALL exit non-zero when the summary's violations hold a library of that flag's verdict, and SHALL name each such library in an error line. A violation of a verdict whose flag is off SHALL only be warned about.

#### Scenario: Unknown license, flag on

- **WHEN** `--fail-on-unknown` is given and `com.example:foo-core:1.0` is `UNKNOWN`
- **THEN** the runner prints that `com.example:foo-core:1.0` fails the build for an unknown license and exits non-zero

#### Scenario: Not allowed, its flag off

- **WHEN** only `--fail-on-unknown` is given and the only violation is `NOT_ALLOWED`
- **THEN** the runner warns and exits 0

#### Scenario: Unidentified, flag on

- **WHEN** `--fail-on-unidentified` is given and a checksum-only file is `UNIDENTIFIED`
- **THEN** the runner exits non-zero

#### Scenario: Low trust, flags on

- **WHEN** every flag is given, trust is `LOW` and there is no violation
- **THEN** the runner exits 0

### Requirement: No answer fails a full report's build when any flag is on

For a `FULL` report, with any flag on, the runner SHALL exit non-zero when it has no verdicts to judge by: the service is unavailable, the report is `FAILED`, the wait timed out, a `DONE` report has no summary, its trust is `NONE` (no released file read), or the runner itself failed. Arguments the runner cannot read SHALL exit non-zero when they hold a `--fail-on-*` flag and no bare `--check-only`; `--check-only=false` is not a check.

#### Scenario: Nothing read on a release

- **WHEN** `--fail-on-unknown` is given without `--check-only` and the report is `DONE` with trust `NONE`
- **THEN** the runner exits non-zero

#### Scenario: A typo next to a fail flag

- **WHEN** the arguments are `--fail-on-unknown --timeout forever`
- **THEN** the runner warns that it did not run and exits non-zero

#### Scenario: Service down on a release

- **WHEN** `--fail-on-unknown` is given without `--check-only` and the service cannot be reached
- **THEN** the runner exits non-zero

#### Scenario: Report failed on a release

- **WHEN** `--fail-on-not-allowed` is given without `--check-only` and the report ends `FAILED`
- **THEN** the runner exits non-zero

### Requirement: A check fails only on what Xray has seen

For a `CHECK` report (`--check-only`), with any flag on:

- no answer SHALL pass with a warning: the service is unavailable, the report is `FAILED`, the wait timed out, a `DONE` report has no summary or trust `NONE`, the runner itself failed, or its arguments could not be read;
- a violation marked `preliminary` SHALL be warned about and SHALL NOT fail the build;
- any other violation SHALL follow its flag as in a `FULL` report.

The service SHALL mark a violation of a `CHECK` report `preliminary` exactly when no released file it is found in was read by Xray's binary scan; in a `FULL` report it SHALL never mark one.

#### Scenario: Xray down on a regular build

- **WHEN** `--check-only --fail-on-unknown` is given and the report ends `FAILED` because Xray cannot be reached
- **THEN** the runner warns and exits 0

#### Scenario: Fresh upload not scanned yet

- **WHEN** `--check-only --fail-on-unknown` is given and the only `UNKNOWN` library is found in a file Xray has not indexed yet, through its build record
- **THEN** the violation is `preliminary`, the runner warns and exits 0

#### Scenario: Scanned file with an unknown license

- **WHEN** `--check-only --fail-on-unknown` is given and an `UNKNOWN` library is found in a file Xray has scanned
- **THEN** the runner exits non-zero

#### Scenario: Full report never preliminary

- **WHEN** a `FULL` report lists a violation found only in a file Xray could not read
- **THEN** the violation is not `preliminary` and follows its flag

### Requirement: An excluded component never fails

The runner SHALL exit 0 for an `EXCLUDED` report, whatever flags are given.

#### Scenario: Excluded

- **WHEN** every flag is given and the report is `EXCLUDED`
- **THEN** the runner exits 0

### Requirement: The output is written before the build fails

The runner SHALL write the report and the license files to the output directory before it exits non-zero.

#### Scenario: Failing build keeps its files

- **WHEN** `--fail-on-unknown` fails the build on a `DONE` report
- **THEN** `license-report.json` and the zip are in the output directory

### Requirement: A disagreement fails the build only with its flag

With `--fail-on-disagreement`, the runner SHALL exit non-zero when the summary lists a license disagreement, in a `FULL` and in a `CHECK` report, and SHALL name each such library. Without it, a disagreement SHALL only be warned about.

#### Scenario: GPL against MIT

- **WHEN** `--fail-on-disagreement` is given and `pkg:npm/left-pad@1.3.0` is `ALLOWED` with Xray naming `GPL-3.0` and the SBOM `MIT`
- **THEN** the runner names it and exits non-zero
