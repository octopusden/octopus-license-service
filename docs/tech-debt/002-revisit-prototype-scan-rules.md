# TD-002: revisit the prototype's scan rules once parity is shown

## Status

Open. **Should do**, after the parity check passes for all components in scope.

## Context

`engine/inventory` ports the prototype's license scan rule for rule, so its run-14 results stay the baseline (`openspec/changes/scan-license-inventory/design.md`, Decision 10). Some of those rules look wrong on their own:

- a docker image of the component's docker build is read whether the registry declares it or not;
- SBOMs and npm dependencies of a build are counted for every file of that build;
- a file gets every shipped dependency of the module that published it, because build-info records dependencies per module, not per file: a module publishing a jar and a zip gives both the same list (`requestedBy` names the dependency that pulled another in, not the file that holds it, so it does not settle this);
- a package in several SBOMs of one build keeps the last SBOM's licenses, not their union;
- naming a checksum-only file drops the licenses Xray gave it;
- when several declared images share a name, a convention image matches only the first;
- the Xray summary lists only packages filed under a license, so one with no license entry is not found;
- the producing module is the first with a file of the same sha1 or the same name, so a same-named file in an earlier module wins;
- a build file named `mybom.json` is read as an SBOM, and an SBOM's nested components are not read;
- a dependency's classifier is ignored, for the nested archive looked up and for the package listed.

## Why this matters

Each can add or drop a library from a report, or give it fewer licenses than its sources know. Changing them before parity is shown would make every difference from the prototype ambiguous: a bug or a fix.

## What it takes

One change per rule, each measured with `./gradlew :license-inventory:parityTest` before and after, so the report names the components it moves.
