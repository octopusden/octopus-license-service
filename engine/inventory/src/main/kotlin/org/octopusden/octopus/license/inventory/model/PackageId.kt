package org.octopusden.octopus.license.inventory.model

/**
 * One package, as a source named it. [toString] renders it exactly as the prototype wrote it, and
 * two ids are the same package exactly when they render the same (D3).
 */
sealed interface PackageId {
    /** `maven`, `npm`, `docker`, `rpm`…: for any id, what the prototype's `purl_type` reads from its rendering. */
    val type: String

    /** `pkg:{type}/{name}@{version}`; [name] keeps everything between, `group/artifact` for Maven. */
    data class Purl(
        override val type: String,
        val name: String,
        val version: String,
    ) : PackageId {
        fun mavenCoordinates(): MavenCoordinates? =
            if (type == MAVEN) {
                MavenCoordinates(name.substringBefore('/'), name.substringAfter('/'), version)
            } else {
                null
            }

        override fun toString() = "pkg:$type/$name@$version"
    }

    /** A file Xray found but could not name: `generic://sha256:{sha256}/{fileName}`. */
    data class ChecksumOnly(
        val sha256: String,
        val fileName: String,
    ) : PackageId {
        override val type get() = GENERIC

        override fun toString() = "generic://sha256:$sha256/$fileName"
    }

    /** Any other id, kept verbatim so parsing never changes what a source said. */
    data class Unrecognized(
        val raw: String,
    ) : PackageId {
        override val type get() = if (raw.startsWith(
                PURL_PREFIX,
            )
        ) {
            raw.substringAfter(':').substringBefore('/')
        } else {
            raw.substringBefore("://")
        }

        override fun toString() = raw
    }

    companion object {
        const val MAVEN = "maven"
        private const val GENERIC = "generic"
        private const val PURL_PREFIX = "pkg:"
        private val CHECKSUM_ONLY = Regex("generic://sha256:([0-9a-f]{64})/(.*)")
        private val PURL = Regex("pkg:([^/]+)/(.+)@([^@]*)")

        fun maven(
            groupId: String,
            artifactId: String,
            version: String,
        ) = Purl(MAVEN, "$groupId/$artifactId", version)

        fun parse(raw: String): PackageId {
            CHECKSUM_ONLY.matchEntire(raw)?.let { return ChecksumOnly(it.groupValues[1], it.groupValues[2]) }
            val purl = PURL.matchEntire(raw)?.let { Purl(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            return purl?.takeIf { it.toString() == raw } ?: Unrecognized(raw)
        }
    }
}
