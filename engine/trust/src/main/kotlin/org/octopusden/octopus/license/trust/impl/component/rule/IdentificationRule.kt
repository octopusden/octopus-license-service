package org.octopusden.octopus.license.trust.impl.component.rule

import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.trust.impl.component.ComponentRule
import org.octopusden.octopus.license.trust.impl.shared.GradingSubject
import org.octopusden.octopus.license.trust.impl.shared.IdentificationShare
import org.octopusden.octopus.license.trust.model.TrustReason
import org.octopusden.octopus.license.trust.model.TrustReasonCode

/** Are too many third-party libraries unnamed, or without a known license? Over all of them (D4, D7). */
internal object IdentificationRule : ComponentRule {
    override fun reasons(subject: GradingSubject): List<TrustReason> {
        val total = subject.thirdParty.size
        val unidentified = subject.thirdParty.count { it.verdict == LibraryVerdict.UNIDENTIFIED }
        val unknown = subject.thirdParty.count { it.verdict == LibraryVerdict.UNKNOWN }
        return listOfNotNull(
            TrustReason(TrustReasonCode.UNIDENTIFIED_BINARIES, "$unidentified of $total outside files identified by checksum only")
                .takeIf { IdentificationShare.tooManyUnidentified(unidentified, total) },
            TrustReason(TrustReasonCode.UNKNOWN_LICENSES, "$unknown of $total outside libraries have no known license")
                .takeIf { IdentificationShare.tooManyUnknown(unknown, total) },
        )
    }
}
