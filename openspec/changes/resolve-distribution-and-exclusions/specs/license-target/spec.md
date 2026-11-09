## Purpose

How the service turns a component version into its license target, or into an exclusion, from the components registry alone. It governs what the target holds and when a component is excluded; finding concrete files in Artifactory is outside it.

## ADDED Requirements

### Requirement: The license target is read from the components registry only

The service SHALL build the license target of a component version from the components registry and from no other system. The target SHALL hold the component version, the versions its artifacts may carry (the requested version, then the registry's build version when it differs), the build system, the labels, the declared artifacts and the Maven artifact patterns.

#### Scenario: Build version differs from the requested version

- **WHEN** the registry gives build version `1.4.12-0034` for requested version `1.4.12`
- **THEN** the target's artifact versions are `1.4.12` then `1.4.12-0034`

#### Scenario: Build version equals the requested version

- **WHEN** the registry gives build version `1.4.12` for requested version `1.4.12`
- **THEN** the target's artifact versions are `1.4.12` only

### Requirement: Declared artifacts come from the GAV and docker fields only

The service SHALL read declared artifacts from the registry's `GAV` and `docker` distribution fields for the version, and SHALL NOT read the `DEB` and `RPM` fields.

#### Scenario: GAV entry with extension

- **WHEN** the `GAV` field is `org.example.sample:sample-app:zip`
- **THEN** the target declares a Maven artifact with group `org.example.sample`, artifact `sample-app`, extension `zip` and no classifier

#### Scenario: GAV entry with classifier

- **WHEN** the `GAV` field holds `org.example.sample:sample-app-config:zip:config`
- **THEN** the target declares a Maven artifact with classifier `config`

#### Scenario: Several GAV entries

- **WHEN** the `GAV` field holds two comma-separated entries with spaces around the comma
- **THEN** the target declares two Maven artifacts, each trimmed

#### Scenario: Docker image without a tag

- **WHEN** the `docker` field is `sample/sample-app`
- **THEN** the target declares a docker image `sample/sample-app` with no tag

#### Scenario: Docker image with a tag and a registry port

- **WHEN** the `docker` field is `registry.example:5000/sample/sample-app:1.4`
- **THEN** the target declares a docker image `registry.example:5000/sample/sample-app` with tag `1.4`

#### Scenario: DEB and RPM fields are ignored

- **WHEN** the registry gives `DEB` and `RPM` fields and no `GAV` or `docker` field
- **THEN** the target declares no artifacts

### Requirement: A malformed declared entry is kept as unsupported

The service SHALL keep a `GAV` entry that is not `group:artifact:extension[:classifier]`, or that is a `file:`, `http:` or `https:` URI, as an unsupported declared artifact with its reason, and SHALL NOT fail the resolution because of it.

#### Scenario: GAV entry with two parts

- **WHEN** the `GAV` field holds `org.example.sample:sample-app`
- **THEN** the target declares an unsupported artifact for that entry, and the other entries are declared as usual

#### Scenario: GAV entry that is a file share

- **WHEN** the `GAV` field holds `file:///share/sample/bundle.zip`
- **THEN** the target declares an unsupported artifact for that entry

### Requirement: Maven artifact patterns are those whose range holds the version

The service SHALL keep every Maven artifact pattern entry whose version range, parsed as the components registry parses it, holds the requested version, with its comma-separated group and artifact patterns split and trimmed.

#### Scenario: One range holds the version

- **WHEN** the entries are `[1.0,)` and `(,1.0)` and the version is `1.4.12`
- **THEN** the target holds only the patterns of `[1.0,)`

#### Scenario: No range holds the version

- **WHEN** no entry's range holds the version
- **THEN** the target holds no Maven artifact patterns, and the resolution still succeeds

#### Scenario: Comma-separated patterns

- **WHEN** the matching entry has group pattern `org.a, org.b` and artifact pattern `x-.*`
- **THEN** the target holds groups `org.a` and `org.b` and artifacts `x-.*`

### Requirement: Excluded components are told why

The service SHALL exclude a component when any of these holds, checked in this order, and SHALL give the reason of the first that holds: archived (`Archived component`); internal, its distribution not external (`Internal component: its distribution is not external`); build system `WHISKEY` (`Built with WHISKEY`); label `test-component` (`Test component (label test-component)`); name starting with `doc_` or `doc-` in any case (`Documentation component`); on the configured exclusion list (that entry's reason).

#### Scenario: Archived component

- **WHEN** the registry marks the component archived
- **THEN** the component is excluded with reason `Archived component`

#### Scenario: Internal component

- **WHEN** the component's distribution is not external
- **THEN** the component is excluded with reason `Internal component: its distribution is not external`

#### Scenario: WHISKEY component

- **WHEN** the component's build system is `WHISKEY`
- **THEN** the component is excluded with reason `Built with WHISKEY`

#### Scenario: Test component

- **WHEN** the component has the label `test-component`
- **THEN** the component is excluded with reason `Test component (label test-component)`

#### Scenario: Documentation component by name

- **WHEN** the component is named `DOC-User-Guide`
- **THEN** the component is excluded with reason `Documentation component`

#### Scenario: Configured exclusion

- **WHEN** the configured list holds the component with reason `Applies the old license Maven plugin`
- **THEN** the component is excluded with that reason

#### Scenario: One configured reason for several components

- **WHEN** one configured entry lists two components with one reason
- **THEN** each of them is excluded with that reason

#### Scenario: Several rules hold

- **WHEN** the component is archived and built with `WHISKEY`
- **THEN** the reason is `Archived component`

#### Scenario: A name containing doc is not excluded

- **WHEN** the component is named `docker-tools` and no other rule holds
- **THEN** the component is not excluded

### Requirement: An excluded component costs one registry call

The service SHALL decide exclusion from the component-version call alone, and SHALL NOT fetch Maven artifact patterns or version names for an excluded component.

#### Scenario: Excluded component

- **WHEN** a component is excluded
- **THEN** the registry received exactly one call for it

### Requirement: Unknown and unreachable are errors, not results

The service SHALL report an unknown component, or a version the registry does not know, as not found, and a registry that cannot be reached, answers with a server error, or is still loading its repository as unavailable; neither SHALL produce a license target or an exclusion.

#### Scenario: Unknown component version

- **WHEN** the registry answers `404` for the component version
- **THEN** the resolution fails as not found

#### Scenario: Registry unreachable

- **WHEN** the registry cannot be reached
- **THEN** the resolution fails as unavailable

#### Scenario: Registry answers 503

- **WHEN** the registry answers `503`
- **THEN** the resolution fails as unavailable

#### Scenario: Registry still loading its repository

- **WHEN** the registry answers `425`
- **THEN** the resolution fails as unavailable
