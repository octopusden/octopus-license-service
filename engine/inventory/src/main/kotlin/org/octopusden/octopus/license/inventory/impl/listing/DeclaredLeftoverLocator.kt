package org.octopusden.octopus.license.inventory.impl.listing

import org.octopusden.octopus.license.inventory.impl.listing.candidate.entry
import org.octopusden.octopus.license.inventory.impl.listing.data.ListedFile
import org.octopusden.octopus.license.inventory.impl.listing.data.notFound
import org.octopusden.octopus.license.inventory.impl.port.ArtifactStore
import org.octopusden.octopus.license.inventory.impl.shared.BuildLocator
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.inventory.model.SelectionReason
import org.octopusden.octopus.license.target.model.DeclaredArtifact
import org.octopusden.octopus.license.target.model.DeclaredArtifact.DockerImage
import org.octopusden.octopus.license.target.model.DeclaredArtifact.MavenArtifact
import org.octopusden.octopus.license.target.model.LicenseTarget

/**
 * The second route of step A (D5): a declared entry no convention build produced, typically a file
 * another build made (C/C++ by a shared build). Looked up by its path at each artifact version,
 * then its build is found as for any stored file.
 */
internal class DeclaredLeftoverLocator(
    private val store: ArtifactStore,
    private val locator: BuildLocator,
) {
    fun locate(
        declared: DeclaredArtifact,
        target: LicenseTarget,
    ): ListedFile =
        when (declared) {
            is DeclaredArtifact.Unsupported -> unsupported(declared)
            is MavenArtifact -> maven(declared, target)
            is DockerImage -> image(declared, target)
        }

    private fun maven(
        declared: MavenArtifact,
        target: LicenseTarget,
    ): ListedFile {
        val listed = ListedFile(declared.entry(), FileKind.MAVEN, selectedBy = setOf(SelectionReason.DECLARED))
        return target.artifactVersions
            .firstNotNullOfOrNull { version ->
                store
                    .byCoordinates(declared.groupId, declared.artifactId, version, declared.extension, declared.classifier)
                    ?.let { version to it }
            }?.let { (version, stored) ->
                withBuild(listed.copy(stored = stored, ownId = PackageId.maven(declared.groupId, declared.artifactId, version)), target)
            }
            ?: listed.notFound(triedVersions(target))
    }

    private fun image(
        declared: DockerImage,
        target: LicenseTarget,
    ): ListedFile {
        val listed =
            ListedFile(
                declared.image + declared.tag?.let { ":$it" }.orEmpty(),
                FileKind.DOCKER,
                selectedBy = setOf(SelectionReason.DECLARED),
            )
        return (declared.tag?.let(::listOf) ?: target.artifactVersions)
            .firstNotNullOfOrNull { tag -> store.byPath("${declared.image}/$tag", MANIFEST)?.let { tag to it } }
            ?.let { (tag, stored) -> withBuild(listed.copy(entry = "${declared.image}:$tag", stored = stored), target) }
            ?: listed.notFound(triedVersions(target))
    }

    private fun withBuild(
        listed: ListedFile,
        target: LicenseTarget,
    ): ListedFile {
        val (build, lookup) = locator.locate(listed.stored!!) ?: return listed
        val differs =
            Finding(FindingCode.BUILD_NAME_DIFFERS, listed.entry, "${build.ref.name} (convention ${ConventionBuilds.name(target)})")
                .takeUnless { build.ref.name in ConventionBuilds.names(target) }
        return listed.copy(build = build, buildFoundBy = lookup, findings = listOfNotNull(differs))
    }

    // The prototype tells a file share from a malformed GAV entry by these prefixes, case included.
    private fun unsupported(declared: DeclaredArtifact.Unsupported): ListedFile {
        val fileShare = URI_PREFIXES.any { declared.entry.startsWith(it) }
        return ListedFile(
            entry = declared.entry,
            kind = if (fileShare) FileKind.FILE_SHARE else FileKind.MAVEN,
            selectedBy = setOf(SelectionReason.DECLARED),
            skip = FileStatus.UNSUPPORTED,
            findings =
                listOf(
                    Finding(
                        FindingCode.UNSUPPORTED_DISTRIBUTION_ENTRY,
                        declared.entry,
                        if (fileShare) "" else "unsupported GAV format '${declared.entry}'",
                    ),
                ),
        )
    }

    private fun triedVersions(target: LicenseTarget) = "tried versions " + target.artifactVersions.joinToString(", ")

    private companion object {
        const val MANIFEST = "manifest.json"
        val URI_PREFIXES = listOf("file:", "http:", "https:")
    }
}
