package org.octopusden.octopus.license.policy.impl.parser

import org.octopusden.octopus.license.policy.exception.InvalidLicenseRegistryException
import org.octopusden.octopus.license.policy.model.LicensePolicy
import org.octopusden.octopus.license.policy.model.LicenseText
import org.octopusden.octopus.license.policy.model.OverrideRule
import org.octopusden.releng.versions.NumericVersionFactory
import org.octopusden.releng.versions.VersionNames
import org.octopusden.releng.versions.VersionRangeFactory
import java.io.StringReader
import java.util.Properties

/**
 * Turns the license registry's files, as read at one commit, into a [LicensePolicy]. Pure: the
 * adapter that reads Bitbucket pushes the files in.
 *
 * Strict on structure, lenient on content (D4): a registry that allows nothing is refused, a single
 * bad line becomes one of the policy's problems.
 */
object LicenseRegistryParser {
    private const val GAV_PARTS = 3
    private const val WHITELIST = "licenses-whitelist.txt"
    private const val HIDDEN = "licenses-hidden.txt"
    private const val MERGES = "merges.txt"
    private const val OVERRIDES = "thirdparty-licenses.properties"
    private const val TEXTS = "licenses.properties"
    private const val TEXTS_DIRECTORY = "licenses/"

    // The names the Maven plugin parses override versions with: none, so only plain numbers count (D6).
    private val versionNames = VersionNames("", "", "")
    private val rangeFactory = VersionRangeFactory(versionNames)
    private val range = Regex("""^[\[(].*[])]$""")

    /**
     * @param files file contents by their path in the registry, `licenses/` texts included.
     * @throws InvalidLicenseRegistryException no whitelist, or one that names no license.
     */
    fun parse(
        files: Map<String, String>,
        commit: String,
    ): LicensePolicy {
        val whitelist = listedNames(files[WHITELIST].orEmpty())
        if (whitelist.isEmpty()) {
            throw InvalidLicenseRegistryException("$WHITELIST is missing or names no license at $commit")
        }
        val hidden = listedNames(files[HIDDEN].orEmpty())
        val problems = mutableListOf<String>()
        val canonicalNames = canonicalNames(files[MERGES].orEmpty(), whitelist + hidden, problems)
        return LicensePolicy(
            commit = commit,
            problems = problems,
            whitelist = whitelist,
            hidden = hidden,
            canonicalNames = canonicalNames,
            overrides = overrides(files[OVERRIDES].orEmpty(), problems),
            texts = texts(files, canonicalNames, problems),
            versionFactory = NumericVersionFactory(versionNames),
        )
    }

    /**
     * Lower-cased name to its canonical spelling (D5). A `merges.txt` line wins over a list's own
     * spelling, so a whitelisted synonym such as `CDDL-1.0-GPL-2.0` still reads as `CDDL-1.0`.
     */
    private fun canonicalNames(
        merges: String,
        listed: Set<String>,
        problems: MutableList<String>,
    ): Map<String, String> {
        val merged = mutableMapOf<String, String>()
        lines(merges).forEach { line ->
            val group = names(line)
            group.forEach { name ->
                val earlier = merged.putIfAbsent(name.lowercase(), group.first())
                if (earlier != null && earlier != group.first()) {
                    problems += "$MERGES: '$name' is merged into '$earlier' already; its line for '${group.first()}' is ignored for it"
                }
            }
        }
        return listed.associateBy { it.lowercase() } + merged
    }

    private fun overrides(
        text: String,
        problems: MutableList<String>,
    ): List<OverrideRule> =
        properties(text).mapNotNull { (key, value) ->
            val licenses = names(value)
            val parts = key.split("--")
            when {
                // An empty value is the plugin's "do not override", not a mistake.
                licenses.isEmpty() -> null
                parts.size != GAV_PARTS -> null.also { problems += "$OVERRIDES: '$key' is not group--artifact--version" }
                else ->
                    runCatching { rangeFactory.create(asRange(parts[2])) }
                        .map { OverrideRule(key, parts[0], parts[1], it, licenses.toSet()) }
                        .onFailure { problems += "$OVERRIDES: '$key' has a version range that cannot be read" }
                        .getOrNull()
            }
        }

    /** Lower-cased canonical name to its text. The plugin looks texts up by merged name; so does the policy. */
    private fun texts(
        files: Map<String, String>,
        canonicalNames: Map<String, String>,
        problems: MutableList<String>,
    ): Map<String, LicenseText> =
        properties(files[TEXTS].orEmpty())
            .mapNotNull { (name, value) ->
                val location = value.trim()
                val text =
                    when {
                        location.startsWith("http://") || location.startsWith("https://") -> LicenseText.AtUrl(location)
                        location.startsWith(TEXTS_DIRECTORY) && location in files ->
                            LicenseText.InRegistry(location, files.getValue(location))
                        else -> null
                    }
                if (text == null) {
                    problems += "$TEXTS: the text of '$name' is neither a $TEXTS_DIRECTORY file in the registry nor a URL: '$location'"
                }
                text?.let { (canonicalNames[name.trim().lowercase()] ?: name.trim()).lowercase() to it }
            }.toMap()

    // The plugin's rule: a version that is not already a range means exactly that version.
    private fun asRange(version: String) = if (range.matches(version)) version else "[$version]"

    private fun properties(text: String): List<Pair<String, String>> =
        Properties().apply { load(StringReader(text)) }.map { (key, value) -> key.toString() to value.toString() }

    private fun listedNames(text: String) = lines(text).flatMap { names(it) }.toSet()

    private fun lines(text: String) = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    private fun names(text: String) = text.split('|').map { it.trim() }.filter { it.isNotEmpty() }
}
