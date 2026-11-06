## Purpose

How the service reads the license policy from the license registry, and how the policy judges one library. It governs the verdict of a library that has a name; finding libraries, and naming files nobody could identify, are outside it.

## ADDED Requirements

### Requirement: The policy is read from the license registry at one commit

The service SHALL read the license policy from the default branch of the license registry only, and SHALL record the commit it read every file at.

#### Scenario: Files read at the head commit

- **WHEN** the branch head is `a1b2c3d` during the whole load
- **THEN** the policy's commit is `a1b2c3d`

#### Scenario: The branch moves during a load

- **WHEN** the branch head changes between the start and the end of a load
- **THEN** the load starts over, and the policy holds the files of a single commit

#### Scenario: No whitelist

- **WHEN** the registry has no `licenses-whitelist.txt`, or it names no license
- **THEN** the load fails and no policy is produced

### Requirement: Malformed registry lines are skipped and reported

The service SHALL skip a malformed override or license text line, keep the rest of the policy, and list each skipped line among the policy's problems.

#### Scenario: Override key without a version

- **WHEN** `thirdparty-licenses.properties` holds `org.example--sample-lib=MIT`
- **THEN** that line is a problem and every other override applies

#### Scenario: Unparseable version range

- **WHEN** an override's version is `[1.0,`
- **THEN** that line is a problem and the load succeeds

### Requirement: An override replaces the licenses of a Maven library

The service SHALL replace the licenses found for a library with those of the first override, in file order, whose group and artifact equal the library's and whose version or version range holds its version.

#### Scenario: Version in the range

- **WHEN** the override is `org.example--sample-lib--[1.0,2.0)=MIT` and the library is `org.example:sample-lib:1.5` found with `GPL-3.0`
- **THEN** the judgement uses `MIT`, says the licenses were overridden, and is `ALLOWED`

#### Scenario: Version outside the range

- **WHEN** the same override and the library is `org.example:sample-lib:2.1` found with `GPL-3.0`
- **THEN** the judgement uses `GPL-3.0` and is `NOT_ALLOWED`

#### Scenario: Single version

- **WHEN** the override is `org.example--sample-lib--1.5=MIT`
- **THEN** it applies to `1.5` only

#### Scenario: Several licenses in one override

- **WHEN** the override's value is `MIT|Apache-2.0`
- **THEN** the judgement uses both

#### Scenario: Library without Maven coordinates

- **WHEN** a library is judged without coordinates
- **THEN** no override applies

### Requirement: License names are canonical and case-insensitive

The service SHALL replace every license name with its canonical spelling, matching case-insensitively against the names in `merges.txt`, the whitelist and the hidden list; the first name of a `merges.txt` line is the canonical one. A name found nowhere SHALL keep its own spelling.

#### Scenario: Synonym

- **WHEN** a library is found with `The Apache Software License, Version 2.0`
- **THEN** the judgement lists `Apache-2.0`

#### Scenario: Different casing

- **WHEN** a library is found with `apache 2`
- **THEN** the judgement lists `Apache-2.0`

#### Scenario: Unknown name

- **WHEN** a library is found with `Some Custom License`
- **THEN** the judgement lists `Some Custom License` and is `NOT_ALLOWED`

### Requirement: The verdict follows the allowed and hidden lists

The service SHALL judge a library `UNKNOWN` when no license is known after dropping blank, `Unknown` and `Unknown license` in any case; `ALLOWED` when any canonical license is in the whitelist or the hidden list; and `NOT_ALLOWED` otherwise.

#### Scenario: No license

- **WHEN** a library is found with no license, or only `Unknown`
- **THEN** the verdict is `UNKNOWN`

#### Scenario: Dual license, one allowed

- **WHEN** a library is found with `GPL-2.0` and `MIT`
- **THEN** the verdict is `ALLOWED`

#### Scenario: Hidden license

- **WHEN** a library is found with a license on the hidden list only
- **THEN** the verdict is `ALLOWED`

#### Scenario: Nothing allowed

- **WHEN** a library is found with `GPL-3.0` only
- **THEN** the verdict is `NOT_ALLOWED`

### Requirement: Allowed licenses without a text are known

The service SHALL know, for every load, which allowed licenses have no text in the registry, and SHALL report them in its log and health details.

#### Scenario: Allowed license with no text

- **WHEN** `MIT` is allowed and `licenses.properties` has no entry for it
- **THEN** the policy lists `MIT` among the licenses without text

#### Scenario: Text entry pointing at a missing file

- **WHEN** `licenses.properties` maps `MIT` to `licenses/MIT.txt` and `licenses/` has no such file
- **THEN** the line is a problem and `MIT` is a license without text
