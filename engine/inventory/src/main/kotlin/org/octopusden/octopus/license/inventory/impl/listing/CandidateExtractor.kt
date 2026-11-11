package org.octopusden.octopus.license.inventory.impl.listing

import org.octopusden.octopus.license.inventory.impl.port.data.BuildRecord

/** Every file a convention build published, as a candidate entry, in build-info order (D5). */
internal object CandidateExtractor {
    private const val DOCKER = "docker"
    private const val MANIFEST = "manifest.json"
    private const val LIST_MANIFEST = "list.manifest.json"

    fun extract(build: BuildRecord): List<Candidate> =
        build.modules.flatMap { module ->
            module.files.mapNotNull { file ->
                if (module.type == DOCKER || file.path.orEmpty().endsWith(MANIFEST)) {
                    // Only the manifest stands for an image; layers and the list manifest are no released file.
                    image(file.path)
                        .takeIf { file.name.endsWith(MANIFEST) && file.name != LIST_MANIFEST }
                        ?.let { (image, tag) -> ImageCandidate(image, tag, build, file) }
                } else {
                    val (group, moduleArtifactId, version) = (module.id.split(':') + listOf("", "", "")).take(3)
                    val name = PublishedFileName.split(file.name, moduleArtifactId, version)
                    MavenCandidate(group, name.artifactId, version, name.classifier, name.extension, build, file)
                }
            }
        }

    /** `sample/portal/2.4.0/manifest.json` is the image `sample/portal`, tag `2.4.0`. */
    private fun image(path: String?): Pair<String, String>? {
        val parts = path?.split('/') ?: return null
        if (parts.size < 2) return null
        return parts.dropLast(2).joinToString("/") to parts[parts.size - 2]
    }
}
