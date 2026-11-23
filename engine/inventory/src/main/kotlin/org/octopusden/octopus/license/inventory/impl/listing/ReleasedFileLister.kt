package org.octopusden.octopus.license.inventory.impl.listing

import org.octopusden.octopus.license.inventory.impl.listing.candidate.CandidateExtractor
import org.octopusden.octopus.license.inventory.impl.listing.candidate.CandidateSelector
import org.octopusden.octopus.license.inventory.impl.listing.data.Candidate
import org.octopusden.octopus.license.inventory.impl.listing.data.ImageCandidate
import org.octopusden.octopus.license.inventory.impl.listing.data.ListedFile
import org.octopusden.octopus.license.inventory.impl.listing.data.Listing
import org.octopusden.octopus.license.inventory.impl.listing.data.MavenCandidate
import org.octopusden.octopus.license.inventory.impl.listing.data.Selection
import org.octopusden.octopus.license.inventory.impl.listing.data.notFound
import org.octopusden.octopus.license.inventory.impl.port.ArtifactStore
import org.octopusden.octopus.license.inventory.impl.port.BuildRecords
import org.octopusden.octopus.license.inventory.impl.port.data.BuildRecord
import org.octopusden.octopus.license.inventory.impl.shared.BuildLocator
import org.octopusden.octopus.license.inventory.model.BuildLookup
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.target.model.DeclaredArtifact
import org.octopusden.octopus.license.target.model.LicenseTarget

/**
 * Step A (D5): every file a component version releases, located and with its build, before any is
 * opened. Two routes fill one list: the files the convention builds published, then the declared
 * entries none of them produced ([DeclaredLeftoverLocator]).
 */
internal class ReleasedFileLister(
    private val records: BuildRecords,
    private val store: ArtifactStore,
    locator: BuildLocator,
) {
    private val leftovers = DeclaredLeftoverLocator(store, locator)

    fun list(target: LicenseTarget): Listing {
        val builds =
            ConventionBuilds.names(target).mapNotNull { name ->
                target.artifactVersions.firstNotNullOfOrNull { records.find(name, it) }
            }
        val selector = CandidateSelector(target)
        val matched = mutableSetOf<DeclaredArtifact>()
        val fromBuilds =
            builds.flatMap { build ->
                CandidateExtractor.extract(build).map { candidate ->
                    val selection = selector.select(candidate)
                    selection.matched?.let(matched::add)
                    when (candidate) {
                        is MavenCandidate -> maven(candidate, selection, build)
                        is ImageCandidate -> image(candidate, selection, build)
                    }
                }
            }
        val files = fromBuilds + target.distribution.declared
            .filterNot { it in matched }
            .map { leftovers.locate(it, target) }
        val listing = Listing(builds, files, emptyList())
        return if (listing.nothingToRead) listing.copy(findings = listOf(noDistribution(target, builds))) else listing
    }

    private fun maven(
        candidate: MavenCandidate,
        selection: Selection,
        build: BuildRecord,
    ): ListedFile {
        val listed = fromBuild(candidate, FileKind.MAVEN, selection, build).copy(skip = selection.skip)
        if (selection.skip != null) return listed
        val stored = candidate.file.sha1
            ?.takeIf { it.isNotEmpty() }
            ?.let(store::bySha1)
            ?: return listed.notFound("listed in ${build.ref} but not in Artifactory")
        return listed.copy(stored = stored, ownId = PackageId.maven(candidate.group, candidate.artifactId, candidate.version))
    }

    private fun image(
        candidate: ImageCandidate,
        selection: Selection,
        build: BuildRecord,
    ): ListedFile {
        val listed = fromBuild(candidate, FileKind.DOCKER, selection, build)
        return store.byPath("${candidate.image}/${candidate.tag}", MANIFEST)?.let { listed.copy(stored = it) }
            ?: listed.notFound("image in build-info but not in Artifactory")
    }

    private fun fromBuild(
        candidate: Candidate,
        kind: FileKind,
        selection: Selection,
        build: BuildRecord,
    ) = ListedFile(
        entry = candidate.entry,
        kind = kind,
        selectedBy = selection.selectedBy,
        build = build,
        buildFoundBy = BuildLookup.CONVENTION,
    )

    private fun noDistribution(
        target: LicenseTarget,
        builds: List<BuildRecord>,
    ) = Finding(
        FindingCode.NO_DISTRIBUTION,
        target.componentVersion.component,
        if (builds.isEmpty()) {
            "no build ${ConventionBuilds.name(target)}(_docker)/${target.componentVersion.version} " +
                "and no artifact in the registry GAV or docker field"
        } else {
            "the builds publish nothing that matches the registry GAV or maven-artifacts pattern"
        },
    )

    private companion object {
        const val MANIFEST = "manifest.json"
    }
}
