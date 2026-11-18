## Purpose

What a finished license report's summary says about the libraries of a component version and how far that list can be trusted, and how a generation fails when the systems it reads cannot be asked. The license files are outside this spec.

## ADDED Requirements

### Requirement: The summary carries the component's trust and each file's

A finished report's summary SHALL carry `trust`, never null:

- its `level`;
- its `reasons`, each with `code`, `level`, `title`, `detail`, `meaning` and `fix`, in the grade's order;
- `files`: for each selected released file, its entry, level and reason codes, in listing order.

#### Scenario: Low trust

- **WHEN** a report of `sample-portal:2.4.0` finishes with `PARTIAL_ARTIFACTS` and `UNKNOWN_LICENSES`
- **THEN** `trust.level` is `LOW`
- **AND** both reasons are listed with their level, detail and fix
- **AND** `trust.files` holds each selected file's level

#### Scenario: Trusted

- **WHEN** no trust rule gives a reason
- **THEN** `trust.level` is `HIGH` and `trust.reasons` is empty

### Requirement: The generation grades with the target's labels

The service SHALL grade the evaluated assessment with the components registry labels of the license target the report was started for.

#### Scenario: Labels reach grading

- **WHEN** the target of `sample-portal:2.4.0` has the label `javascript`
- **THEN** grading checks for `npm` libraries

### Requirement: The runner shows trust and never fails on it

The runner SHALL log:

- the trust level;
- each reason with its level, title, detail and fix;
- each released file graded below `HIGH`, with its level and codes.

These lines SHALL be warnings when the level is `LOW` or `NONE`. The runner's outcome SHALL NOT depend on trust.

#### Scenario: Low trust in a build

- **WHEN** a report finishes with trust `LOW`
- **THEN** the build log shows the reasons as warnings and the step does not fail

## REMOVED Requirements

### Requirement: Trust is not computed yet

Replaced by "The summary carries the component's trust and each file's".
