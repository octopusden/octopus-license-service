package org.octopusden.octopus.license.policy.model

import org.octopusden.releng.versions.NumericVersionFactory

/**
 * What the license registry allows at one [commit], and the verdict on one named library. Pure: it
 * is read once (see `LicensePolicySource`) and asked many times.
 *
 * Every name lookup ignores case, a deliberate difference from the license plugins (D5).
 *
 * @param problems registry entries skipped because they could not be read; the rest of the policy
 * holds without them (D4).
 */
class LicensePolicy internal constructor(
    val commit: String,
    val problems: List<String>,
    private val whitelist: Set<String>,
    hidden: Set<String>,
    private val canonicalNames: Map<String, String>,
    private val overrides: List<OverrideRule>,
    private val texts: Map<String, LicenseText>,
    private val versionFactory: NumericVersionFactory,
) {
    private val allowed = (whitelist + hidden).map { canonical(it).lowercase() }.toSet()

    /** The whitelisted licenses, by canonical name, that have neither a registry text nor a URL. */
    val licensesWithoutText: List<String> =
        whitelist
            .map { canonical(it) }
            .distinct()
            .filter { text(it) == null }
            .sorted()

    /**
     * @param licenses as the library was found with them, in any spelling.
     * @param coordinates only a Maven library can be overridden.
     */
    fun judge(
        licenses: Set<String>,
        coordinates: MavenCoordinates? = null,
    ): Judgement {
        val override = coordinates?.let { overrideFor(it) }
        val names =
            (override?.licenses ?: licenses)
                .map { canonical(it.trim()) }
                .filterNot { it.lowercase() in UNKNOWN_MARKERS }
                .distinct()
                .sorted()
        val verdict =
            when {
                names.isEmpty() -> LicenseVerdict.UNKNOWN
                names.any { it.lowercase() in allowed } -> LicenseVerdict.ALLOWED
                else -> LicenseVerdict.NOT_ALLOWED
            }
        return Judgement(verdict, names, overridden = override != null)
    }

    fun text(license: String): LicenseText? = texts[canonical(license.trim()).lowercase()]

    /** The greatest matching key wins: the plugin applies every match in sorted order, the last one last (D5). */
    private fun overrideFor(coordinates: MavenCoordinates): OverrideRule? {
        val version = runCatching { versionFactory.create(coordinates.version) }.getOrNull() ?: return null
        return overrides.filter { it.matches(coordinates, version) }.maxByOrNull { it.key }
    }

    private fun canonical(name: String) = canonicalNames[name.lowercase()] ?: name

    private companion object {
        val UNKNOWN_MARKERS = setOf("", "unknown", "unknown license")
    }
}
