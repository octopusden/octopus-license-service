## Purpose

What `POST /license-reports` answers, now that it resolves the license target before anything runs in the background. It governs the start call only; polling and downloading are unchanged.

## ADDED Requirements

### Requirement: A report starts only for a component version the registry knows

The service SHALL resolve the license target synchronously when a report is started, and SHALL answer `404` with the error envelope, creating no report, when the component or version is unknown.

#### Scenario: Unknown component

- **WHEN** a client starts a report for a component the registry does not know
- **THEN** the answer is `404` and polling any id for it finds nothing

#### Scenario: Known component version

- **WHEN** a client starts a report for a component version that is not excluded
- **THEN** the answer is `202` with status `RUNNING`, and the generation receives that version's license target

### Requirement: An unreachable registry fails the start

The service SHALL answer `503` with the error envelope, creating no report, when the components registry cannot be reached; the client SHALL raise a distinct exception for it.

#### Scenario: Registry down

- **WHEN** a client starts a report while the registry is unreachable
- **THEN** the answer is `503` and the client raises the service-unavailable exception

### Requirement: An excluded component gets an EXCLUDED report at once

The service SHALL create the report of an excluded component already `EXCLUDED`, with the exclusion reason and no license files, and SHALL NOT run a generation for it.

#### Scenario: Documentation component

- **WHEN** a client starts a report for `doc_user_guide`
- **THEN** the answer carries status `EXCLUDED` and reason `Documentation component`, the generator is not called, and downloading its files answers that it has none

### Requirement: The registry URL is required

The service SHALL fail to start when the components registry URL is not configured.

#### Scenario: URL missing

- **WHEN** the service starts without `license-service.components-registry-url`
- **THEN** startup fails, naming the missing property
