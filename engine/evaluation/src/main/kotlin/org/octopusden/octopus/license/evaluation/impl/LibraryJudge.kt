package org.octopusden.octopus.license.evaluation.impl

import org.octopusden.octopus.license.evaluation.model.AssessedLibrary
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.policy.model.LicensePolicy
import org.octopusden.octopus.license.policy.model.LicenseVerdict
import org.octopusden.octopus.license.policy.model.MavenCoordinates

/**
 * The verdict on one counted library, first match wins: ours, then unnamed, then the license policy
 * (D4). How a license is judged is the policy's alone; this class never reads a license name (D6).
 */
internal class LibraryJudge(
    private val policy: LicensePolicy,
) {
    fun judge(library: FoundLibrary): AssessedLibrary {
        val judgement = policy.judge(library.licenses, coordinates(library.id))
        val verdict =
            when {
                library.ours -> LibraryVerdict.INTERNAL
                // The prototype tests the rendering, so an unrecognized generic:// id is unnamed too.
                library.id.toString().startsWith(UNNAMED) -> LibraryVerdict.UNIDENTIFIED
                else ->
                    when (judgement.verdict) {
                        LicenseVerdict.ALLOWED -> LibraryVerdict.ALLOWED
                        LicenseVerdict.NOT_ALLOWED -> LibraryVerdict.NOT_ALLOWED
                        LicenseVerdict.UNKNOWN -> LibraryVerdict.UNKNOWN
                    }
            }
        return AssessedLibrary(library, verdict, judgement.licenses, judgement.overridden)
    }

    private fun coordinates(id: PackageId) =
        (id as? PackageId.Purl)?.mavenCoordinates()?.let { MavenCoordinates(it.groupId, it.artifactId, it.version) }

    private companion object {
        const val UNNAMED = "generic://"
    }
}
