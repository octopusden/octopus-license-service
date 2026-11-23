package org.octopusden.octopus.license.trust.model

/**
 * How far one released file's list can be trusted (D5). Codes only: the component's reasons carry
 * the details.
 */
data class FileTrust(
    val entry: String,
    val reasons: List<TrustReasonCode>,
) {
    val level: TrustLevel get() = TrustLevel.lowest(reasons.map { it.level })
}
