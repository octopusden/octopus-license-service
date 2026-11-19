## 1. The `license-files` module and its model (Decision 1)

- [x] 1.1 Set up the module:
  - `settings.gradle.kts`: `:license-files` at `engine/files`;
  - build file: `api(project(":license-evaluation"))` and JUnit.

  Confirm with `dependencies` that no Spring, no `:core`, no `:license-trust` and no client is on the compile classpath. Confirmed: `:license-evaluation`, `:license-inventory`, `:license-target-registry`, `:license-policy-registry` and the Kotlin stdlib only.
- [x] 1.2 Model: `model/LicenseFiles.kt`, `FileFolder`, `NamedFile`, `MissingText`, `MissingTextReason`.
- [x] 1.3 `PackageDependencyTest`: the model imports no `impl`; the module imports no `core`, trust, Spring, client, `java.net` or file-system package.

## 2. Folders and their libraries (Decisions 2, 3)

- [x] 2.1 Failing tests for `FolderPlanner`:
  - [x] 2.1.1 a folder per `READ` file, in listing order; none for `NOT_FOUND`, `NOT_READABLE`, `PLAIN_FILE`, `UNSUPPORTED`, `METADATA`, `NOT_SELECTED`;
  - [x] 2.1.2 a Maven file's folder is its path's last segment;
  - [x] 2.1.3 an image's folder is its entry with `/` and `:` as `_`;
  - [x] 2.1.4 a repeated name gets `-2`, then `-3`, in listing order;
  - [x] 2.1.5 a library is in the folder of each entry of its `foundIn`;
  - [x] 2.1.6 `INTERNAL` is left out; `ALLOWED`, `NOT_ALLOWED`, `UNKNOWN`, `UNIDENTIFIED` are listed;
  - [x] 2.1.7 a read file with no third-party library still gets a folder, with no library;
  - [x] 2.1.8 libraries keep the assessment's order.
- [x] 2.2 Implement `impl/FolderPlanner.kt`.
- [x] 2.3 Confirm the tests pass. Green.

## 3. `THIRD-PARTY.txt` (Decision 4)

- [x] 3.1 Failing tests for `NoticeLine` and `ThirdPartyText`:
  - [x] 3.1.1 Maven: artifactId and `group:artifact:version`;
  - [x] 3.1.2 other purl: its name and the rendered purl;
  - [x] 3.1.3 checksum only: its file name and the rendered id;
  - [x] 3.1.4 unrecognized: the raw id twice;
  - [x] 3.1.5 two licenses: ` (A) (B)`, in the assessment's order;
  - [x] 3.1.6 no license: ` (Unknown license)`;
  - [x] 3.1.7 the whole file for three libraries matches the template's output byte for byte, leading empty line and indentation included, with no ` - ` URL part;
  - [x] 3.1.8 no library: the empty line, then `The project has no dependencies.`.
- [x] 3.2 Implement `impl/NoticeLine.kt` and `impl/ThirdPartyText.kt`.
- [x] 3.3 Confirm the tests pass. Green.

## 4. `licenses.xml` (Decision 5)

- [x] 4.1 Failing tests for `LicensesXml`:
  - [x] 4.1.1 a Maven library's `groupId`, `artifactId`, `version`;
  - [x] 4.1.2 an npm package: `groupId` `npm`;
  - [x] 4.1.3 a checksum-only file: `generic`, its file name, no `version`;
  - [x] 4.1.4 a license's `url` is the `licenses.properties` value, registry path or URL;
  - [x] 4.1.5 a license with no entry has no `url`;
  - [x] 4.1.6 the document parses back as the plugin's `LicenseSummaryReader` reads it (element names and nesting).
- [x] 4.2 Implement `impl/LicensesXml.kt` with the JDK's DOM and transformer, indented, UTF-8.
- [x] 4.3 Confirm the tests pass. Green.

## 5. License texts and the writer (Decisions 1, 6)

