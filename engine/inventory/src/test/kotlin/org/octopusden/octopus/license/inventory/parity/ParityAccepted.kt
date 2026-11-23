package org.octopusden.octopus.license.inventory.parity

/**
 * Differences a person has checked and found explained by Decision 11, so a parity run reports them
 * as accepted instead of failing on them.
 *
 * One per line, `<component>: <difference as the report prints it>  # <the Decision 11 row>`; blank
 * lines and lines starting with `#` are skipped. A line without a reason is rejected: an accepted
 * difference that names no row is a regression nobody explained.
 */
class ParityAccepted private constructor(
    private val reasons: Map<Pair<String, String>, String>,
) {
    fun reasonFor(
        component: String,
        difference: String,
    ): String? = reasons[component to difference]

    companion object {
        val NONE = ParityAccepted(emptyMap())

        fun parse(lines: List<String>): ParityAccepted =
            ParityAccepted(
                lines
                    .withIndex()
                    .filter { (_, line) -> line.isNotBlank() && !line.trimStart().startsWith("#") }
                    .associate { (index, line) -> entry(index + 1, line) },
            )

        // The reason is split off at the last " # ", so a difference may itself contain one.
        private fun entry(
            number: Int,
            line: String,
        ): Pair<Pair<String, String>, String> {
            val reason = line.substringAfterLast(" # ", "").trim()
            val accepted = line.substringBeforeLast(" # ", "")
            require(reason.isNotEmpty() && ": " in accepted) {
                "line $number: expected '<component>: <difference>  # <Decision 11 row>', got '$line'"
            }
            return (accepted.substringBefore(": ").trim() to accepted.substringAfter(": ").trim()) to reason
        }
    }
}
