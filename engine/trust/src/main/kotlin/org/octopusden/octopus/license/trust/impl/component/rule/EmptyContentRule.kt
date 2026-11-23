package org.octopusden.octopus.license.trust.impl.component.rule

import org.octopusden.octopus.license.trust.impl.component.ComponentRule
import org.octopusden.octopus.license.trust.impl.shared.GradingSubject
import org.octopusden.octopus.license.trust.model.TrustReason
import org.octopusden.octopus.license.trust.model.TrustReasonCode

/**
 * Files were read, yet no third-party library was in them. For C/C++ that usually means compiled in,
 * which the labels alone tell (D6).
 */
internal object EmptyContentRule : ComponentRule {
    private val NATIVE_LABELS = setOf("c", "cpp")

    override fun reasons(subject: GradingSubject): List<TrustReason> {
        if (subject.read.isEmpty() || subject.thirdParty.isNotEmpty()) return emptyList()
        val note = if (subject.labels.any { it in NATIVE_LABELS }) " (C/C++ build)" else ""
        return listOf(TrustReason(TrustReasonCode.NO_THIRD_PARTY_FOUND, "${subject.read.size} file(s) checked, 0 outside libraries$note"))
    }
}
