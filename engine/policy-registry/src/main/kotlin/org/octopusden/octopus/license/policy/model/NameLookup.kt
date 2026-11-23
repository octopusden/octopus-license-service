package org.octopusden.octopus.license.policy.model

/**
 * Finds a license name as written, as the license plugins do, and only when that finds nothing,
 * ignoring case (D5). A name the registry lists keeps the plugins' answer, even when another line
 * lists it in another casing; ignoring case only reaches names the registry does not spell.
 *
 * @param entries in registry order: of two names equal as written, or equal ignoring case, the first wins.
 */
internal class NameLookup<T>(
    entries: List<Pair<String, T>>,
) {
    private val exact = entries.reversed().toMap()
    private val ignoringCase = entries.reversed().associate { (name, value) -> name.lowercase() to value }

    operator fun get(name: String): T? = exact[name] ?: ignoringCase[name.lowercase()]
}
