package org.octopusden.octopus.license.runner

import java.time.Duration
import java.time.format.DateTimeParseException

/** Reads a duration as people type it in build settings: `45m`, `30s`, `2h`, or ISO-8601 (`PT45M`). */
object Durations {
    private val shortForm = Regex("""(\d+)([smh])""")

    fun parse(text: String): Duration? {
        val trimmed = text.trim()
        shortForm.matchEntire(trimmed)?.let { match ->
            val amount = match.groupValues[1].toLong()
            return when (match.groupValues[2]) {
                "s" -> Duration.ofSeconds(amount)
                "m" -> Duration.ofMinutes(amount)
                else -> Duration.ofHours(amount)
            }
        }
        if (!trimmed.startsWith("P", ignoreCase = true)) return null
        return try {
            Duration.parse(trimmed).takeUnless { it.isNegative }
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
