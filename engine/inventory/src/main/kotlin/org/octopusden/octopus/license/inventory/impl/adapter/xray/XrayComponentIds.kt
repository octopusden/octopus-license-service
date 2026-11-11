package org.octopusden.octopus.license.inventory.impl.adapter.xray

import org.octopusden.octopus.license.inventory.model.PackageId

/**
 * Xray's component ids and this module's [PackageId], both ways, so a package is asked about by
 * the id it was found under. Renders exactly as the prototype's `normalise()` (D3).
 */
internal object XrayComponentIds {
    private const val SCHEME_SEPARATOR = "://"
    private const val GAV = "gav"
    private const val NPM = "npm"
    private val SCHEMES_WITH_NAME_AND_VERSION = setOf("go", "npm", "pub", "cocoapods", "pypi", "nuget", "rpm", "deb", "alpine", "docker")

    fun toPackageId(xrayId: String): PackageId {
        val id = xrayId.replaceFirst(Regex("^gav://\\.\\."), "gav://")
        val scheme = id.substringBefore(SCHEME_SEPARATOR)
        val rest = id.substringAfter(SCHEME_SEPARATOR, "")
        val rendered =
            when {
                SCHEME_SEPARATOR !in id -> id
                scheme == GAV -> (rest.split(':') + listOf("", "")).let { (g, a, v) -> "pkg:maven/$g/$a@$v" }
                scheme in SCHEMES_WITH_NAME_AND_VERSION ->
                    "pkg:${if (scheme == "go") "golang" else scheme}/${rest.substringBeforeLast(':', "")}@${rest.substringAfterLast(':')}"
                else -> id
            }
        return PackageId.parse(rendered)
    }

    /** Null for a package type Xray's component summary is not asked about. */
    fun toXrayId(purl: PackageId.Purl): String? =
        when (purl.type) {
            PackageId.MAVEN -> purl.mavenCoordinates()?.let { "$GAV://${it.groupId}:${it.artifactId}:${it.version}" }
            NPM -> "$NPM://${purl.name}:${purl.version}"
            else -> null
        }

    /** Xray answers a component summary with the id stripped of its scheme. */
    fun bare(id: String) = id.substringAfter(SCHEME_SEPARATOR)
}
