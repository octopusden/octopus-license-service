## Purpose

How the service turns a license target into its license inventory: every file the version releases, its status, and every package found in it with the licenses its sources name. It ends before judging: whether a package is ours, allowed, or counted is evaluation, outside this spec.

## ADDED Requirements

### Requirement: The convention builds are found by name at each artifact version

The service SHALL look up the build-info of `{component}_{x}e`, where `x` is `e` for an explicit component and `i` otherwise, and of the same name with `_docker`, at each artifact version of the target in order, keeping the first that exists. Build-info that is missing or cannot be read SHALL mean no build, not a failure.

#### Scenario: Build exists only at the build version

- **WHEN** an explicit component `sample-portal` has artifact versions `2.4.0`, `2.4.0-118` and only `sample-portal_ee/2.4.0-118` exists
- **THEN** the convention build is `sample-portal_ee/2.4.0-118`

#### Scenario: Implicit component

- **WHEN** the component is not explicit
- **THEN** the builds looked up are `{component}_ie` and `{component}_ie_docker`

#### Scenario: No convention build

- **WHEN** neither build exists at any artifact version
- **THEN** the inventory lists no convention build, and declared entries are still looked up directly

### Requirement: Every Maven file a convention build published is listed with its selection

The service SHALL list each non-docker file of a convention build as `group:artifact:extension[:classifier]`, the group from the module id and the artifact id, classifier and extension from the file name. It SHALL select the file when the entry equals a declared Maven artifact, or when its group and artifact each fully match a pattern of one Maven artifact pattern entry. A selected file that is not declared and has extension `pom`, `module`, `md5`, `sha1`, `sha256` or `asc`, or classifier `sources`, `javadoc`, `tests`, `test-sources` or `test-fixtures`, SHALL be `METADATA`. A file not selected SHALL be `NOT_SELECTED`. Both SHALL be listed and not read.

#### Scenario: File of another artifact id in the same module

- **WHEN** module `org.example.portal:sample-portal:2.4.0-118` publishes `sample-portal-config-2.4.0-118.yaml`
- **THEN** the entry is `org.example.portal:sample-portal-config:yaml`

#### Scenario: Classifier

- **WHEN** the module publishes `sample-portal-2.4.0-118-sources.jar`
- **THEN** the entry is `org.example.portal:sample-portal:jar:sources`

#### Scenario: Selected by declaration and pattern

- **WHEN** the entry is declared and also matches a pattern
- **THEN** the file is selected by `DECLARED` and `PATTERN`

#### Scenario: Pattern-selected pom

- **WHEN** a pom matches a pattern and is not declared
- **THEN** the file is `METADATA` and is not read

#### Scenario: Declared pom

- **WHEN** a pom is declared in the `GAV` field
- **THEN** the file is read like any other selected file

#### Scenario: Group matches but artifact does not

- **WHEN** the group matches a pattern entry's group patterns and the artifact matches none of that entry's artifact patterns
- **THEN** the file is `NOT_SELECTED`

#### Scenario: Pattern that is not a valid regular expression

- **WHEN** a pattern cannot be compiled
- **THEN** it matches only the identical string

#### Scenario: Selected file missing from Artifactory

- **WHEN** no stored file has the published file's sha1
- **THEN** the file is `NOT_FOUND` with finding `ARTIFACT_NOT_FOUND`

### Requirement: Every image a convention build published is listed and read

The service SHALL list each file whose name ends in `manifest.json`, other than `list.manifest.json`, of a docker module or at a path ending in `manifest.json`, as the image `image:tag` given by its folder, and SHALL select it as `DOCKER_BUILD` whether or not it is declared. It SHALL mark the image declared when the image name equals a declared image's name. Other files of a docker module SHALL NOT be listed.

#### Scenario: Image not in the docker field

- **WHEN** the docker build publishes `sample/portal/2.4.0-118/manifest.json` and the `docker` field is empty
- **THEN** the file `sample/portal:2.4.0-118` is listed, selected, not declared, and read

#### Scenario: Declared without a tag

- **WHEN** the `docker` field is `sample/portal`
- **THEN** the image `sample/portal:2.4.0-118` is declared, and `sample/portal` is not looked up again as a leftover

#### Scenario: Layers

- **WHEN** the docker module also publishes layer blobs and `list.manifest.json`
- **THEN** none of them is listed

### Requirement: Declared entries no convention build produced are looked up directly

