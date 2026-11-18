package org.octopusden.octopus.license.trust.impl.component

import org.octopusden.octopus.license.trust.impl.shared.GradingSubject
import org.octopusden.octopus.license.trust.model.TrustReason

/** One question asked about the whole component. Gives reasons, never a level: the codes carry it (D4). */
internal fun interface ComponentRule {
    fun reasons(subject: GradingSubject): List<TrustReason>
}
