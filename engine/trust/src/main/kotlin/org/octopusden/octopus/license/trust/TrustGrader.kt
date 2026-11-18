package org.octopusden.octopus.license.trust

import org.octopusden.octopus.license.evaluation.model.LicenseAssessment
import org.octopusden.octopus.license.trust.impl.component.ComponentGrader
import org.octopusden.octopus.license.trust.impl.file.FileGrader
import org.octopusden.octopus.license.trust.impl.shared.GradingSubject
import org.octopusden.octopus.license.trust.model.TrustGrade

/**
 * How far a component version's library list, and each released file's, can be trusted to be
 * complete: the prototype's stage 12. Pure (D1): the assessment and the registry labels are pushed
 * in, nothing is read. Never judges a license and never decides a build's outcome. Keeps no state,
 * so one instance serves every generation.
 */
class TrustGrader {
    fun grade(
        assessment: LicenseAssessment,
        labels: Set<String>,
    ): TrustGrade {
        val subject = GradingSubject.of(assessment, labels)
        return TrustGrade(
            reasons = ComponentGrader.reasons(subject),
            files = subject.selected.map { FileGrader.grade(it, subject) },
        )
    }
}
