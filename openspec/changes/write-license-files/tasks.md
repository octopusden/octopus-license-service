## 1. The `license-files` module and its model (Decision 1)

- [ ] 1.1 Set up the module:
  - `settings.gradle.kts`: `:license-files` at `engine/files`;
  - build file: `api(project(":license-evaluation"))` and JUnit.

  Confirm with `dependencies` that no Spring, no `:core`, no `:license-trust` and no client is on the compile classpath.
- [ ] 1.2 Model: `model/LicenseFiles.kt`, `FileFolder`, `NamedFile`, `MissingText`, `MissingTextReason`.
- [ ] 1.3 `PackageDependencyTest`: `impl` is internal; the module imports no `core`, Spring or client package.

## 2. Folders and their libraries (Decisions 2, 3)

- [ ] 2.1 Failing tests for `FolderPlanner`:
  - [ ] 2.1.1 a folder per `READ` file, in listing order; none for `NOT_FOUND`, `NOT_READABLE`, `PLAIN_FILE`, `UNSUPPORTED`, `METADATA`, `NOT_SELECTED`;
  - [ ] 2.1.2 a Maven file's folder is its path's last segment;
  - [ ] 2.1.3 an image's folder is its entry with `/` and `:` as `_`;
  - [ ] 2.1.4 a repeated name gets `-2`, then `-3`, in listing order;
  - [ ] 2.1.5 a library is in the folder of each entry of its `foundIn`;
  - [ ] 2.1.6 `INTERNAL` is left out; `ALLOWED`, `NOT_ALLOWED`, `UNKNOWN`, `UNIDENTIFIED` are listed;
  - [ ] 2.1.7 a read file with no third-party library still gets a folder, with no library;
  - [ ] 2.1.8 libraries keep the assessment's order.
- [ ] 2.2 Implement `impl/FolderPlanner.kt`.
- [ ] 2.3 Confirm the tests pass.

## 3. `THIRD-PARTY.txt` (Decision 4)

- [ ] 3.1 Failing tests for `NoticeLine` and `ThirdPartyText`:
  - [ ] 3.1.1 Maven: artifactId and `group:artifact:version`;
  - [ ] 3.1.2 other purl: its name and the rendered purl;
  - [ ] 3.1.3 checksum only: its file name and the rendered id;
  - [ ] 3.1.4 unrecognized: the raw id twice;
  - [ ] 3.1.5 two licenses: ` (A) (B)`, in the assessment's order;
  - [ ] 3.1.6 no license: ` (Unknown license)`;
  - [ ] 3.1.7 the whole file for three libraries matches the template's output byte for byte, leading empty line and indentation included, with no ` - ` URL part;
  - [ ] 3.1.8 no library: the empty line, then `The project has no dependencies.`.
- [ ] 3.2 Implement `impl/NoticeLine.kt` and `impl/ThirdPartyText.kt`.
- [ ] 3.3 Confirm the tests pass.

## 4. `licenses.xml` (Decision 5)

- [ ] 4.1 Failing tests for `LicensesXml`:
  - [ ] 4.1.1 a Maven library's `groupId`, `artifactId`, `version`;
  - [ ] 4.1.2 an npm package: `groupId` `npm`;
  - [ ] 4.1.3 a checksum-only file: `generic`, its file name, no `version`;
  - [ ] 4.1.4 a license's `url` is the `licenses.properties` value, registry path or URL;
  - [ ] 4.1.5 a license with no entry has no `url`;
  - [ ] 4.1.6 the document parses back as the plugin's `LicenseSummaryReader` reads it (element names and nesting).
- [ ] 4.2 Implement `impl/LicensesXml.kt` with the JDK's DOM and transformer, indented, UTF-8.
- [ ] 4.3 Confirm the tests pass.

## 5. License texts and the writer (Decisions 1, 6)

- [ ] 5.1 Failing tests for `LicenseTextFiles` and `LicenseFilesWriter`:
  - [ ] 5.1.1 file names: `Apache-2.0` at `licenses/APACHE-2.0.txt` gives `apache-2.0 - apache-2.0.txt`; `CC0` at `licenses/cc0.html` keeps `.html`; a name with `/` gets `_`; a location with no extension gets `.txt`;
  - [ ] 5.1.2 a registry text is written as stored;
  - [ ] 5.1.3 a downloaded text is written as its bytes;
  - [ ] 5.1.4 only licenses used in the folder get a text;
  - [ ] 5.1.5 a license with no entry is `NO_REGISTRY_ENTRY`; a URL missing from `downloaded` is `DOWNLOAD_FAILED` with its URL;
  - [ ] 5.1.6 a license missing in two folders is listed once;
  - [ ] 5.1.7 `textsToDownload` holds exactly the URLs of used licenses, and no registry path;
  - [ ] 5.1.8 the worked example of `design.md` end to end.
- [ ] 5.2 Implement `impl/LicenseTextFiles.kt` and `LicenseFilesWriter.kt`.
- [ ] 5.3 Confirm `:license-files:check` passes.

## 6. Server: download, write, zip (Decisions 1, 7)

