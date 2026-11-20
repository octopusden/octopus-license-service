package org.octopusden.octopus.license.files.impl

import org.octopusden.octopus.license.evaluation.model.AssessedLibrary

/**
 * `THIRD-PARTY.txt` in the layout of the plugin's `third-party-file.ftl`, whose blank line before
 * its first directive is the file's empty first line (D4).
 */
internal object ThirdPartyText {
    const val FILE_NAME = "THIRD-PARTY.txt"

    fun render(libraries: List<AssessedLibrary>): String =
        if (libraries.isEmpty()) {
            "\nThe project has no dependencies.\n"
        } else {
            "\nLists of ${libraries.size} third-party dependencies.\n" + libraries.joinToString("") { NoticeLine.of(it) + "\n" }
        }
}
