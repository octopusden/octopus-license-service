package org.octopusden.octopus.license.inventory.impl

import org.octopusden.octopus.license.inventory.impl.port.data.BuildDependency
import org.octopusden.octopus.license.inventory.impl.port.data.BuildModule
import org.octopusden.octopus.license.inventory.impl.port.data.BuildRecord
import org.octopusden.octopus.license.inventory.impl.port.data.BuiltFile
import org.octopusden.octopus.license.inventory.impl.port.data.StoredFile
import org.octopusden.octopus.license.inventory.model.BuildRef
import org.octopusden.octopus.license.inventory.model.OwnershipRules
import org.octopusden.octopus.license.target.model.ArtifactPattern
import org.octopusden.octopus.license.target.model.ComponentVersion
import org.octopusden.octopus.license.target.model.DeclaredArtifact
import org.octopusden.octopus.license.target.model.DistributionSpec
import org.octopusden.octopus.license.target.model.LicenseTarget

internal const val COMPONENT = "sample-portal"
internal const val VERSION = "2.4.0"
internal const val BUILD_VERSION = "2.4.0-118"
internal const val GROUP = "org.example.portal"
internal const val INTERNAL_GROUP = "org.example"

internal val OWNERSHIP = OwnershipRules(internalGroups = listOf(INTERNAL_GROUP), ownRepositoryPrefixes = listOf("rnd-"))

internal fun target(
    declared: List<DeclaredArtifact> = emptyList(),
    patterns: List<ArtifactPattern> = emptyList(),
    explicit: Boolean = true,
    artifactVersions: List<String> = listOf(VERSION, BUILD_VERSION),
    labels: Set<String> = emptySet(),
) = LicenseTarget(
    componentVersion = ComponentVersion(COMPONENT, VERSION),
    artifactVersions = artifactVersions,
    buildSystem = "GRADLE",
    labels = labels,
    explicit = explicit,
    distribution = DistributionSpec(declared, patterns),
)

internal fun build(
    name: String = "${COMPONENT}_ee",
    number: String = BUILD_VERSION,
    vararg modules: BuildModule,
) = BuildRecord(BuildRef(name, number), agent = "jfrog-cli-go", modules = modules.toList())

internal fun module(
    id: String = "$GROUP:$COMPONENT:$BUILD_VERSION",
    type: String? = "gradle",
    files: List<BuiltFile> = emptyList(),
    dependencies: List<BuildDependency> = emptyList(),
) = BuildModule(id, type, files, dependencies)

internal fun builtFile(
    name: String,
    sha1: String? = "sha1-$name",
    path: String? = null,
) = BuiltFile(name, sha1, path)

internal fun dependency(
    id: String,
    vararg scopes: String,
    type: String? = "jar",
    sha1: String? = "sha1-$id",
) = BuildDependency(id, type, sha1, scopes.toList())

internal fun stored(
    name: String,
    path: String = "${GROUP.replace('.', '/')}/$COMPONENT/$BUILD_VERSION",
    repo: String = "rnd-maven-release-local",
    sha1: String? = "sha1-$name",
    sha256: String? = "sha256-$name",
    properties: Map<String, String> = emptyMap(),
) = StoredFile(repo, path, name, sha1, sha256, properties)
