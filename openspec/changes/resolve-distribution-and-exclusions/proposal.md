## Why

The service answers every component version with an empty report, whatever the caller sends. It does not know what a version releases, and it cannot tell a typo from a real component.

- A caller who mistypes a component or version polls a report that ends DONE and empty, instead of being told the request is wrong.
- Archived, internal, documentation, test and WHISKEY components, and components that still apply the old license plugins, get the same empty DONE report as everyone else. They should be told they are excluded and why.
- The license scan needs to know what a version releases before it can look inside anything. That knowledge lives in the components registry and nowhere else.

## What Changes

**Part A: the license target, from the components registry only**

- A new module, `license-target-registry`, turns a component version into its license target. It talks to the components registry and to nothing else.
- The license target holds:
  - the component version;
  - its build system and labels;
  - the declared artifacts, from the registry's `GAV` and `docker` distribution fields for that version;
  - the Maven artifact patterns (group and artifact patterns) whose version range holds that version.
- `DEB` and `RPM` distribution fields are not read.
- The license target stays inside the service. It is the input of the license scan; callers never see it, and the report and its summary do not change.
- Turning declared artifacts and patterns into concrete files, through Artifactory and build-info, belongs to the license scan.

**Part B: validation and exclusion at start**

- `POST /license-reports` resolves the license target synchronously before anything runs in the background:
  - unknown component, or a version the registry does not know: `404`, no report created;
  - registry unreachable: `503`, no report created;
  - excluded component: the report is created already `EXCLUDED`, with the reason; nothing runs in the background;
  - otherwise: `202` `RUNNING`, and the generation runs in the background with the license target.
- A component is excluded when any of these holds, checked in this order, the first match giving the reason:
  - it is archived;
  - it is internal: its distribution is not `external`;
  - its build system is `WHISKEY`;
  - it has the registry label `test-component`;
  - it is a documentation component: its name starts with `doc_` or `doc-` (any case);
  - it is on the exclusion list in configuration, each entry with its own reason.
- The exclusion list in configuration starts with the documentation components whose names do not follow the `doc_`/`doc-` rule. Components that still apply the old license plugins are added to it as a configuration change; nothing detects them.

## Affected areas

- New module `license-target-registry`: the license target model, the exclusion rules, and the components registry adapter. Depends on the published components registry client; never on `core`, `server` or Spring.
- `core`: a new exception for "a system the service depends on is unreachable", mapped to `503` by the server and back by the client.
- `server`:
  - wires the module: the registry client from configuration, the exclusion list from configuration;
  - `LicenseReportService.start` resolves the license target before it registers a report;
  - the generator receives the license target instead of a bare component and version.
- **Behaviour change for callers:** `POST` can now answer `404` and `503`, and can return a report that is already `EXCLUDED`. The runner already handles `EXCLUDED` and error responses; nothing calls the service in production yet.

## Out of scope

- Finding the concrete files in Artifactory, by AQL or build-info: part of the license scan, which needs Artifactory anyway.
- `DEB` and `RPM` distribution fields: not covered by the license scan.
- "A version with no build": the registry cannot tell whether a build exists; that is known only once Artifactory is read, in the license scan.
- Detecting components that apply the old license plugins: the registry has no such field; the configuration list covers it.
- Caching registry answers: the service is stateless for now; one start costs up to three registry calls: the component version, its Maven artifact patterns, and the version names that parse their ranges; an excluded component costs one.

## Rollout note

- No feature flag. The registry URL comes from Spring Cloud Config, like the rest of the service's configuration.
- A missing registry URL fails startup, so a misconfigured environment is caught on deploy, not on the first report.
