package org.octopusden.octopus.license.trust.model

/**
 * How far a component version's library list can be trusted.
 *
 * @param reasons in rule order, no code and detail twice; the level is theirs alone, never the files' (D5).
 * @param files every selected released file, in listing order; files not selected are not graded.
 */
data class TrustGrade(
    val reasons: List<TrustReason>,
    val files: List<FileTrust>,
) {
    val level: TrustLevel get() = TrustLevel.lowest(reasons.map { it.code.level })
}
