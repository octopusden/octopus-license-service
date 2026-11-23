# TD-004: store the link-only license texts in the license registry

## Status

Open. **Should do**; needs the license registry's owners.

## Context

Four licenses in `licenses.properties` give their text only as a URL: `H2-1.0`, `GWT`, `SBCLA`, `oswego-concurrent`. The service downloads them for each full report (`LicenseTextDownloader`, `openspec/changes/write-license-files/design.md`, Decision 7). That is the service's only need for outbound internet access, and an unreachable host leaves the text out of the files.

## What to do

- Store the four texts under `licenses/` in the registry and point `licenses.properties` at them.
- Then remove `LicenseTextDownloader`, `license-service.license-texts.*`, the `license.texts.downloads` metric and `DOWNLOAD_FAILED`.

## Acceptance

- No `http` value in `licenses.properties`.
- A full report's files hold every whitelisted license's text without a network call.

## Risk

Today: a text can be missing from the files when its host is unreachable; the summary and the build log say so.