The service SHALL look up each declared Maven artifact and image not matched by a convention file: a Maven artifact at `g/a/v/a-v[-classifier].ext` for each artifact version in order, an image at its tag or, without one, at each artifact version. When several stored copies exist it SHALL take a repository whose name contains `release` and ends with `local` first, then any whose name ends with `local`, then any other, each group by repository name. It SHALL find the file's build from its `build.name` and `build.number` properties, then from a build that published its sha1, and SHALL add finding `BUILD_NAME_DIFFERS` when that build's name is not a convention build name.

#### Scenario: Built by another build

- **WHEN** a declared `org.example.lib:native-lib:tar.gz` is stored with properties naming build `shared-native-build/7.1.3`
- **THEN** the file is listed with build `shared-native-build/7.1.3`, found by properties, and finding `BUILD_NAME_DIFFERS`

#### Scenario: No properties

- **WHEN** the stored file has no build properties and build `other_build/12` published its sha1
- **THEN** the file's build is `other_build/12`, found by checksum

#### Scenario: Copy in a cache and in a release repository

- **WHEN** the file is stored in `maven-central-cache` and in `rnd-maven-release-local`
- **THEN** the copy in `rnd-maven-release-local` is read

#### Scenario: Missing at every version

- **WHEN** no artifact version has the file
- **THEN** it is `NOT_FOUND` with finding `ARTIFACT_NOT_FOUND`

#### Scenario: Unsupported entry

- **WHEN** the target declares an unsupported entry
- **THEN** it is listed `UNSUPPORTED` with finding `UNSUPPORTED_DISTRIBUTION_ENTRY`, and nothing is looked up

#### Scenario: Leftover image without a tag

- **WHEN** the `docker` field declares `sample/batch` with no tag, no convention image matches it, and it exists at artifact version `2.4.0-118`
- **THEN** the file is listed as `sample/batch:2.4.0-118`

#### Scenario: Nothing to read

- **WHEN** every listed file is `NOT_SELECTED` or `METADATA`, or nothing is listed
- **THEN** the inventory has finding `NO_DISTRIBUTION` and no packages

### Requirement: A located file's status follows from Xray and its build

The service SHALL give a located file the status `PLAIN_FILE`, with finding `NOT_SCANNABLE`, when Xray has not indexed it, it is a Maven file, and its extension is a text type (`yaml`, `yml`, `xml`, `json`, `txt`, `properties`, `sql`, `sh`, `bat`, `md`, `pdf`, `html`, `csv`), and SHALL NOT read it. Otherwise it SHALL add finding `NOT_IN_XRAY` when Xray has not indexed it and `NO_BUILD_INFO` when its build is unknown; with neither the file SHALL be `NOT_READABLE`, else `READ` with evidence `BINARY_SCAN` when Xray indexed it and `BUILD_RECORD` when its build is known.

#### Scenario: Plain file with a build

- **WHEN** an unindexed `yaml` file has a known build
- **THEN** it is `PLAIN_FILE` and its build is not read

#### Scenario: Unindexed jar with a build

- **WHEN** Xray has not indexed a jar whose build is known
- **THEN** it is `READ` with evidence `BUILD_RECORD` only, and finding `NOT_IN_XRAY`

#### Scenario: Indexed without a build

- **WHEN** Xray indexed the file and no build is found
- **THEN** it is `READ` with evidence `BINARY_SCAN` only, and finding `NO_BUILD_INFO`

#### Scenario: Neither

- **WHEN** Xray has not indexed the file and no build is found
- **THEN** it is `NOT_READABLE`, and nothing is read

### Requirement: The service waits until Xray has finished scanning a file

The service SHALL ask Xray for a file's license scan status before reading its scan, and SHALL wait while the scan is pending, up to the configured timeout per report (5 minutes by default, one budget for all the report's files). It SHALL then read the file's scan whatever the status, and a scan Xray answers with an error for the file SHALL count as not indexed.

#### Scenario: Scan finishes while waiting

- **WHEN** Xray reports the file pending, then done
- **THEN** the file's packages come from the finished scan

#### Scenario: Not supported

- **WHEN** Xray reports the file not supported, and its scan answers an error for the file
- **THEN** the file is not indexed, without waiting, and its status follows from its build

#### Scenario: Timeout

- **WHEN** the file is still pending when the timeout passes, and its scan answers an error for the file
- **THEN** the file is not indexed: it is read from its build only, with finding `NOT_IN_XRAY`

#### Scenario: Not scanned

- **WHEN** Xray reports the file's license scan `NOT_SCANNED`
- **THEN** the service does not wait, and reads the file's scan as the prototype does

