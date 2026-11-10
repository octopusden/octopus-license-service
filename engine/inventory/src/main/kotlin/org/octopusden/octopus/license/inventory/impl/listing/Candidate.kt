package org.octopusden.octopus.license.inventory.impl.listing

import org.octopusden.octopus.license.inventory.impl.port.BuildRecord
import org.octopusden.octopus.license.inventory.impl.port.BuiltFile

/** A file a convention build published, before anyone decides whether it is released. */
internal sealed interface Candidate {
    val entry: String
    val build: BuildRecord
    val file: BuiltFile
}

/** [version] is the publishing module's, which locates nested copies later. */
internal data class MavenCandidate(
    val group: String,
    val artifactId: String,
    val version: String,
    val classifier: String?,
    val extension: String,
    override val build: BuildRecord,
    override val file: BuiltFile,
) : Candidate {
    override val entry get() = "$group:$artifactId:$extension" + classifier.orEmpty().let { if (it.isEmpty()) "" else ":$it" }
}

internal data class ImageCandidate(
    val image: String,
    val tag: String,
    override val build: BuildRecord,
    override val file: BuiltFile,
) : Candidate {
    override val entry get() = "$image:$tag"
}
