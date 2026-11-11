## Why

The service knows what a component version declares (the license target) and how to judge one named library (the license policy), but not which libraries actually ship in its released files. Without that list there is nothing to judge, and every report stays empty.

The list comes from the prototype that scanned all 559 external components (`xray_licence_scan.py`, run 14 on TeamCity). Its algorithm is settled; what is missing is a production implementation that gives **the same list for the same inputs**, so the prototype's results stay the baseline for the rollout.

This change ports the first half of that algorithm: from the license target to the license inventory, every released file and every library found in it, with the licenses its sources name. Judging those libraries (ours or third party, allowed or not) and grading trust come in the next change.

## What Changes

**Part A: which files a version releases (prototype stages 5–7)**

- The component's own builds are found by the naming convention `{component}_{e|i}e`, plus `_docker`, at each artifact version of the target, first found wins.
- Every file those builds published is listed. A file is read only when it is declared in the registry's `GAV` or `docker` field, matches a Maven artifact pattern, or is an image a convention build published. Poms, sources, javadoc and Gradle module files are listed but not read, unless declared.
- Declared entries no convention build produced are looked up in Artifactory by path; their build is found by the file's `build.name`/`build.number` properties, then by checksum.
- A declared file that does not exist is `NOT_FOUND`; a file share or malformed entry is `UNSUPPORTED`.

**Part B: what is inside each file (stages 8–9)**

- Each released file is read from every source it has: Xray's scan of the file, and when its build is known, the build's SBOM files, its npm production dependencies and the producing module's shipped dependencies.
- A file Xray has not indexed is still read from its build (`BUILD_RECORD` only). A plain text file Xray does not index is `PLAIN_FILE` and is not read. A file with neither Xray nor a build is `NOT_READABLE`.
- Our own archives inside a released file are followed into their own build, recursively, from a file at depth 10 or less. An inner archive Xray finds no library in is data: only Xray's list counts for it.
- Before reading a file's scan, the service waits for Xray to finish scanning it, up to 5 minutes per report. It then reads the scan whatever the status; a file Xray answers an error for is read from its build only, as the prototype reads any file Xray has no scan of.

**Part C: naming and licenses (stage 10)**

- A file Xray knows only by checksum, and that is not ours, is renamed to Maven coordinates when the same checksum is stored under a Maven path anywhere in Artifactory.
- A library with no license yet is looked up in Xray by its coordinates (Maven and npm only, 100 per call).

**Part D: the clients, in octopus-external-systems-client**

- A new `xray-client` module: scan status of an artifact, the summary of an artifact, the summary of components.
- `artifactory-client` gains what the scan reads: checksums on AQL items, AQL over builds, and the path of a build-info artifact. Additive only; no existing consumer changes.

**Shipping together with evaluation**

- This change and the evaluation change go to QA and Prod together; they are split only to keep each one small enough to review.
- So this change does not touch the server. The evaluation change wires the scanner (JFrog URL and token from Vault; ownership rules from Spring Cloud Config) and maps its result into the report.

### A note on parity with the prototype

- The goal is the same inventory as the prototype for the same Artifactory and Xray state. Every rule, threshold and fallback is ported as it is, including the ones that look odd (see design, Decision 10).
- A parity check, outside the normal build, runs the scanner over the components of a prototype run and reports every difference per file and per library.
- The few deliberate differences are listed in the design (Decision 11), each with the components it can affect.

## Affected areas

- New `engine/` module `license-inventory` (`engine/inventory/`): the inventory model, the scan, and its Artifactory and Xray adapters. Depends on `license-target-registry` (for `LicenseTarget`), `artifactory-client` and `xray-client`; never on `core`, `server`, Spring or `license-policy-registry`.
- `license-target-registry`: `LicenseTarget` gains `explicit`, which the build naming convention needs. Read from the same registry call; no new call.
- `server`: unchanged; wired by the evaluation change (design, Decision 15).
- `octopus-external-systems-client`: new `xray-client` module; additive DTO fields and one AQL method in `artifactory-client`. Published before the service can build.
- `docs/architecture.md`: the license scan module is no longer "later".
- **No behaviour change for callers:** the API, the summary and the files are unchanged until the evaluation change.

## Out of scope

- Ownership verdicts, license verdicts, excluding OS packages and base-image Python: evaluation, the next change. The inventory keeps every package found, including ours and OS packages, as the prototype's rows do before stage 11.
- Trust levels and their reasons: grading, after evaluation. The inventory records the facts they need (file statuses, evidence, builds, findings).
- Writing license files: a later change.
- Server wiring, configuration and the scan counter: the evaluation change, which ships with this one.
- Choosing the version: the caller names it; the prototype's "latest RELEASE, else RC, else BUILD" is a batch concern.
- Caching across reports: each scan reads fresh, so a report reflects Artifactory and Xray when it ran.
- Asking Xray to scan files it has never indexed (old versions): deferred, waiting on the Xray administrator.
