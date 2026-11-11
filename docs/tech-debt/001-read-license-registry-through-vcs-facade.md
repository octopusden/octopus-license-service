# TD-001: read the license registry through octopus-vcs-facade, not Bitbucket directly

## Status

Open. **Must do.** The license policy is read today by `BitbucketLicensePolicySource`, which talks to Bitbucket itself. It works, but it ties the service to one VCS provider.

## Context

`engine/policy-registry` loads the license policy from the license registry, a Git repository. `impl/BitbucketLicensePolicySource` does it with the published Bitbucket client from `octopus-external-systems-client`:

- the head commit of the default branch (`commits?limit=1`);
- the file list at that commit (`files?at=`);
- each file's raw content (`raw/{path}`), which the client reads at the default branch's head only, so the source reads the head again after the files and starts over when it moved.

Octopus already runs `octopus-vcs-facade`: one REST API over Bitbucket and Gitea, used by releng, the Jira VCS plugin and others. It can resolve a ref to a commit, but it cannot list files or read a file at a commit.

## Why this matters

- **Provider lock-in.** The service talks Bitbucket's REST API. Moving the license registry to Gitea, or any other backend vcs-facade supports, means rewriting the source.
- **Credentials in the wrong place.** The license service holds its own Bitbucket token in Vault. Through vcs-facade it would hold none; the facade's account already reads repositories.
- **A workaround for a client gap.** The head re-check loop (Design, Decision 3) exists only because the Bitbucket client cannot read a raw file at a commit. Reading through vcs-facade pinned to one hash removes it.

## Decision

Ship `BitbucketLicensePolicySource` now; replace it with a source that reads through `octopus-vcs-facade`, once the facade can list and read files at a commit.

## What it takes

In order, each its own pull request:

1. **`octopus-external-systems-client`**
   - Bitbucket: an `at` parameter on `getRepositoryRawFileContent`.
   - Gitea: list a tree recursively (`GET /repos/{owner}/{repo}/git/trees/{sha}?recursive=true`) and read a raw file at a ref (`GET /repos/{owner}/{repo}/raw/{filepath}?ref={sha}`).
2. **`octopus-vcs-facade`**
   - `GET rest/api/2/repository/files?sshUrl=&hashOrRef=&path=`: the file paths under `path` at the commit, recursively, with the resolved commit hash.
   - `GET rest/api/2/repository/file?sshUrl=&hashOrRef=&path=`: one file's raw content at the commit.
   - Both on `VcsService`, implemented for Bitbucket and Gitea, exposed in `VcsFacadeClient`, covered by the `ft` suite against its Bitbucket and Gitea containers.
   - Optional: `defaultBranch` on `Repository`, so a caller needs no branch name.
3. **This service**
   - A `LicensePolicySource` through the vcs-facade client: resolve the branch to a commit once, then list and read every file at that hash.
   - Configuration: `license-service.license-registry.vcs-facade-url` and the registry's `ssh-url` from Spring Cloud Config; no token.
   - Delete `BitbucketLicensePolicySource`, its head re-check loop, the Bitbucket client dependency and the `bitbucket-*` versions.

## Acceptance criteria

- [ ] vcs-facade lists files and reads a file at a commit, for Bitbucket and Gitea, released.
- [ ] `engine/policy-registry` reads the license registry only through vcs-facade; no Bitbucket client on its classpath.
- [ ] Every file of a load is read at the one commit the load resolved; the head re-check loop is gone.
- [ ] The license service needs no VCS token; the `bitbucket.token` setting is removed from configuration and Vault.
- [ ] `docs/architecture.md` and the `license-policy-registry` row name vcs-facade, not Bitbucket.
