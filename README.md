# octopus-license-service

Generates third-party license files for a component version from JFrog Xray and Artifactory data, checked against a license registry.

## Modules

| Module | Directory | Ships as |
| --- | --- | --- |
| `core` | `core/` | Wire DTOs and exceptions, Maven Central `org.octopusden.octopus.license:core` |
| `client` | `client/` | Feign client, Maven Central `org.octopusden.octopus.license:client` |
| `license-service` | `server/` | Spring Boot service, docker image `ghcr.io/octopusden/license-service` |
| `license-runner` | `runner/` | Command-line runner (`-all` jar) and its TeamCity metarunner, GitHub Packages |
| `license-target-registry` | `engine/target-registry/` | Not published; part of the service. Resolves what a component version releases, from the components registry |

Which module may depend on which, and how a request flows: [docs/architecture.md](docs/architecture.md).

## API

A generation can take minutes, so the caller starts it, polls it and downloads the result. A finished report is kept in memory for `license-service.report-ttl` (one hour by default), then dropped; once the kept license files pass `license-service.max-stored-bytes` (512 MB), the oldest finished reports go early. At most `license-service.generation-threads` (4) generations run and `license-service.max-queued-reports` (50) wait; past that, a new report is refused. The API needs no authorization.

| Call | Answer |
| --- | --- |
| `POST /rest/api/1/license-reports` with `{"component", "version"}` | `202` with the report (status `RUNNING`) and its URL in `Location`; `400` for a blank component or version; `404` for a component version the components registry does not know; `503` with `Retry-After` when the queue is full, `503` without it when the components registry is unreachable |
| `GET /rest/api/1/license-reports/{id}` | the report: `RUNNING`, `DONE` (with the summary), `EXCLUDED` (with the reason) or `FAILED` (with the error); `404` for an unknown or expired id |
| `GET /rest/api/1/license-reports/{id}/files` | the license files as one zip; `409` unless the report is `DONE` |

Errors use the envelope `{"error": {"code": 404, "message": "..."}}`. The OpenAPI description is at `/v3/api-docs`, Swagger UI at `/swagger-ui/index.html`.

## Running locally

JDK 25 is needed; the Gradle toolchain downloads one if none is installed.

```bash
./gradlew :license-service:bootRun
```

The `dev` profile switches Spring Cloud Config off, so the service starts with no config server. Then run the runner against it:

```bash
./gradlew :license-runner:shadowJar
java -jar runner/build/libs/license-runner-*-all.jar --url http://localhost:8080 --component my-component --version 1.0.0
```

The runner writes `license-report.json` and the zip to `--output-dir` (`license-report` by default), prints the summary and always exits 0: it only reports. Problems are printed with a `WARNING:` prefix. Publishing the output directory, as TeamCity artifacts for example, is the caller's job.

## Building

```bash
./gradlew build
./gradlew qualityCheck
```
