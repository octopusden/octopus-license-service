## Purpose

Which license files a finished report's download holds, and what each file says. Where the files are published is outside this spec.

## ADDED Requirements

### Requirement: One folder per released file that was read

The download of a `DONE` report SHALL hold one folder per released file with status `READ`, in listing order, and none for any other released file. A Maven file's folder SHALL be named by the last segment of its Artifactory path; an image's by its entry with `/` and `:` replaced by `_`. A name already used SHALL get `-2`, `-3`… in listing order.

#### Scenario: Three wars

- **WHEN** `sample-app:1.4.0` releases the read files `sample-api-1.4.0.war`, `sample-ui-1.4.0.war` and `sample-batch-1.4.0.war`
- **THEN** the zip holds three folders with those names

#### Scenario: An image

- **WHEN** the image `payments/ui:1.4.0` was read
- **THEN** its folder is `payments_ui_1.4.0`

#### Scenario: A file that was not read

- **WHEN** a declared file is `NOT_FOUND`
- **THEN** the zip has no folder for it

### Requirement: A folder lists every third-party library found in its file

A folder SHALL list every counted library whose verdict is not `INTERNAL` and whose `foundIn` holds the folder's released file, whatever its ecosystem or verdict, in the assessment's order.

#### Scenario: A library in two files

- **WHEN** `slf4j-api` is found in the app jar and in the image
- **THEN** both folders list it

#### Scenario: Our own library

- **WHEN** a library's verdict is `INTERNAL`
- **THEN** no folder lists it

#### Scenario: A violation

- **WHEN** a library's verdict is `NOT_ALLOWED`
- **THEN** its folder lists it with its licenses

### Requirement: THIRD-PARTY.txt follows the plugin's template without a URL

Each folder SHALL hold `THIRD-PARTY.txt` in the plugin template's layout: an empty first line, `Lists of N third-party dependencies.`, then per library four spaces, ` (License)` per canonical license or ` (Unknown license)` when it has none, a space, and `name (id)`. The name and id SHALL be the artifactId and `group:artifact:version` for a Maven library, and the package's name and rendered id for any other. With no library the file SHALL read `The project has no dependencies.` after the empty first line.

#### Scenario: A Maven library

- **WHEN** `commons-io:commons-io:2.11.0` is allowed under `Apache-2.0`
- **THEN** its line is `     (Apache-2.0) commons-io (commons-io:commons-io:2.11.0)`

#### Scenario: An npm package

- **WHEN** `pkg:npm/react@18.2.0` is under `MIT`
- **THEN** its line is `     (MIT) react (pkg:npm/react@18.2.0)`

#### Scenario: No known license

- **WHEN** `com.example:foo-core:1.0` has no license
- **THEN** its line is `     (Unknown license) foo-core (com.example:foo-core:1.0)`

#### Scenario: An empty file

- **WHEN** a read file holds no third-party library
- **THEN** its `THIRD-PARTY.txt` says `The project has no dependencies.`

### Requirement: licenses.xml follows the plugin's license summary

Each folder SHALL hold `licenses.xml` with `licenseSummary/dependencies/dependency` per listed library: `groupId`, `artifactId`, `version`, and `licenses/license` with `name` and, when the license has a registry entry, `url` holding that entry's location. A non-Maven library SHALL use its purl type as `groupId`; a checksum-only file `generic` and no `version`.

#### Scenario: A registry text

- **WHEN** `Apache-2.0` is `licenses/APACHE-2.0.txt` in the registry
- **THEN** its `url` is `licenses/APACHE-2.0.txt`

#### Scenario: No registry entry

- **WHEN** a license has no `licenses.properties` entry
- **THEN** its `license` element has a `name` and no `url`

### Requirement: Each license used has its text, named as the plugin names it

Each folder SHALL hold one text per canonical license its libraries use that has a registry entry, named `{license, '/' replaced by '_'} - {last segment of the location}`, with `.txt` appended when that has no extension, in lower case. A registry text SHALL be written as stored, a downloaded one as its bytes.

#### Scenario: A registry text

- **WHEN** `Apache-2.0` is used and is `licenses/APACHE-2.0.txt`
- **THEN** the folder holds `apache-2.0 - apache-2.0.txt` with the registry's text

#### Scenario: A text at a URL

- **WHEN** `oswego-concurrent` is used and its URL ends in `sun-u.c.license.pdf`
- **THEN** the folder holds `oswego-concurrent - sun-u.c.license.pdf` with the downloaded bytes

### Requirement: A text at a URL is downloaded, and a failure does not fail the report

The service SHALL download each URL text a report needs before writing, within `license-service.license-texts.download-timeout`, and keep a successful download for later reports. A failed download SHALL leave that text out and the report SHALL still finish `DONE`.

#### Scenario: Unreachable host

- **WHEN** the host of the `GWT` text cannot be reached
- **THEN** the report is `DONE`, no folder holds a `gwt` text, and the summary lists `GWT` as `DOWNLOAD_FAILED`

#### Scenario: Downloaded once

- **WHEN** two reports need the `H2-1.0` text and the first downloaded it
- **THEN** the second does not download it again

### Requirement: Every download is counted by its outcome

The service SHALL count each URL text it needs as `downloaded`, `cached` or `failed` in `license.texts.downloads{outcome}`.

#### Scenario: A failure

- **WHEN** a download times out
- **THEN** `license.texts.downloads{outcome=failed}` increases by one
