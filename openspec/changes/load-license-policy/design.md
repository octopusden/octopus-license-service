## Context

- `LicenseReportServiceImpl.start` resolves the license target, then hands it to `LicenseReportGenerator` on a background thread. `EmptyLicenseReportGenerator` returns zero counts and `GenerationInputs(licenseRegistryCommit = null, …)`.
- `core` already carries `Verdict` (`ALLOWED`, `NOT_ALLOWED`, `UNKNOWN`, `UNIDENTIFIED`) and `GenerationInputs.licenseRegistryCommit`; nothing fills them.
- The license registry is a Git repository on Bitbucket. Its default branch is `master`. Files at its root:
  - `licenses-whitelist.txt`, `licenses-hidden.txt`: one line, names separated by `|`;
  - `merges.txt`: one line per license, `|`-separated, the first name canonical;
  - `thirdparty-licenses.properties`: `group--artifact--version=license`, `version` a single version or a range such as `[2.3,2.6)`, `#` comments;
  - `licenses.properties`: `NAME=licenses/FILE.txt`, blank lines and `#` comments; `licenses/` holds the texts.
- The Maven plugin clones the repository with `git` on every build. It treats a hidden license as allowed, passes a library licensed under any allowed license, and matches override versions with `versions-api` (`VersionRangeFactory` over empty `VersionNames`, a single version wrapped as `[v]`).
- The prototype applies the same rules, and maps synonyms case-insensitively.

## Worked example

