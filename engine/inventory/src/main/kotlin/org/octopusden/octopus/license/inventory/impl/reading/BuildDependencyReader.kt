package org.octopusden.octopus.license.inventory.impl.reading

import org.octopusden.octopus.license.inventory.impl.Ownership
import org.octopusden.octopus.license.inventory.impl.port.BuildDependency
import org.octopusden.octopus.license.inventory.impl.port.BuildRecord
import org.octopusden.octopus.license.inventory.impl.port.StoredFile
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode
import org.octopusden.octopus.license.inventory.model.PackageId

/** One of our own archives a file depends on, to follow into its own build rather than list (D7). */
internal data class NestedArchive(
    val dependencyId: String,
    val group: String,
    val artifactId: String,
    val version: String,
    val type: String,
    val sha1: String?,
)

/** What a build recorded for one file: [packages] carry no license, the build names none. */
internal data class DependencyReading(
    val packages: List<PackageId>,
    val nested: List<NestedArchive>,
    val findings: List<Finding>,
)

/**
 * Reads what a build recorded about one of its files (D6, D7): the production dependencies of its
 * npm modules, which bundled JavaScript hides from Xray, then the shipped dependencies of the module
 * that published the file. Pure: the caller fetched the build and Xray's scan of the file.
 */
internal class BuildDependencyReader(
    private val ownership: Ownership,
) {
    /** @param scanned what Xray found inside [file]: the proof that one of our archives under a custom scope ships. */
    fun read(
        build: BuildRecord,
        file: StoredFile,
        scanned: Set<PackageId>,
    ): DependencyReading {
        val packages = npmPackages(build).toMutableList()
        val producing =
            build.modules.firstOrNull { module -> module.files.any { it.sha1 == file.sha1 || it.name == file.name } }
                ?: return DependencyReading(
                    packages,
                    emptyList(),
                    listOf(Finding(FindingCode.NO_PRODUCING_MODULE, file.name, build.ref.toString())),
                )
        val nested = mutableListOf<NestedArchive>()
        val findings = mutableListOf<Finding>()
        producing.dependencies
            .filter { dependency -> dependency.scopes.any(ShippingScopes::ships) || shipsByProof(dependency, scanned) }
            .filterNot { it.id in GRADLE_SUPPLIED }
            .forEach { dependency ->
                val coordinates = coordinates(dependency.id)
                when {
                    coordinates == null -> findings += Finding(FindingCode.LOCAL_FILE_DEPENDENCY, dependency.id, LOCAL_FILE_DETAIL)
                    isOwnArchive(dependency) ->
                        nested += coordinates.let { (g, a, v) -> NestedArchive(dependency.id, g, a, v, dependency.type!!, dependency.sha1) }
                    else -> packages += coordinates.let { (g, a, v) -> PackageId.Purl(PackageId.MAVEN, "$g/$a", v) }
                }
            }
        return DependencyReading(packages, nested, findings)
    }

    private fun npmPackages(build: BuildRecord) =
        build.modules
            .filter { it.type == NPM }
            .flatMap { it.dependencies }
            .filter { dependency -> dependency.scopes.any { it in NPM_RUNTIME_SCOPES } }
            .mapNotNull { dependency ->
                val name = dependency.id.substringBeforeLast(':', "")
                val version = dependency.id.substringAfterLast(':', "")
                if (name.isEmpty() || version.isEmpty()) null else PackageId.parse("pkg:npm/$name@$version")
            }

    // A custom configuration (Gradle `modpackages`, Maven `provided`) says nothing about shipping; Xray finding the archive does.
    private fun shipsByProof(
        dependency: BuildDependency,
        scanned: Set<PackageId>,
    ): Boolean {
        if (!isOwnArchive(dependency) || dependency.scopes.any(ShippingScopes::isTestOrDebug)) return false
        val (g, a, v) = coordinates(dependency.id) ?: return false
        return PackageId.Purl(PackageId.MAVEN, "$g/$a", v) in scanned
    }

    private fun isOwnArchive(dependency: BuildDependency) =
        dependency.type in ARCHIVE_TYPES && ownership.isInternalGroup(dependency.id) && coordinates(dependency.id) != null

    /** Group, artifact and version, or null for an id that names a local file or a checksum. */
    private fun coordinates(id: String): Triple<String, String, String>? {
        if (id.count { it == ':' } < 2 || id.startsWith(SHA256)) return null
        val (g, a, v) = id.split(':')
        return if (g.isEmpty() || a.isEmpty() || v.isEmpty()) null else Triple(g, a, v)
    }

    private companion object {
        const val NPM = "npm"
        const val SHA256 = "sha256"
        const val LOCAL_FILE_DETAIL = "shipped file without Maven coordinates; check its license by hand"
        val NPM_RUNTIME_SCOPES = setOf("prod")
        val ARCHIVE_TYPES = setOf("zip", "tar", "tgz", "gz", "war", "ear")
        val GRADLE_SUPPLIED = setOf("Gradle API", "Local Groovy", "Gradle TestKit")
    }
}