### Requirement: A read file's packages come from every source it has

For a `READ` file the service SHALL take, in order: the packages Xray found in it (evidence `BINARY_SCAN`); and when its build is known, the components of every CycloneDX file (`.cdx.json`, `bom.json`, `sbom.json`, `-cyclonedx.json`) any module of the build published, without purl qualifiers, with their license ids, names or expressions (evidence `SBOM`); the `prod` dependencies of every `npm` module (evidence `BUILD_RECORD`); and the shipped dependencies of the module that published the file. When no module published the file it SHALL add finding `NO_PRODUCING_MODULE` and read no dependencies.

#### Scenario: Fat jar library only in build-info

- **WHEN** Xray finds nothing for `jackson-databind` and the producing module records it under `runtime`
- **THEN** `pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.17.0` is found with evidence `BUILD_RECORD` and no license

#### Scenario: Same library from two sources

- **WHEN** Xray and the build both report `spring-core`
- **THEN** one package is found with evidence `BINARY_SCAN` and `BUILD_RECORD`, and Xray's licenses, since a build record names none

#### Scenario: SBOM that cannot be read

- **WHEN** an SBOM file of the build cannot be downloaded or parsed
- **THEN** it is skipped and the other sources are still read

#### Scenario: Bundled JavaScript

- **WHEN** an `npm` module of the build records `react` `18.2.0` with scope `prod` and `jest` with scope `dev`
- **THEN** `pkg:npm/react@18.2.0` is found for every read file of that build, and `jest` is not

### Requirement: Only shipped dependencies are read

The service SHALL read a dependency of the producing module when one of its scopes is `compile`, `runtime`, `runtimeClasspath` or `embed`, or ends in `Release…RuntimeClasspath` or `Embed` without `test` or `debug` in it. It SHALL skip `Gradle API`, `Local Groovy` and `Gradle TestKit`, and SHALL add finding `LOCAL_FILE_DEPENDENCY` for a shipped dependency without group, artifact and version.

#### Scenario: Test scope

- **WHEN** a dependency's only scope is `test`
- **THEN** it is not read

#### Scenario: Android release variant

- **WHEN** a dependency's scope is `boricaProductionReleaseRuntimeClasspath`
- **THEN** it is read

#### Scenario: Android debug variant

- **WHEN** a dependency's scope is `fullDebugEmbed`
- **THEN** it is not read

#### Scenario: Checksum id

- **WHEN** a shipped dependency's id is `sha256:ab12…:file`
- **THEN** no package is found for it, and finding `LOCAL_FILE_DEPENDENCY` names it

#### Scenario: Local jar

- **WHEN** a shipped dependency's id is `VTS-SDK-6.3.0.jar`
- **THEN** no package is found for it, and finding `LOCAL_FILE_DEPENDENCY` names it

### Requirement: Our own archives inside a file are followed into their builds

The service SHALL follow a dependency whose type is `zip`, `tar`, `tgz`, `gz`, `war` or `ear` and whose group starts with an internal group, instead of reading it as a package, when one of its scopes ships, or when none of its scopes mentions `test` or `debug` and Xray's scan of the current file lists exactly its group, artifact and version. It SHALL locate the archive by its Maven path, then by its sha1, and its build as for declared entries; with no file or no build it SHALL add finding `UNRESOLVED_INTERNAL_ARCHIVE`. It SHALL read the archive as a file, its packages counting for the outer released file, following each build and dependency once per released file, and only from a file at depth 10 or less, the released file being depth 0. An archive whose Xray scan lists no library other than itself and docker layers SHALL contribute only Xray's packages, with finding `NO_LIBRARIES_IN_FILE`.

#### Scenario: Plugin zip under a custom configuration

- **WHEN** the zip records `org.example.portal:portal-plugins:2.4.0-118` type `zip` under `modpackages` and Xray's scan of the zip lists `pkg:maven/org.example.portal/portal-plugins@2.4.0-118`
- **THEN** `portal-plugins` is followed, and the packages found in it are found in the zip

#### Scenario: Custom configuration without proof

- **WHEN** the same dependency is not in Xray's scan of the zip
- **THEN** it is neither followed nor found

#### Scenario: Third-party archive

- **WHEN** a shipped dependency is a `zip` of a group that is not internal
- **THEN** it is found as a Maven package and not followed

#### Scenario: Data archive

- **WHEN** a followed archive's Xray scan lists only the archive itself
- **THEN** only that is found from it, its build is not read, and finding `NO_LIBRARIES_IN_FILE` names it

