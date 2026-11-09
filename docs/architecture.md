# Architecture

The license service turns a component version into its third-party license files. This page says which module owns what and how a request flows. Class-level detail is left to the code.

## Modules

```mermaid
flowchart TD
    runner --> client
    portal["portal (another repository, later)"] -.-> client
    client --> core
    server --> core
    server --> target
    server --> policy
    server -.-> scan
    subgraph engine["engine/"]
        target["license-target-registry"]
        policy["license-policy-registry"]
        scan["license scan (later)"]
    end
    target --> crs["components registry client (published)"]
    policy --> bitbucket["Bitbucket client (published)"]
    scan -.-> policy
    scan -.-> xray["Xray, Artifactory"]
```

An arrow means "depends on". Dashed parts do not exist yet.

`engine/` holds the modules that produce a report: what a version releases, the license policy, and the scan that finds each file's licenses. It is a folder, not a module: each part is its own Gradle project, so each reaches only the systems it reads.

| Module | Owns | May depend on | Must never depend on |
| --- | --- | --- | --- |
| `core` | The wire contract: request, report, summary, error envelope, exceptions | nothing | anything |
| `client` | The Feign client for the API | `core` | `server` |
| `runner` | The command line that starts a report, waits for it and writes the files | `client` | `server`, `engine/*` |
| `server` | The Spring Boot service | `core`, `engine/*` | `client`, `runner` |
| `engine/target-registry` (`:license-target-registry`) | What a component version releases, or why it is excluded, from the components registry | the components registry client, `versions-api` | `core`, `server`, Spring, any other system |
| `engine/policy-registry` (`:license-policy-registry`) | The license policy, read from the license registry through Bitbucket, and the verdict on one named library | the Bitbucket client, `versions-api` | `core`, `server`, Spring, any other system |
| `engine/` license scan (later) | Finding each released file's libraries and licenses through Xray and Artifactory, judged by the license policy | Xray and Artifactory clients, the other `engine/` models | `core`, `server`, Spring |

Why each rule:

- **`core` depends on nothing**, because every caller imports it: whatever it depends on, every caller pulls in.
- **`client` and `runner` never see `server` or `engine/`.** A caller needs the HTTP contract, not Spring Boot and Tomcat. The runner talks to the service over HTTP like any other caller.
- **`server` never sees `client` or `runner`.** The service does not call itself and knows nothing about who calls it.
- **`engine/` modules never import `core` or Spring.** They work on their own models, richer than the wire; `server` builds them from configuration and maps their results into `core`.
- **Each `engine/` module reads only its own systems.** `license-target-registry` answers "what the registry declares", so it never needs Artifactory or Xray, and the scan builds on its answer instead of asking the registry again.
- **The scan's logic stays apart from its I/O.** Data goes in and a result comes out, so the algorithm is testable without mocks, the way `component-validation` is in octopus-components-registry-service. `license-target-registry` and `license-policy-registry` already have that shape: `impl/describer/` and `impl/parser/` with `LicensePolicy` are pure; `impl/RegistryLicenseTargetResolver` and `impl/BitbucketLicensePolicySource` are the only I/O.

Gradle enforces the dependency rules: a module cannot import what its build file does not declare. This page holds the reasons.

## Request flow

```
TeamCity step (metarunner)
  runner ──client── POST /rest/api/1/license-reports ──► controller → service
                                                           ├─ license policy, as loaded in the background
                                                           │    none loaded yet → 503, nothing stored
                                                           ├─ license target, from the components registry
                                                           │    unknown → 404, unreachable → 503, nothing stored
                                                           │    excluded → store: report EXCLUDED, done
                                                           ├─ store: report RUNNING
                                                           └─ generator, on a background thread, with the target and that policy
                                                                └─ later: Xray, Artifactory
  runner ──client── GET …/{id} until DONE, EXCLUDED or FAILED
  runner ──client── GET …/{id}/files ──► zip
  runner writes license-report.json and the zip to --output-dir
```

Only `core` types cross the wire, in both directions.

Errors become `{"error": {"code", "message"}}` in one `@RestControllerAdvice`.

