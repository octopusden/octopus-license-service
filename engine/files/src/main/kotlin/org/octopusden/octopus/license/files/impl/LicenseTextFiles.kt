package org.octopusden.octopus.license.files.impl

import org.octopusden.octopus.license.files.model.MissingText
import org.octopusden.octopus.license.files.model.MissingTextReason
import org.octopusden.octopus.license.files.model.NamedFile
import org.octopusden.octopus.license.policy.model.LicensePolicy
import org.octopusden.octopus.license.policy.model.LicenseText

/** The text file of each license a folder uses, and the ones that cannot be written (D6). */
internal object LicenseTextFiles {
    private const val DEFAULT_EXTENSION = ".txt"

    // The plugin keeps a file's extension only when it is at least two characters long.
    private const val SHORTEST_EXTENSION = 2

    fun texts(
        licenses: Set<String>,
        policy: LicensePolicy,
        downloaded: Map<String, ByteArray>,
    ): FolderTexts {
        val files = linkedMapOf<String, ByteArray>()
        val missing = mutableListOf<MissingText>()
        licenses.sorted().forEach { license ->
            when (val text = policy.text(license)) {
                is LicenseText.InRegistry -> files.addText(fileName(license, text.path), text.content.toByteArray())
                is LicenseText.AtUrl ->
                    downloaded[text.url]?.let { files.addText(fileName(license, text.url), it) }
                        ?: run { missing += MissingText(license, MissingTextReason.DOWNLOAD_FAILED, text.url) }
                null -> missing += MissingText(license, MissingTextReason.NO_REGISTRY_ENTRY)
            }
        }
        return FolderTexts(files.map { (name, content) -> NamedFile(name, content) }.sortedBy { it.name }, missing)
    }

    // Two licenses can get one file name (`Foo/Bar` and `Foo_Bar`): the same text is written once,
    // a different one under the first free `-2`, `-3`, so the zip never holds a name twice.
    private fun MutableMap<String, ByteArray>.addText(
        name: String,
        content: ByteArray,
    ) {
        val free =
            generateSequence(1) { it + 1 }
                .map { if (it == 1) name else numbered(name, it) }
                .first { candidate -> this[candidate]?.contentEquals(content) != false }
        this[free] = content
    }

    private fun numbered(
        name: String,
        n: Int,
    ): String {
        val dot = name.lastIndexOf('.')
        return if (dot == -1) "$name-$n" else "${name.substring(0, dot)}-$n${name.substring(dot)}"
    }

    /** The plugin's `getLicenseFileName`, for a license not organized by dependency. */
    fun fileName(
        license: String,
        location: String,
    ): String {
        val name = "${license.replace('/', '_')} - ${lastSegment(location)}"
        val extension = name.lastIndexOf('.')
        val withExtension =
            if (extension == -1 || extension > name.length - SHORTEST_EXTENSION - 1) name + DEFAULT_EXTENSION else name
        return withExtension.lowercase()
    }

    // As the plugin reads a URL: Bitbucket's raw suffix and encoded slashes removed, then the path only.
    private fun lastSegment(location: String): String {
        val path =
            if (location.startsWith("http")) {
                location
                    .replace("/raw?ref=master", "")
                    .replace("%2F", "/")
                    .substringAfter("://")
                    .substringBefore('?')
                    .substringBefore('#')
                    .substringAfter('/', "")
            } else {
                location
            }
        return path.trimEnd('/').substringAfterLast('/')
    }
}

internal class FolderTexts(
    val files: List<NamedFile>,
    val missing: List<MissingText>,
)
