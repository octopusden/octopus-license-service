package org.octopusden.octopus.license.trust.model

/** One reason a component's list may be incomplete; its level and texts are the [code]'s (D2). */
data class TrustReason(
    val code: TrustReasonCode,
    val detail: String,
)
