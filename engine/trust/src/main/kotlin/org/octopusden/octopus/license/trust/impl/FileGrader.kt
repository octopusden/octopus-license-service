package org.octopusden.octopus.license.trust.impl

import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.ReleasedFile
import org.octopusden.octopus.license.trust.model.FileTrust
import org.octopusden.octopus.license.trust.model.TrustReasonCode

/**
 * One selected file's codes, from its own status, sources and libraries (D5). Never feeds the
 * component's reasons: those are the component rules' alone.
 */
internal object FileGrader {
    private val UNREAD =
        mapOf(
            FileStatus.UNSUPPORTED to TrustReasonCode.NO_ARTIFACT_IN_REGISTRY,
            FileStatus.NOT_FOUND to TrustReasonCode.ARTIFACT_NOT_FOUND,
            FileStatus.NOT_READABLE to TrustReasonCode.NOT_SCANNABLE,
        )

    fun grade(
        file: ReleasedFile,
        subject: GradingSubject,
    ) = FileTrust(file.entry, if (file.status == FileStatus.READ) read(file, subject) else listOfNotNull(UNREAD[file.status]))

    private fun read(
        file: ReleasedFile,
        subject: GradingSubject,
    ): List<TrustReasonCode> {
        val own = subject.thirdPartyIn(file.entry)
        val unidentified = own.count { it.verdict == LibraryVerdict.UNIDENTIFIED }
        val unknown = own.count { it.verdict == LibraryVerdict.UNKNOWN }
        return listOfNotNull(
            TrustReasonCode.BUILD_LIST_ONLY.takeIf { Evidence.BINARY_SCAN !in file.evidence },
            TrustReasonCode.NO_BUILD_RECORD.takeIf { Evidence.BUILD_RECORD !in file.evidence },
            TrustReasonCode.NO_THIRD_PARTY_FOUND.takeIf { own.isEmpty() },
            TrustReasonCode.UNIDENTIFIED_BINARIES.takeIf { IdentificationShare.tooManyUnidentified(unidentified, own.size) },
            TrustReasonCode.UNKNOWN_LICENSES.takeIf { IdentificationShare.tooManyUnknown(unknown, own.size) },
        )
    }
}
