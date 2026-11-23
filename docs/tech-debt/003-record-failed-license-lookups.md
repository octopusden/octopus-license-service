# TD-003: record a failed license lookup, so it never reads as an unknown license

## Status

Open. **Should do** before `--fail-on-unknown` is switched on for a component whose builds see it.

## Context

The scan asks Xray's component summary for the licenses of build-record and SBOM packages, in batches. A batch that fails leaves its packages without licenses, as the prototype does (`openspec/changes/scan-license-inventory/design.md`, D9). Evaluation then judges them `UNKNOWN`.

The runner's `--fail-on-unknown` fails a build on an `UNKNOWN` library. A regular build's quick check marks a violation preliminary only when Xray has scanned none of its files (`openspec/changes/write-license-files/design.md`, Decision 11); a package in a file Xray did scan, whose license lookup failed, is not marked. So a short Xray hiccup can fail a build that has `--fail-on-unknown` on.

## What to do

- The scan records which packages' license lookup failed.
- The summary marks those violations preliminary in a quick check, and the runner never fails on them; a release fails on no answer anyway.

## Acceptance

- A failed lookup batch in a quick check gives preliminary `UNKNOWN` violations, and `--fail-on-unknown` passes.
- A real `UNKNOWN` (lookup answered, no license) still fails.

## Risk

Today: can over-report. A false `UNKNOWN` is possible, never a missed one.
