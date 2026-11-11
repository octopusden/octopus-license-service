## Purpose

What a finished license report's summary says about the libraries of a component version, and how a generation fails when the systems it reads cannot be asked. Trust levels and the license files are outside this spec.

## ADDED Requirements

### Requirement: A generation scans and evaluates the component version

The service SHALL generate a report by scanning the license target's released files, then evaluating the inventory with the license policy current when the report started.

#### Scenario: Report of a scanned component

- **WHEN** a report for `sample-portal:2.4.0` finishes
- **THEN** its summary is built from the scan of `sample-portal:2.4.0` and the policy commit the report started with

### Requirement: The summary counts third-party libraries per verdict

The summary's `libraryCounts` SHALL hold every verdict (`ALLOWED`, `NOT_ALLOWED`, `UNKNOWN`, `UNIDENTIFIED`) with the number of counted libraries of that verdict that are not ours, zero included. Internal libraries and skipped packages SHALL NOT be counted.

#### Scenario: Counts

- **WHEN** the assessment holds three `ALLOWED`, one `UNKNOWN`, one `UNIDENTIFIED` and one `INTERNAL` library
- **THEN** `libraryCounts` is `ALLOWED 3, NOT_ALLOWED 0, UNKNOWN 1, UNIDENTIFIED 1`

#### Scenario: No library

- **WHEN** the assessment holds no counted library
- **THEN** every verdict is counted 0

### Requirement: The summary lists every third-party library that is not allowed

The summary's `violations` SHALL list each counted library that is not ours and whose verdict is not `ALLOWED`, in the assessment's order, with its id as written by the inventory, its verdict, its canonical license names and the entries of the released files it was found in, sorted.

#### Scenario: Unknown license

- **WHEN** `pkg:npm/left-pad@1.3.0` is `UNKNOWN` and was found in `org.example.portal:sample-portal:zip`
- **THEN** a violation lists `pkg:npm/left-pad@1.3.0`, `UNKNOWN`, no license, and that file

#### Scenario: Unidentified file

- **WHEN** a checksum-only file is `UNIDENTIFIED`
- **THEN** it is listed as a violation

#### Scenario: Internal library

- **WHEN** a library is `INTERNAL`
- **THEN** it is not listed as a violation

### Requirement: Trust is not computed yet

The summary's `trust` SHALL be null.

#### Scenario: Finished report

- **WHEN** a report finishes
- **THEN** its summary's `trust` is null

### Requirement: An unreachable Artifactory or Xray fails the report

The service SHALL finish a report as `FAILED`, with the scan's message as its error, when Artifactory or Xray cannot be asked during the scan. No summary and no license files SHALL be kept for it.

#### Scenario: Xray down

- **WHEN** Xray answers every try with a server error during a scan
- **THEN** the report is `FAILED` and its error names Xray

### Requirement: Every scan is counted by its outcome

The service SHALL count each scan in `license.inventory.scans`, tagged `outcome`: `scanned` when it gave an inventory, `unavailable` when Artifactory or Xray could not be asked, `failed` for any other error.

#### Scenario: Unavailable

- **WHEN** a scan fails because Artifactory cannot be asked
- **THEN** `license.inventory.scans{outcome="unavailable"}` increases by one

### Requirement: The systems read are configured under the keys every service shares

The service SHALL read the components registry from `components-registry-service.url`, Bitbucket from `bitbucket.host` and `bitbucket.token`, and Artifactory and Xray from `artifactory.url` and `artifactory.token`. It SHALL NOT start without any of them, or without at least one internal group and one own repository prefix; the startup error SHALL name the missing setting.

#### Scenario: Missing token

- **WHEN** `artifactory.token` is not set
- **THEN** the service does not start and the error names `artifactory.token`

#### Scenario: Shared URL

- **WHEN** Spring Cloud Config's `application.yml` sets `artifactory.url` for every service
- **THEN** the license service scans through that URL, with nothing set under `license-service`

#### Scenario: Missing internal groups

- **WHEN** `license-service.ownership.internal-groups` is empty
- **THEN** the service does not start and the error names it
