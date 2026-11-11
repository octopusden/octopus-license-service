## Purpose

How the service turns a license inventory and a license policy into an assessment: which libraries count, and the verdict on each counted library. It reads no system. Whether a library is ours is read from the inventory; how a license is judged is the license policy's. Trust levels are outside this spec.

## ADDED Requirements

### Requirement: Docker layers are not counted

The service SHALL drop every library of type `docker` from the assessment: it SHALL NOT be counted, judged or added to any skipped total.

#### Scenario: Image layer

- **WHEN** the inventory holds `pkg:docker/sample/base@sha256__ab12`
- **THEN** the assessment does not list it and no skipped count includes it

### Requirement: OS packages are skipped and counted

The service SHALL skip every library of type `rpm`, `deb` or `alpine`: it SHALL NOT be judged, it SHALL add one to the OS package total, and one to the skipped count of each released file it was found in.

#### Scenario: OS package in an image

- **WHEN** `pkg:rpm/openssl-libs@3.0.7` was found in the image `sample/portal:2.4.0-118`
- **THEN** it is not judged, the OS package total is 1 and the image's skipped count is 1

#### Scenario: OS package in two files

- **WHEN** an OS package was found in two released files
- **THEN** the OS package total counts it once and each file's skipped count counts it once

### Requirement: Python packages found only in images are skipped

The service SHALL skip a library of type `pypi` when every released file it was found in is an image: it SHALL NOT be judged, it SHALL add one to the base-image Python total, and one to the skipped count of each released file it was found in. A `pypi` library found in any file that is not an image SHALL be counted.

#### Scenario: Python package only in an image

- **WHEN** `pkg:pypi/requests@2.31.0` was found only in an image
- **THEN** it is not judged and the base-image Python total is 1

#### Scenario: Python package in an image and a zip

- **WHEN** `pkg:pypi/attrs@23.2.0` was found in an image and in a zip
- **THEN** it is counted and judged

### Requirement: Our own libraries are internal

The service SHALL give every counted library the inventory marks as ours the verdict `INTERNAL`, before any other rule.

#### Scenario: Our nested archive

- **WHEN** `pkg:maven/org.example.portal/portal-plugins@2.4.0-118` is marked ours
- **THEN** its verdict is `INTERNAL`

#### Scenario: Our file known only by checksum

- **WHEN** a checksum-only file is marked ours
- **THEN** its verdict is `INTERNAL`, not `UNIDENTIFIED`

### Requirement: A file nobody could name is unidentified

The service SHALL give a counted library that is not ours, and whose id is written as `generic://…`, the verdict `UNIDENTIFIED`, whatever licenses were found for it.

#### Scenario: Checksum-only file

- **WHEN** `generic://sha256:cd34…/vendor-blob.so` is not ours
- **THEN** its verdict is `UNIDENTIFIED`

#### Scenario: Checksum-only file with a license

- **WHEN** Xray named the license `MIT` for a checksum-only file that is not ours
- **THEN** its verdict is still `UNIDENTIFIED`

### Requirement: Every other library is judged by the license policy

The service SHALL give every other counted library the license policy's verdict (`ALLOWED`, `NOT_ALLOWED` or `UNKNOWN`) on the licenses the inventory found for it, passing the Maven coordinates of a Maven library so that the registry's override applies. Every counted library SHALL carry the policy's canonical license names and whether an override applied, whatever its verdict.

#### Scenario: Allowed through a synonym

- **WHEN** `pkg:maven/org.springframework/spring-core@6.1.4` was found with `The Apache Software License, Version 2.0`, a synonym of the allowed `Apache-2.0`
- **THEN** its verdict is `ALLOWED` and its license is `Apache-2.0`

#### Scenario: Override

- **WHEN** `pkg:maven/org.example/sample-lib@1.5` was found with `GPL-3.0` and the registry overrides `org.example--sample-lib--[1.0,2.0)` with `MIT`
- **THEN** its verdict is `ALLOWED`, its license is `MIT` and it is overridden

#### Scenario: No license

- **WHEN** `pkg:npm/left-pad@1.3.0` was found with no license
- **THEN** its verdict is `UNKNOWN`

#### Scenario: Not a Maven library

- **WHEN** an `npm` library is judged
- **THEN** no override applies to it

#### Scenario: Internal library with an override

- **WHEN** a library marked ours matches an override
- **THEN** its verdict is `INTERNAL` and it is overridden

### Requirement: The assessment keeps the inventory and its order

The assessment SHALL hold the inventory unchanged, the counted libraries in the inventory's order, and the skipped totals; a released file with nothing skipped SHALL have no skipped count of its own.

#### Scenario: Order

- **WHEN** the inventory lists libraries sorted by rendered id
- **THEN** the assessment lists the counted ones in that order

#### Scenario: Nothing skipped in a file

- **WHEN** no OS or base-image Python package was found in the zip
- **THEN** the zip has no skipped count entry