## Inside the server

The server's packages are grouped by role, so a new piece has one obvious place:

```
org.octopusden.octopus.license.service
├── controller/      HTTP only: LicenseReportController, advice/ExceptionInfoHandler
├── service/         the use case: LicenseReportService, LicenseReportGenerator, and their impl/
├── state/           what the server keeps in memory, and the jobs that keep it current
│   ├── report/      LicenseReportStore and ExpiredReportEvictor: reports until their TTL ends
│   └── policy/      LicensePolicyHolder and LicensePolicyRefresher: the current license policy
├── health/          health indicators; they read state and change nothing
└── configuration/   properties and wiring
```

| Package | May use | Never uses |
| --- | --- | --- |
| `controller` | `service.LicenseReportService`; only `core` types in and out | `state`, `engine/`, the generator |
| `service`: the report service | `state.report` (add, finish, find), `state.policy` (`current()`, read once per report), `engine/` `LicenseTargetResolver`, the generator | `controller`, `health` |
| `service`: the generator | only what it is given: the license target and the policy; later the scan | `state`: a reload or an eviction never changes a running generation |
| `state` | `engine/` models; its own `@Scheduled` job | `service`, `controller`, `health`: state never calls back into the use case |
| `health` | `state`, read-only | anything that writes |
| `configuration` | everything, to wire it | — |

Dependencies point one way: `controller → service → state`, and `health → state`. Nothing hides `state` behind an interface: the store and the holder have one implementation each, and the tests use them as they are.

What each part does on a request:

- the controller only translates HTTP;
- the service checks the request, takes the current license policy, resolves the license target, registers the report and runs the generation in the background;
- the generator produces the result from what it was given;
- the report store keeps reports in memory until their TTL ends;
- the policy refresher loads the license policy in the background: every minute until the first load succeeds, then every 15 minutes it checks the registry's head commit and reloads only when it moved; a failed reload keeps the policy it has;
- a pod without a policy stays alive, answers `503` and gets no traffic (see Health and metrics).

## Health and metrics

- **Readiness covers only what this pod holds.** A pod that cannot serve until it has loaded something is unready until it has it: the `licenseRegistry` indicator is down until the license policy is in memory, and up from then on, whatever Bitbucket does later. Liveness stays `livenessState` only: a restart fixes nothing outside the pod.
- **A per-request dependency is never a health indicator.** The components registry, and later Xray and Artifactory, are called per request and shared by every pod. In readiness, an outage would turn every pod unready at once, and block even `GET` of reports already running.
- **Instead, an outage answers `503` with a message, and is counted.** `license.target.resolutions{outcome}` (`license_target_resolutions_total` in Prometheus) counts each resolution as `resolved`, `excluded`, `not_found` or `unavailable`; an alert watches `unavailable`. A later dependency gets its own counter the same way.
- **A background dependency is counted the same way.** The license policy is refreshed from Bitbucket in the background; once a policy is loaded readiness stays up, so a failing refresh shows only as a metric. `license.policy.refreshes{outcome}` (`license_policy_refreshes_total`) counts each refresh that asks Bitbucket as `loaded`, `unchanged`, `unavailable` or `invalid`; an alert watches `unavailable` and `invalid`.
- **Metrics live in `server`.** `engine/` modules know nothing of Micrometer; the server counts what they answer or throw.

## Boundaries

**Narrow at the edges, never in the middle.** `LicenseReportSummary` is built for callers. The scan algorithm works on much more: every released file, checksums, nested archives, build-info, and the reasons behind a trust level.

- **`engine/` has its own model.** It never uses the `core` DTOs. Otherwise every wire change would ripple into the algorithm, and the algorithm would see only what callers are allowed to see.
- **`engine/` reads the external systems; the server maps.** The server passes the license target into the scan, and maps the scan's result into `core` types and the zip.
- **The `LicenseReportGenerator` implementation is that mapping.** Today it returns an empty `core` summary directly, because no scan exists yet.

The service stores nothing but the current license policy. Each result records its inputs (license registry commit, generation time, service version), so a difference between two results for one version can be explained later.