#### Scenario: Sources checksum

- **WHEN** a followed dependency carries the sha1 of its sources classifier and the archive exists at its Maven path
- **THEN** the archive at the Maven path is read

#### Scenario: Depth limit

- **WHEN** archives are nested twelve deep
- **THEN** the archive at depth 11 is read and the one at depth 12 is not followed

#### Scenario: Cycle

- **WHEN** two archives depend on each other
- **THEN** each is followed once for the released file

### Requirement: Checksum-only files are named by a Maven copy

After reading every file, the service SHALL look up each package Xray knows only by checksum, unless its file name starts with an internal group followed by `.` or a copy with its sha256 is stored in a repository whose name starts with an own-repository prefix and ends with `local`, among every stored file with that sha256, in the order Artifactory answers. When a copy's path ends in `{artifact}/{version}` and its name starts with `{artifact}-{version}`, the package SHALL become `pkg:maven/{group}/{artifact}@{version}`, with evidence `CHECKSUM_MATCH` added, without the licenses found for the unnamed file, merged with a package of that id if one exists.

#### Scenario: Renamed copy of a Maven Central jar

- **WHEN** Xray reports `generic://sha256:ab12…/io.netty.netty-common-4.1.107.Final.jar` and that sha256 is stored at `io/netty/netty-common/4.1.107.Final/netty-common-4.1.107.Final.jar`
- **THEN** the package is `pkg:maven/io.netty/netty-common@4.1.107.Final` with evidence `BINARY_SCAN` and `CHECKSUM_MATCH`

#### Scenario: Ours by file name

- **WHEN** the unnamed file is `com.example.internal.parser-1.2.jar` and `com.example.internal` is an internal group
- **THEN** it is not looked up and stays checksum-only

#### Scenario: Found nowhere

- **WHEN** no stored copy has a Maven layout
- **THEN** the package stays checksum-only with its licenses

### Requirement: Packages without a license are asked by coordinates

The service SHALL ask Xray for the licenses of every Maven and npm package that has none, at most 100 per call. A call that fails SHALL leave its packages without a license and SHALL NOT fail the inventory.

#### Scenario: Build-info library

- **WHEN** `jackson-databind` has no license and Xray knows it as `Apache-2.0`
- **THEN** its licenses are `Apache-2.0`

#### Scenario: 250 packages

- **WHEN** 250 packages have no license
- **THEN** three calls are made, of 100, 100 and 50

#### Scenario: Other types

- **WHEN** a `pypi` package has no license
- **THEN** it is not asked about and keeps no license

### Requirement: The inventory lists every file and merges packages across files

The inventory SHALL hold every listed file in listing order with its status, selection, path and build; every package once, sorted by its rendered id, with the union of its licenses, evidence and the files it was found in; and every finding once, with the prototype's code, subject and detail.

#### Scenario: Library in two files

- **WHEN** `guava` is found in the zip and in the image
- **THEN** the inventory has one `guava` package found in both

#### Scenario: Finding text

- **WHEN** a selected Maven file of build `sample-portal_ee/2.4.0-118` is missing from Artifactory
- **THEN** the finding is `ARTIFACT_NOT_FOUND` for its entry with detail `listed in sample-portal_ee/2.4.0-118 but not in Artifactory`

#### Scenario: Library in several SBOMs of one build

- **WHEN** two SBOM files of the build list `pkg:npm/lodash@4.17.21`, the first with `MIT` and the second with no license
- **THEN** the SBOM gives it no license, as the last file read wins

#### Scenario: Ids render as the prototype's

- **WHEN** Xray reports `gav://..org.example:lib:1.0` and `go://golang.org/x/net:0.20.0`
- **THEN** the packages are `pkg:maven/org.example/lib@1.0` and `pkg:golang/golang.org/x/net@0.20.0`

### Requirement: An unreachable Artifactory or Xray fails the inventory

The service SHALL fail the inventory with `InventorySourceUnavailableException` when Artifactory or Xray cannot be asked, apart from the tolerated cases (missing build-info, an unreadable SBOM, a failed license call, an error Xray gives for one path).

#### Scenario: Artifactory down

- **WHEN** an AQL search fails with `503` after retries
- **THEN** the scan fails with `InventorySourceUnavailableException` naming Artifactory

#### Scenario: Xray error for one path

- **WHEN** Xray's artifact summary answers with an error for the file's path
- **THEN** the file is not indexed and the scan goes on
