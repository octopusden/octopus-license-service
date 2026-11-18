package org.octopusden.octopus.license.trust.impl.component.rule

import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode
import org.octopusden.octopus.license.trust.impl.component.ComponentRule
import org.octopusden.octopus.license.trust.impl.shared.GradingSubject
import org.octopusden.octopus.license.trust.model.TrustReason
import org.octopusden.octopus.license.trust.model.TrustReasonCode

/**
 * Was every selected file covered? With nothing covered, the scan's findings say why, one reason each;
 * with some covered, the missing ones show only as [TrustReasonCode.PARTIAL_ARTIFACTS] (D4).
 */
internal object CoverageRule : ComponentRule {
    private val WHY_NOT_COVERED =
        mapOf(
            FindingCode.NO_DISTRIBUTION to TrustReasonCode.NO_ARTIFACT_IN_REGISTRY,
            FindingCode.UNSUPPORTED_DISTRIBUTION_ENTRY to TrustReasonCode.NO_ARTIFACT_IN_REGISTRY,
            FindingCode.ARTIFACT_NOT_FOUND to TrustReasonCode.ARTIFACT_NOT_FOUND,
            FindingCode.NOT_IN_XRAY to TrustReasonCode.NOT_SCANNABLE,
            FindingCode.NO_BUILD_INFO to TrustReasonCode.NOT_SCANNABLE,
        )

    override fun reasons(subject: GradingSubject) =
        when {
            subject.covered.isEmpty() ->
                subject.findings.mapNotNull(::whyNotCovered).ifEmpty {
                    listOf(TrustReason(TrustReasonCode.NOT_SCANNABLE, "no registered artifact could be scanned"))
                }
            subject.covered.size < subject.selected.size ->
                listOf(
                    TrustReason(
                        TrustReasonCode.PARTIAL_ARTIFACTS,
                        "${subject.covered.size} of ${subject.selected.size} registered artifacts checked",
                    ),
                )
            else -> emptyList()
        }

    private fun whyNotCovered(finding: Finding) =
        WHY_NOT_COVERED[finding.code]?.let { TrustReason(it, "${finding.subject}: ${finding.detail}") }
}
