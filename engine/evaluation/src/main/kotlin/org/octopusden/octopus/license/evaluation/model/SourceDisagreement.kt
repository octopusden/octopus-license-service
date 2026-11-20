package org.octopusden.octopus.license.evaluation.model

import org.octopusden.octopus.license.inventory.model.LicenseSource

/**
 * A library whose verdict depends on which source is believed: judged on each source's names alone,
 * the sources do not all get the same verdict. Reported only; the verdict stays the policy's on all
 * names together, where one allowed name wins (D12).
 *
 * @param bySource each source's names, canonical, in source order.
 */
data class SourceDisagreement(
    val bySource: Map<LicenseSource, List<String>>,
)
