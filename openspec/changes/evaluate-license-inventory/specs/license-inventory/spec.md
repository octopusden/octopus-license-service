## Purpose

How the service turns a license target into its license inventory. This change adds one fact per library: whether it is ours, which needs Artifactory for a file known only by its checksum. The inventory still judges no license.

## ADDED Requirements

### Requirement: Each library records whether it is ours

The service SHALL mark a library as ours when its id is a Maven id whose group starts with an internal group, or a checksum-only id whose file name starts with an internal group followed by `.`, or a checksum-only id with a copy of its sha256 stored in a repository whose name starts with one of our repository prefixes and ends with `local`. Every other library SHALL NOT be ours. A checksum-only file renamed to Maven coordinates SHALL be marked by its new id.

#### Scenario: Maven library of an internal group

- **WHEN** the internal groups are `org.example` and a library is `pkg:maven/org.example.portal/portal-plugins@2.4.0-118`
- **THEN** it is ours

#### Scenario: Third-party Maven library

- **WHEN** a library is `pkg:maven/org.springframework/spring-core@6.1.4`
- **THEN** it is not ours

#### Scenario: Checksum-only file named after an internal group

- **WHEN** a checksum-only file is named `org.example.portal.portal-core-2.4.0.jar`
- **THEN** it is ours, and Artifactory is not asked about its checksum

#### Scenario: Checksum-only file stored in our repository

- **WHEN** a checksum-only file `vendor-blob.so` has a copy in `rnd-releases-local`, and `rnd-` is one of our repository prefixes
- **THEN** it is ours and it is not renamed

#### Scenario: Checksum-only file stored only in a remote cache

- **WHEN** a checksum-only file's only copy is in `maven-central-cache` under `io/netty/netty-common/4.1.107.Final/`
- **THEN** it is renamed to `pkg:maven/io.netty/netty-common@4.1.107.Final` and is not ours

#### Scenario: Other package types

- **WHEN** a library is `pkg:npm/left-pad@1.3.0`
- **THEN** it is not ours
