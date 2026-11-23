package org.octopusden.octopus.license.evaluation

import org.octopusden.octopus.license.evaluation.impl.LibraryJudge
import org.octopusden.octopus.license.evaluation.impl.PackageScope
import org.octopusden.octopus.license.evaluation.impl.Scope
import org.octopusden.octopus.license.evaluation.model.LicenseAssessment
import org.octopusden.octopus.license.evaluation.model.SkippedPackages
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.policy.model.LicensePolicy

/**
 * Which libraries of an inventory count, and the verdict on each: the prototype's stage 11. Pure
 * (D1): the inventory and the policy are pushed in, nothing is read. Keeps no state, so one
 * instance serves every generation.
 */
class LicenseEvaluator {
    fun evaluate(
        inventory: LicenseInventory,
        policy: LicensePolicy,
    ): LicenseAssessment {
        val scope = PackageScope(inventory.releasedFiles)
        val byScope = inventory.libraries.groupBy(scope::of)
        val skipped = byScope[Scope.OS_PACKAGE].orEmpty() + byScope[Scope.BASE_IMAGE_PYTHON].orEmpty()
        val judge = LibraryJudge(policy)
        return LicenseAssessment(
            inventory = inventory,
            libraries = byScope[Scope.COUNTED].orEmpty().map(judge::judge),
            skipped =
                SkippedPackages(
                    osPackages = byScope[Scope.OS_PACKAGE].orEmpty().size,
                    baseImagePython = byScope[Scope.BASE_IMAGE_PYTHON].orEmpty().size,
                    perFile = skipped.flatMap { it.foundIn }.groupingBy { it }.eachCount(),
                ),
        )
    }
}
