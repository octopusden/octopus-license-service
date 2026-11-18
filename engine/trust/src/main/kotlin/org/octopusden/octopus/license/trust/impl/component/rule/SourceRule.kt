package org.octopusden.octopus.license.trust.impl.component.rule

import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.ReleasedFile
import org.octopusden.octopus.license.trust.impl.component.ComponentRule
import org.octopusden.octopus.license.trust.impl.shared.GradingSubject
import org.octopusden.octopus.license.trust.model.TrustReason
import org.octopusden.octopus.license.trust.model.TrustReasonCode

/** Which sources could see inside the read files? Each source misses what only the other records (D4). */
internal object SourceRule : ComponentRule {
    override fun reasons(subject: GradingSubject) =
        listOfNotNull(
            reason(TrustReasonCode.NO_BUILD_RECORD, "no build for", subject.read.without(Evidence.BUILD_RECORD)),
            reason(TrustReasonCode.BUILD_LIST_ONLY, "not indexed in Xray", subject.read.without(Evidence.BINARY_SCAN)),
        )

    private fun List<ReleasedFile>.without(source: Evidence) = filter { source !in it.evidence }

    private fun reason(
        code: TrustReasonCode,
        lead: String,
        files: List<ReleasedFile>,
    ) = files.takeIf { it.isNotEmpty() }?.let { TrustReason(code, "$lead: ${it.joinToString(" ") { file -> file.entry }}") }
}
