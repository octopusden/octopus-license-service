## Why

The service resolves what a component version releases, but it cannot judge a single license yet. The policy that decides ALLOWED or NOT ALLOWED lives in the license registry, a Git repository the license plugins clone on every build.

- Without the policy, the license scan has nothing to judge its libraries against, and every report says nothing about licenses.
- A report cannot say which policy it was judged with. Two reports for one version can differ after a registry edit, and nothing explains why (`GenerationInputs.licenseRegistryCommit` is always null).
- Nobody can see which allowed licenses have no text in the registry, although the license files will need one for every license they list.

## What Changes

**Part A: the license policy, from the license registry only**

- A new module, `license-policy-registry`, turns the license registry into a license policy. It talks to Bitbucket and to nothing else.
- The policy is read from the registry's files at one commit:
  - `licenses-whitelist.txt`: allowed licenses;
  - `licenses-hidden.txt`: licenses allowed but not listed as such, treated as allowed;
  - `merges.txt`: synonyms, the first name of a line being the canonical one;
  - `thirdparty-licenses.properties`: overrides, the licenses of a Maven library within a version range;
  - `licenses.properties` and `licenses/`: the text of each license, in the registry or at a URL.
- Both properties files are read with `java.util.Properties`, as the license plugins read them.
- The policy records the commit it was read at.

**Part B: a verdict for one library**

- The policy judges one library from the licenses it was found with, and from its Maven coordinates when it has them:
  1. an override for the coordinates replaces the licenses;
  2. every license name becomes its canonical name;
  3. no known license left: `UNKNOWN`;
  4. any license allowed or hidden: `ALLOWED`, so a dual-licensed library passes on either license;
  5. otherwise `NOT_ALLOWED`.
- The judgement gives the canonical license names, so the license files list one spelling per license.
- `UNIDENTIFIED` (a file nobody could name) is not the policy's answer: a library without an identity never reaches the policy. The license scan assigns it.
- The rules are the license plugins': overrides first, the last matching override in sorted key order wins, version ranges are parsed with `versions-api`, then synonyms, then the allowed and hidden lists.
- One deliberate difference: license names are compared case-insensitively, as in the prototype; the plugins compare them exactly. Xray and POMs spell one license in several casings.
- A license name on two `merges.txt` lines fails the plugins' build; the service keeps the first line and reports the other.

**Part C: kept in memory, refreshed on a new commit**

- The service loads the policy in the background at startup, then checks the registry's default branch every 15 minutes and reloads only when its head commit changed.
- A failed load or reload keeps the policy it has; the failure is logged.
- Until the first load succeeds, `POST /license-reports` answers `503`, and the readiness probe is down.
- Each generation takes the policy current when it starts, and its summary records that policy's commit.
- Allowed licenses with no text are logged on every load, and shown in the health details.

## Affected areas

- New module `license-policy-registry`: the policy model, the registry file parser, the verdict rules and the Bitbucket adapter. Depends on the published Bitbucket client and `versions-api`; never on `core`, `server` or Spring.
- `server`:
  - holds the current policy and refreshes it on a schedule;
  - `LicenseReportService.start` answers `503` until a policy is loaded;
  - the generator receives the policy with the target, and records its commit in `GenerationInputs`.
- **Behaviour change for callers:** `POST` can answer `503` right after a deploy, until the first load. The summary's `licenseRegistryCommit` is set. Nothing calls the service in production yet.

## Out of scope

- Judging real libraries in a report: there are none until the license scan lands; the report keeps zero counts.
- `UNIDENTIFIED`: assigned by the license scan to files with no identity.
- Writing license files and their texts: the license files change; this one only loads the texts and checks they exist.
- Fixing license names or adding missing texts in the registry: the registry's own work (license registry validation).
- Reading a branch other than the registry's default one, or pinning a commit per request: one policy at a time, from the branch the license plugins read.

## Rollout note

- No feature flag. The Bitbucket URL, token, project and repository come from Spring Cloud Config; the project and repository names stay out of this public repository.
- A missing setting fails startup. An unreachable Bitbucket does not: the service starts, answers `503` and retries every minute until the first load succeeds.