- [x] 5.1 Failing tests for `LicenseTextFiles` and `LicenseFilesWriter`:
  - [x] 5.1.1 file names (also a Bitbucket raw URL, a query string, and an extension shorter than two characters, which the plugin replaces): `Apache-2.0` at `licenses/APACHE-2.0.txt` gives `apache-2.0 - apache-2.0.txt`; `CC0` at `licenses/cc0.html` keeps `.html`; a name with `/` gets `_`; a location with no extension gets `.txt`;
  - [x] 5.1.2 a registry text is written as stored;
  - [x] 5.1.3 a downloaded text is written as its bytes;
  - [x] 5.1.4 only licenses used in the folder get a text;
  - [x] 5.1.5 a license with no entry is `NO_REGISTRY_ENTRY`; a URL missing from `downloaded` is `DOWNLOAD_FAILED` with its URL;
  - [x] 5.1.6 a license missing in two folders is listed once;
  - [x] 5.1.7 `textsToDownload` holds exactly the URLs of used licenses, and no registry path;
  - [x] 5.1.8 the worked example of `design.md` end to end.
- [x] 5.2 Implement `impl/LicenseTextFiles.kt` and `LicenseFilesWriter.kt`.
- [x] 5.3 Confirm `:license-files:check` passes. Green: 50 tests, detekt and ktlint clean.

## 6. Server: download, write, zip (Decisions 1, 7)

- [x] 6.1 Failing tests:
  - [x] 6.1.1 `LicenseTextDownloader`, against a local HTTP server: success; timeout and 404 are absent from the result; a second request for a succeeded URL makes no call; a failed URL is asked again next time; the counter's three outcomes;
  - [x] 6.1.2 `LicenseFilesZip`: entries `folder/file`, in the writer's order; an empty `LicenseFiles` gives an empty zip;
  - [x] 6.1.3 `EvaluatingLicenseReportGenerator`: writes after grading, downloads only `textsToDownload`, puts the zip in the result. Covered as: the zip holds each read file's files and a URL that cannot be reached is listed missing and counted `failed` once, so only the needed URL was asked;
  - [x] 6.1.4 `SummaryMapper`: `missingLicenseTexts` from the writer's missing texts.
- [x] 6.2 Implement:
  - [x] 6.2.1 `server/.../service/impl/LicenseTextDownloader.kt`;
  - [x] 6.2.2 `server/.../service/impl/LicenseFilesZip.kt`;
  - [x] 6.2.3 `LicenseServiceProperties.licenseTexts.downloadTimeout`, `application.yaml`;
  - [x] 6.2.4 the `LicenseFilesWriter` bean; `server` depends on `:license-files`.
- [x] 6.3 Confirm `:license-service:check` passes. Green: 109 tests, 0 skipped.
- [x] 6.4 `SummaryMapper` also sets `Violation.preliminary` (task 8a.2a), so the mode reaches it now (added on review).

## 7. Core and runner (Decision 8)

- [x] 7.1 `core`: `LicenseReportSummary.missingLicenseTexts`, `MissingLicenseText`, `MissingTextReason`; serialization round-trip test. Covered by the runner's command test, which reads a summary with both fields from JSON; both fields have defaults, so earlier JSON still reads.
- [x] 7.2 `BuildLog`: one warning per missing text; the exit code test still ignores it.
- [x] 7.2a A preliminary violation's warning says Xray has not scanned its files yet (added on review).
- [x] 7.3 Confirm `:core:check` and `:license-runner:check` pass. Green.

## 8. Report store (Decision 9)

- [x] 8.1 Failing tests for `LicenseReportStore`:
  - [x] 8.1.1 over `maxStoredReports`, the oldest finished go first; the one just finished stays;
  - [x] 8.1.2 a running report never counts toward the limit and is never dropped;
  - [x] 8.1.3 over the byte limit, a finished report without files is never dropped;
  - [x] 8.1.4 the existing byte-limit tests still hold.
- [x] 8.2 Implement; `license-service.max-stored-reports` (default 500) in the properties and `application.yaml`.
- [x] 8.3 Confirm the tests pass. Green: `:license-service:check`, 112 tests.

## 8a. Build outcome (Decision 11)

