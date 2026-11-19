package org.octopusden.octopus.license.files.model

/**
 * The license files of a component version: one [FileFolder] per released file that was read, in
 * listing order (D2), and the license texts none of them could hold.
 */
data class LicenseFiles(
    val folders: List<FileFolder>,
    val missingTexts: List<MissingText>,
)

/**
 * The plugin's outputs for one released file: `THIRD-PARTY.txt`, `licenses.xml` and the license
 * texts, side by side as the plugin writes them.
 *
 * @param releasedFile the released file's entry, as the inventory lists it.
 */
data class FileFolder(
    val name: String,
    val releasedFile: String,
    val files: List<NamedFile>,
)

/** Not a data class: an array's equality is its identity. */
class NamedFile(
    val name: String,
    val content: ByteArray,
) {
    override fun toString() = "NamedFile($name, ${content.size} bytes)"
}

/**
 * A license used by a listed library whose text no folder holds; once per report, whatever the
 * number of folders that use it.
 *
 * @param url the text's URL, for [MissingTextReason.DOWNLOAD_FAILED] only.
 */
data class MissingText(
    val license: String,
    val reason: MissingTextReason,
    val url: String? = null,
)

enum class MissingTextReason {
    /** `licenses.properties` does not name the license. */
    NO_REGISTRY_ENTRY,

    /** The registry gives the text only as a URL, and it could not be downloaded. */
    DOWNLOAD_FAILED,
}
