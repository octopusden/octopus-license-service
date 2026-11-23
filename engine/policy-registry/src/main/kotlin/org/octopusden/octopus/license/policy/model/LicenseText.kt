package org.octopusden.octopus.license.policy.model

/** Where the text of a license is, as `licenses.properties` says. Fetching a URL is the license files' job. */
sealed interface LicenseText {
    data class InRegistry(
        val path: String,
        val content: String,
    ) : LicenseText

    data class AtUrl(
        val url: String,
    ) : LicenseText
}