- [x] 8a.1 Failing tests for `BuildOutcome`:
  - [x] 8a.1.1 no flag: pass for every outcome, unavailable and `FAILED` included;
  - [x] 8a.1.2 each flag alone fails on its own verdict and passes on the other two;
  - [x] 8a.1.3 the fail reasons name each failing library with its verdict;
  - [x] 8a.1.4 `FULL`, any flag on: unavailable, `FAILED`, timed out, `DONE` without summary each fail;
  - [x] 8a.1.5 `CHECK`, any flag on: the same four, and the runner's own error, each pass with a warning;
  - [x] 8a.1.6 `CHECK`: a `preliminary` violation passes with a warning; a non-preliminary one follows its flag;
  - [x] 8a.1.7 any flag on: `EXCLUDED` passes;
  - [x] 8a.1.8 trust `NONE` and missing texts never fail.
- [x] 8a.2 Implement `runner/.../BuildOutcome.kt` and `FailOn`; `BuildOutcome.of` takes the mode.
- [x] 8a.2a Server: failing tests, then `Violation.preliminary` in `core` and in `SummaryMapper`:
  - [x] 8a.2a.1 `CHECK`, every file of the violation without `BINARY_SCAN` evidence: `true`;
  - [x] 8a.2a.2 `CHECK`, one of its files with `BINARY_SCAN`: `false`;
  - [x] 8a.2a.3 `FULL`: always `false`.
- [x] 8a.3 `LicenseRunnerCommand`: the three flags; writes the output, prints the fail reasons as errors, then exits non-zero on `Fail`; the runner's own error goes through the same decision. Update its KDoc, which says it always exits 0.
- [x] 8a.4 Command tests (also: an unavailable service with `--fail-on-unknown` exits 1 for a release and 0 with `--check-only`): exit code 0 without flags on a report with violations; non-zero with `--fail-on-unknown` on an `UNKNOWN` library, with the report and zip written.
- [x] 8a.4a `main`: a failing outcome exits with its code; arguments that cannot be read stay a warning with exit 0 even when a fail flag is among them, since the flags themselves may not have been read (added on review).
- [x] 8a.5 Confirm `:license-runner:check` passes. Green: 70 tests, 0 skipped.

## 9. Compare with the plugin (Decision 10)

- [ ] 9.1 Adapt the prototype's `compare_plugin.py` to read the service's zip: per released file, the plugin's `THIRD-PARTY.txt` against the folder's, by group:artifact:version and canonical licenses.
- [ ] 9.2 A component that releases several wars: every plugin line is in the matching folder, same canonical licenses; each extra line explained. Record the counts here, never the component's name.
- [ ] 9.3 The other components of the earlier comparison: record the totals and any difference not explained by naming or by libraries outside the Maven tree.

## 10. Finalization

- [x] 10.1 `./gradlew build qualityCheck` green. 742 tests, 0 skipped, every module.
- [x] 10.2 `docs/architecture.md`: the files module in the diagram and table, the request flow (write after grade, download texts), the `SummaryMapper` and empty-zip sentences, the downloads metric.
- [x] 10.3 `CLAUDE.md` and `README.md` module lists; `README.md` also documents the three fail flags (added on review).
- [x] 10.4 Out of scope holds, by grep: no POM read; no combined top-level set; no folder for a file not read; nothing publishes the zip; `perFile` still unread; the runner's exit code reads neither `trust` nor `missingLicenseTexts`, and `violations` only through `BuildOutcome`; no fail flag defaults to on; the service has no fail setting. Confirmed by grep: no POM read in `:license-files` or the server; no reader of `perFile`; `BuildOutcome` reads only `violations` and the report status; the three flags are Clikt `flag()`s, off unless given; no fail setting in `server`. No combined set and no folder for an unread file: `FolderPlanner` and its tests.
- [x] 10.5 The risks in `design.md` still hold; anything deferred becomes a `TD-NNN`, never a `TODO`. The failed license lookup that gives a false `UNKNOWN` gets its own `TD-NNN`. Recorded as TD-003, cited from the design risk and from `SummaryMapper`. The other risks stand as written.