- [ ] 6.1 Failing tests:
  - [ ] 6.1.1 `LicenseTextDownloader`, against a local HTTP server: success; timeout and 404 are absent from the result; a second request for a succeeded URL makes no call; a failed URL is asked again next time; the counter's three outcomes;
  - [ ] 6.1.2 `LicenseFilesZip`: entries `folder/file`, in the writer's order; an empty `LicenseFiles` gives an empty zip;
  - [ ] 6.1.3 `EvaluatingLicenseReportGenerator`: writes after grading, downloads only `textsToDownload`, puts the zip in the result;
  - [ ] 6.1.4 `SummaryMapper`: `missingLicenseTexts` from the writer's missing texts.
- [ ] 6.2 Implement:
  - [ ] 6.2.1 `server/.../service/impl/LicenseTextDownloader.kt`;
  - [ ] 6.2.2 `server/.../service/impl/LicenseFilesZip.kt`;
  - [ ] 6.2.3 `LicenseServiceProperties.licenseTexts.downloadTimeout`, `application.yaml`;
  - [ ] 6.2.4 the `LicenseFilesWriter` bean; `server` depends on `:license-files`.
- [ ] 6.3 Confirm `:license-service:check` passes.

## 7. Core and runner (Decision 8)

- [ ] 7.1 `core`: `LicenseReportSummary.missingLicenseTexts`, `MissingLicenseText`, `MissingTextReason`; serialization round-trip test.
- [ ] 7.2 `BuildLog`: one warning per missing text; the exit code test still ignores it.
- [ ] 7.3 Confirm `:core:check` and `:license-runner:check` pass.

## 8. Report store (Decision 9)

- [ ] 8.1 Failing tests for `LicenseReportStore`:
  - [ ] 8.1.1 over `maxStoredReports`, the oldest finished go first; the one just finished stays;
  - [ ] 8.1.2 a running report never counts toward the limit and is never dropped;
  - [ ] 8.1.3 over the byte limit, a finished report without files is never dropped;
  - [ ] 8.1.4 the existing byte-limit tests still hold.
- [ ] 8.2 Implement; `license-service.max-stored-reports` (default 500) in the properties and `application.yaml`.
- [ ] 8.3 Confirm the tests pass.

## 8a. Build outcome (Decision 11)

- [ ] 8a.1 Failing tests for `BuildOutcome`:
  - [ ] 8a.1.1 no flag: pass for every outcome, unavailable and `FAILED` included;
  - [ ] 8a.1.2 each flag alone fails on its own verdict and passes on the other two;
  - [ ] 8a.1.3 the fail reasons name each failing library with its verdict;
  - [ ] 8a.1.4 `FULL`, any flag on: unavailable, `FAILED`, timed out, `DONE` without summary each fail;
  - [ ] 8a.1.5 `CHECK`, any flag on: the same four, and the runner's own error, each pass with a warning;
  - [ ] 8a.1.6 `CHECK`: a `preliminary` violation passes with a warning; a non-preliminary one follows its flag;
  - [ ] 8a.1.7 any flag on: `EXCLUDED` passes;
  - [ ] 8a.1.8 trust `NONE` and missing texts never fail.
- [ ] 8a.2 Implement `runner/.../BuildOutcome.kt` and `FailOn`; `BuildOutcome.of` takes the mode.
- [ ] 8a.2a Server: failing tests, then `Violation.preliminary` in `core` and in `SummaryMapper`:
  - [ ] 8a.2a.1 `CHECK`, every file of the violation without `BINARY_SCAN` evidence: `true`;
  - [ ] 8a.2a.2 `CHECK`, one of its files with `BINARY_SCAN`: `false`;
  - [ ] 8a.2a.3 `FULL`: always `false`.
- [ ] 8a.3 `LicenseRunnerCommand`: the three flags; writes the output, prints the fail reasons as errors, then exits non-zero on `Fail`; the runner's own error goes through the same decision. Update its KDoc, which says it always exits 0.
- [ ] 8a.4 Command tests: exit code 0 without flags on a report with violations; non-zero with `--fail-on-unknown` on an `UNKNOWN` library, with the report and zip written.
- [ ] 8a.5 Confirm `:license-runner:check` passes.

## 9. Compare with the plugin (Decision 10)

- [ ] 9.1 Adapt the prototype's `compare_plugin.py` to read the service's zip: per released file, the plugin's `THIRD-PARTY.txt` against the folder's, by group:artifact:version and canonical licenses.
- [ ] 9.2 A component that releases several wars: every plugin line is in the matching folder, same canonical licenses; each extra line explained. Record the counts here, never the component's name.
- [ ] 9.3 The other components of the earlier comparison: record the totals and any difference not explained by naming or by libraries outside the Maven tree.

## 10. Finalization

- [ ] 10.1 `./gradlew build qualityCheck` green.
- [ ] 10.2 `docs/architecture.md`: the files module in the diagram and table, the request flow (write after grade, download texts), the `SummaryMapper` and empty-zip sentences, the downloads metric.
- [ ] 10.3 `CLAUDE.md` and `README.md` module lists.
- [ ] 10.4 Out of scope holds, by grep: no POM read; no combined top-level set; no folder for a file not read; nothing publishes the zip; `perFile` still unread; the runner's exit code reads neither `trust` nor `missingLicenseTexts`, and `violations` only through `BuildOutcome`; no fail flag defaults to on; the service has no fail setting.
- [ ] 10.5 The risks in `design.md` still hold; anything deferred becomes a `TD-NNN`, never a `TODO`. The failed license lookup that gives a false `UNKNOWN` gets its own `TD-NNN`.
