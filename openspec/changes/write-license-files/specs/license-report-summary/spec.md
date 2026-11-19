## Purpose

What a finished license report's summary says about the license texts its files could not include. The files themselves are the `license-files` spec.

## ADDED Requirements

### Requirement: The summary lists every license text left out

A `DONE` report's summary SHALL carry `missingLicenseTexts`, never null: each used license whose text no folder holds, once, with its reason, `NO_REGISTRY_ENTRY` or `DOWNLOAD_FAILED`, and the URL for a failed download. It SHALL be empty when every text was written.

#### Scenario: Nothing missing

- **WHEN** every used license has a registry text
- **THEN** `missingLicenseTexts` is empty

#### Scenario: No registry entry

- **WHEN** a not-allowed library is under `SSPL-1.0`, which `licenses.properties` does not name
- **THEN** `missingLicenseTexts` holds `SSPL-1.0` with `NO_REGISTRY_ENTRY`

#### Scenario: Missing in two folders

- **WHEN** `GWT` fails to download and is used in two released files
- **THEN** `missingLicenseTexts` holds `GWT` once

### Requirement: The runner warns about missing texts and never fails on them

The runner SHALL log one warning per missing text with its license and reason. Its exit code SHALL NOT depend on `missingLicenseTexts`.

#### Scenario: A failed download

- **WHEN** the summary lists `GWT` as `DOWNLOAD_FAILED`
- **THEN** the runner prints `WARNING: License text of GWT missing: DOWNLOAD_FAILED (http://www.gwtproject.org/terms.html)` and exits as it would without it