A small registry at commit `a1b2c3d` (sample content; the shapes are the registry's):

- whitelist `Apache-2.0|MIT`; hidden `BORLAND`;
- merges `Apache-2.0|The Apache Software License, Version 2.0|Apache 2` and `MIT|The MIT License`;
- override `org.example--sample-lib--[1.0,2.0)=MIT`;
- `licenses.properties` maps `Apache-2.0` to `licenses/APACHE-2.0.txt` and nothing to `MIT`.

| Library | Licenses found | Step that decides | Verdict | Licenses reported |
| --- | --- | --- | --- | --- |
| `org.example:sample-lib:1.5` | `GPL-3.0` | override `[1.0,2.0)` holds `1.5` | `ALLOWED` | `MIT` |
| `org.example:sample-lib:2.1` | `GPL-3.0` | no override; `GPL-3.0` not allowed | `NOT_ALLOWED` | `GPL-3.0` |
| `org.example:other:1.0` | `apache 2`, `GPL-2.0` | `apache 2` is `Apache-2.0`, allowed | `ALLOWED` | `Apache-2.0`, `GPL-2.0` |
| `npm` package `left-pad` | `Unknown` | nothing known left | `UNKNOWN` | none |
| `org.example:old:3.0` | `Borland` | hidden, case-insensitive | `ALLOWED` | `BORLAND` |

Loading also reports `MIT` as allowed with no text.

## Goals / Non-Goals

**Goals:**
- One module answers "what does the license registry allow, at which commit", reading only Bitbucket.
- The verdict rules are pure and give the same answer as the license plugins and the prototype.
- A report says which registry commit judged it.

**Non-Goals:**
- Judging libraries in a report: the license scan produces them.
- `UNIDENTIFIED`: decided by the scan, before the policy is asked.
- Producing license files or choosing which texts go in them.
- Editing the registry, or reporting its problems anywhere but logs and health details.

## Modules

```mermaid
flowchart TD
    server["license-service"] --> core
    server --> target["license-target-registry"]
    server --> policy["license-policy-registry (new)"]
    policy --> bitbucket["bitbucket-client (published)"]
    policy --> versions["versions-api (published)"]
```

| Module | Owns | May depend on | Must never depend on |
| --- | --- | --- | --- |
| `license-policy-registry` | The license policy model, the registry file parser, the verdict rules, the Bitbucket adapter | Bitbucket client, `versions-api` | `core`, `server`, `license-target-registry`, Spring |

## Decisions

### 1. A module of its own, reading only the license registry

- `policy-registry/` (project `:license-policy-registry`) owns turning the license registry into a policy, and judging a library with it.
- Same shape as `license-target-registry`: a pure part the adapter pushes facts into, and one adapter that does the I/O.
- It never imports `core`: it has its own `LicenseVerdict`, and `server` maps it to `core`'s `Verdict`. The scan, when it lands, judges through the policy without seeing the wire types.

### 2. Bitbucket REST, not a Git clone

- The adapter reads the files through the published `bitbucket-client` (`octopus-external-systems-client`): the default branch's head commit (`commits?limit=1`), the file list under `licenses/`, and each file's raw content.
- The service image needs no `git` binary, no disk and no SSH key; a read-only token is enough.
- Rejected: cloning with `git`, as the plugin does. It needs a writable disk and SSH or HTTPS Git credentials in the pod, for some 50 small files.
- The client brings Jackson 2 and Feign 12, like the components registry client; same handling (Decision 5 of the license target change): `feign-core` is `compileOnly`, the adapter test covers the client on the server's classpath.

### 3. One consistent commit per load

- The raw-file endpoint of the published client reads the default branch's head; it takes no commit.
- The adapter reads the head commit, then every file, then the head commit again. When the two differ, it starts over, at most three times, then fails the load.
- So the service reads the default branch only, and has no branch setting; it is the branch the license plugins clone.
- `TD-001`: add an `at` parameter to `getRepositoryRawFileContent` in `octopus-external-systems-client`, then read every file at the first commit and drop the re-check.

### 4. The parser is pure and strict on structure, lenient on content

- `LicenseRegistryParser.parse(files: Map<String, String>, commit: String): LicensePolicy`.
- Missing `licenses-whitelist.txt`, or an empty whitelist, fails the load: a policy that allows nothing would fail every library.
- A malformed line is skipped and named in the policy's `problems`, never fatal:
  - an override key without three `--` parts;
  - an override range `versions-api` cannot parse;
  - a `licenses.properties` line without `=`, or pointing at a file not in `licenses/`.
- Blank lines and `#` comments are ignored everywhere; names are trimmed.

### 5. The verdict rules, in order

```kotlin
class LicensePolicy(
    val commit: String,
    // allowed, hidden, canonical names, overrides, texts: private
) {
    fun judge(licenses: Set<String>, coordinates: MavenCoordinates? = null): Judgement
    fun text(license: String): String?
    val licensesWithoutText: List<String>
    val problems: List<String>
}

data class MavenCoordinates(val groupId: String, val artifactId: String, val version: String)

data class Judgement(val verdict: LicenseVerdict, val licenses: List<String>, val overridden: Boolean)

enum class LicenseVerdict { ALLOWED, NOT_ALLOWED, UNKNOWN }
```

1. Override: the first `thirdparty-licenses.properties` entry, in file order, whose group and artifact equal the coordinates and whose version range holds the version replaces the found licenses. A value with `|` gives several licenses.
2. Canonical names: each name is looked up case-insensitively among every name in `merges.txt`, the whitelist and the hidden list, and replaced by its canonical spelling; an unknown name keeps its own spelling.
3. Unknown markers are dropped: blank, `Unknown`, `Unknown license`, any case. Nothing left: `UNKNOWN`.
4. Any canonical name in the whitelist or the hidden list: `ALLOWED`.
5. Otherwise `NOT_ALLOWED`.

- Case-insensitive lookup is the prototype's: Xray and POMs spell the same license in several casings. The plugin compares exactly; it is stricter only where a casing differs, which the registry then has to list as a synonym.
- `licenses` in the judgement are the canonical names, sorted, so every report spells a license one way.

### 6. Version ranges with `versions-api`, as the plugin does

- `VersionRangeFactory` and `NumericVersionFactory` over `VersionNames("", "", "")`; a single version is wrapped as `[v]`.
- Ranges are parsed once, when the policy is loaded; a range that does not parse is a `problem` (Decision 4), not a failure when judging.

### 7. The server holds one policy and swaps it whole

- `LicensePolicyHolder` keeps the current `LicensePolicy` in an `AtomicReference`, null until the first load.
- `LicensePolicyRefresher` (`@Scheduled`):
  - before the first load: tries every minute;
  - after it: every 15 minutes asks for the head commit only, and reloads only when it changed;
  - a failure is logged and keeps the current policy.
- `start` reads the holder once: null raises `ServiceUnavailableException` (`503`, "license registry not loaded yet"); otherwise the generation gets that policy instance, so a reload during a generation does not change its answer.
- A readiness `HealthIndicator` is down while the holder is empty; its details show the commit, the load time, `licensesWithoutText` and `problems`.

### 8. The generator receives the policy

- `LicenseReportGenerator.generate(target: LicenseTarget, policy: LicensePolicy)`.
- `EmptyLicenseReportGenerator` still lists no library, and sets `GenerationInputs.licenseRegistryCommit` to `policy.commit`.
- The mapping from `LicenseVerdict` to `core`'s `Verdict` lives in `server`, next to the generator.

### 9. Configuration

- `license-service.license-registry.bitbucket-url`, `.project`, `.repository`, `.token`: required; a missing value fails startup, naming the property. All from Spring Cloud Config and Vault; the project and repository names stay out of the repository.
- `license-service.license-registry.refresh-interval`: default `PT15M`; `retry-interval`: default `PT1M`.

## Risks / Trade-offs

- **The service is not ready right after a deploy until Bitbucket answers.** Accepted: a report judged without a policy would be wrong; `503` and a down readiness probe say so plainly.
- **Up to 15 minutes between a registry merge and the service using it.** Accepted: the commit in each summary says which policy judged it.
- **Re-reading the head commit to get a consistent load.** A merge during a load costs a second load. Removed by `TD-001`.
- **Case-insensitive names can allow what the plugin rejects.** Only when two spellings differ in case alone, which is the same license. Accepted.
- **Two Feign and two Jackson versions on the server's classpath**, as with the components registry client. Accepted for the same reasons.
