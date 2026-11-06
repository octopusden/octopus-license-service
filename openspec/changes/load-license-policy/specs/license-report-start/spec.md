## Purpose

What `POST /license-reports` and a finished report say about the license policy. It governs when a report can start and which policy a report records; the verdicts of individual libraries are in the license policy spec.

## ADDED Requirements

### Requirement: A report starts only once a policy is loaded

The service SHALL answer `503` with the error envelope, creating no report, while no license policy has been loaded since startup; its readiness probe SHALL be down for that time.

#### Scenario: Before the first load

- **WHEN** a client starts a report before the license registry has been read once
- **THEN** the answer is `503` and the client raises the service-unavailable exception

#### Scenario: After the first load

- **WHEN** the license registry has been read once
- **THEN** a report for a known, not excluded component version starts with `202`

### Requirement: A report records the policy commit it was judged with

The service SHALL give each generation the policy current when its report started, and SHALL set the summary's `licenseRegistryCommit` to that policy's commit.

#### Scenario: Commit in the summary

- **WHEN** a report starts while the loaded policy is at `a1b2c3d`
- **THEN** its summary's `licenseRegistryCommit` is `a1b2c3d`

#### Scenario: Reload during a generation

- **WHEN** a newer commit is loaded while a generation runs
- **THEN** that generation's summary still records the commit it started with

### Requirement: The policy is refreshed only when the registry changes

The service SHALL check the head of the registry's default branch on a fixed interval, SHALL reload the policy only when the head changed, and SHALL keep the current policy when a check or reload fails.

#### Scenario: Head unchanged

- **WHEN** the head is still the loaded commit
- **THEN** no file is read

#### Scenario: Reload fails

- **WHEN** the head changed and reading the files fails
- **THEN** reports keep using the previous policy, and the failure is logged

### Requirement: The license registry settings are required

The service SHALL fail to start when the Bitbucket URL, token, project or repository of the license registry is not configured, naming the missing property.

#### Scenario: Token missing

- **WHEN** the service starts without `license-service.license-registry.token`
- **THEN** startup fails, naming the missing property
