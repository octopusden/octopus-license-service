package org.octopusden.octopus.license.inventory.impl.listing

/** A published file's name read as `artifactId-version[-classifier].extension`. */
internal data class PublishedFileName(
    val artifactId: String,
    val classifier: String?,
    val extension: String,
) {
    companion object {
        /**
         * The artifact id comes from the file name, because one module can publish several
         * (`sample-portal-config` in `sample-portal`); without `-{version}` in the name it is the
         * module's, and the extension is everything after the first dot.
         */
        fun split(
            fileName: String,
            moduleArtifactId: String,
            version: String,
        ): PublishedFileName {
            val marker = "-$version"
            val at = fileName.indexOf(marker)
            val artifactId = if (at >= 0) fileName.substring(0, at) else moduleArtifactId
            val rest = if (at >= 0) fileName.substring(at + marker.length) else "." + fileName.substringAfter('.')
            if (rest.startsWith('-')) {
                val classifier = rest.substring(1).substringBefore('.')
                val extension = rest.substring(1).substringAfter('.', "")
                return PublishedFileName(artifactId, classifier.ifEmpty { null }, extension)
            }
            return PublishedFileName(artifactId, null, rest.drop(1))
        }
    }
}
