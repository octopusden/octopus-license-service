## Why

A finished report now judges every library and grades the list, but its zip is empty. A component that switches from the license plugins to the service loses the files it ships today:

- `THIRD-PARTY.txt`: one line per third-party library with its licenses;
- `licenses.xml`: the same list as XML;
- the text of each license used.

Today the plugins write these per Maven module, so each released war or jar carries its own set (a web application with three wars ships `WEB-INF/licenses/THIRD-PARTY.txt` in each). The service must write the same files, per released file, so a component can drop the plugin without changing what it ships.

## What Changes

**Part A: the license files, a new pure module**

- New `engine/files` (`:license-files`): `LicenseFilesWriter` turns an assessment, the license policy and the downloaded license texts into `LicenseFiles`. It reads no system.
- One folder per released file that was read. A library is in the folder of every released file it was found in.
- Each folder holds the plugin's three outputs:
  - `THIRD-PARTY.txt`, in the plugin template's layout;
  - `licenses.xml`, in the plugin's `licenseSummary` layout;
  - one text file per license used in that folder, named as the plugin names them.
- Every counted third-party library is listed, whatever its ecosystem or verdict. Our own libraries are left out, as the plugin's `excludedGroups` left them out. Skipped OS packages and base-image Python are not counted, so not listed.
- A line differs from the plugin's in two places only:
  - the artifactId stands for the POM's name, which the scan does not read;
  - the ` - url` part is dropped: no source gives a library's homepage, and the plugin's `no url defined` on every line would say nothing.
- A non-Maven library is written with its package id: `(MIT) react (pkg:npm/react@18.2.0)`.

**Part B: license texts at a URL**

- The registry gives most texts itself and four only as a URL (`H2-1.0`, `GWT`, `SBCLA`, `oswego-concurrent` today). The server downloads those before writing, as the plugin did.
- A download that fails, or a license with no registry entry at all, leaves that text out. The report still finishes; the summary lists the license and why.

**Part C: the zip, the summary and the runner**

- `server`: `EvaluatingLicenseReportGenerator` writes the files after grading and zips them; the empty zip is gone.
- `core`: the summary gains `missingLicenseTexts`.
- `runner`: warns once per missing text. It never fails a build on it.

**Part D: the report store**

- Now that the zip holds real files, the store also bounds how many finished reports it keeps (`license-service.max-stored-reports`), not only their bytes.
- The byte limit evicts only reports that hold files: dropping one without files freed nothing.

**Part E: failing the build, off by default**

- The runner gets three flags, one per violation, as the plugin's `failOnBlacklist` and `failOnMissing` did:
  - `--fail-on-not-allowed`: a library whose license the registry does not allow;
  - `--fail-on-unknown`: a library with no known license;
  - `--fail-on-unidentified`: a file nobody could name.
- Each is off unless given, so the first rollout stays green. A component switches one on through its TeamCity step.
- With any flag on, the runner exits non-zero when a library has a verdict whose flag is on; the log names each such library.
- When there is no answer to judge by (the service is unavailable, the report `FAILED` or timed out, or a `DONE` report has no summary):
  - RC and RELEASE (`FULL`) fail: nothing ships unchecked while failing is on;
  - a regular build (`--check-only`) passes with a warning: Xray being down must not break development.
- In a regular build, a violation fails only when Xray has already scanned a released file it is in. One found only in files Xray has not finished yet is warned about as preliminary. The server marks it: `Violation.preliminary`.
- An `EXCLUDED` report never fails. Trust and missing texts never fail.
- With every flag off, the runner exits 0 whatever happens, as today.

### A note on comparing with the plugin

- The files are not byte-identical to the plugin's: the name and URL differ by design, and the libraries are those the scan finds, which on the earlier comparison with the plugins' files was every plugin dependency plus the ones outside the Maven tree.
- The check is a side-by-side comparison per released file: every group:artifact:version the plugin listed is listed, with the same canonical licenses. It runs on the components the earlier comparison covered, starting with one that releases several wars.

## Affected areas

- **New module `engine/files` (`:license-files`):** depends on `:license-evaluation`, and through it on the inventory's and the policy's models. Never on `core`, `server`, Spring or a client.
- **`core`:** `LicenseReportSummary.missingLicenseTexts`, `MissingLicenseText`, `MissingTextReason`; `Violation.preliminary`.
- **`server`:**
  - `EvaluatingLicenseReportGenerator` writes the files;
  - new `LicenseTextDownloader` and `LicenseFilesZip`;
  - `SummaryMapper` maps the missing texts, and marks a `CHECK` report's violation preliminary when Xray has scanned none of its files;
  - `LicenseReportStore` and `LicenseServiceProperties`: `max-stored-reports`, and the byte limit's eviction;
  - new settings `license-service.license-texts.download-timeout`.
- **`runner`:** `BuildLog` warns about missing texts; `LicenseRunnerCommand` gets the three fail flags and a new `BuildOutcome` decides the exit code.
- **`docs/architecture.md`:** the files module, the request flow, the `SummaryMapper` sentence.
- **Behaviour change for callers:**
  - the download of a `DONE` report is no longer an empty zip;
  - the summary has a new field, `missingLicenseTexts`, always present, empty when every text was written;
  - the runner's exit code changes only for a step that passes a fail flag; with none, it still always exits 0.
- **Rollout:** the service needs outbound HTTP to the four licenses' hosts. Without it, those texts are listed as missing and nothing else changes.

## Out of scope

- **Byte-identical files:** the plugin's name and URL come from POMs; reading every POM is hundreds of calls per report for a cosmetic field.
- **A combined set for the whole version:** each released file carries its own, as with the plugin.
- **Folders for released files that were not read:** they have no libraries to list; trust already reports them.
- **Publishing the files to DMS or into the distribution:** the runner's TeamCity step decides where the zip goes.
- **The per-file counts of skipped packages** (`SkippedPackages.perFile`): not part of the plugin's files; kept for a per-file report if one is asked for.
- **Failing a build on a missing text or on trust:** a missing text is the registry's gap, and trust says how complete the list is, not whether a license is allowed.
- **Switching the flags on or off in the pipeline:** the TeamCity step's own change, built on these flags; until then no step passes a flag, so every build stays green.
- **Analysing the current violations of every component:** a separate analysis, which decides where the flags can be switched on.
