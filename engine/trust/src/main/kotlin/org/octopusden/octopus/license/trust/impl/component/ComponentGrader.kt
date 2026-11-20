package org.octopusden.octopus.license.trust.impl.component

import org.octopusden.octopus.license.trust.impl.component.rule.CoverageRule
import org.octopusden.octopus.license.trust.impl.component.rule.EcosystemRule
import org.octopusden.octopus.license.trust.impl.component.rule.EmptyContentRule
import org.octopusden.octopus.license.trust.impl.component.rule.IdentificationRule
import org.octopusden.octopus.license.trust.impl.component.rule.SourceRule
import org.octopusden.octopus.license.trust.impl.shared.GradingSubject
import org.octopusden.octopus.license.trust.model.TrustReason

/**
 * The component's reasons: every rule over the whole assessment, in the prototype's order, each code
 * and detail once (D4). The counterpart of [org.octopusden.octopus.license.trust.impl.file.FileGrader];
 * a file's codes never reach these reasons (D5).
 */
internal object ComponentGrader {
    private val RULES = listOf(CoverageRule, EmptyContentRule, EcosystemRule, SourceRule, IdentificationRule)

    fun reasons(subject: GradingSubject): List<TrustReason> = RULES.flatMap { it.reasons(subject) }.distinct()
}
