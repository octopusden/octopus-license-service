package org.octopusden.octopus.license.inventory.model

/**
 * One file the version releases, or a declared entry that cannot be read.
 *
 * @param entry `group:artifact:extension[:classifier]` for a Maven file, `image:tag` for an image,
 * the registry's own text for an unsupported entry.
 * @param selectedBy empty when the file is [FileStatus.NOT_SELECTED].
 * @param path `repo/path/name` in Artifactory, once located.
 * @param evidence for a [FileStatus.READ] file only: which sources could see inside it.
 */
data class ReleasedFile(
    val entry: String,
    val kind: FileKind,
    val declared: Boolean,
    val selectedBy: Set<SelectionReason>,
    val path: String? = null,
    val build: BuildRef? = null,
    val buildFoundBy: BuildLookup? = null,
    val buildAgent: String? = null,
    val status: FileStatus,
    val evidence: Set<Evidence> = emptySet(),
)
