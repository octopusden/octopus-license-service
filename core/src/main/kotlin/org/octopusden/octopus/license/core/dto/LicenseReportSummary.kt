package org.octopusden.octopus.license.core.dto

import java.time.Instant

/**
 * Enough of the report for a caller to decide a build outcome without opening the license files.
 *
 * [trust] is null until the service computes trust levels.
 */
data class LicenseReportSummary(
    val inputs: GenerationInputs,
    val trust: Trust?,
    val libraryCounts: Map<Verdict, Int>,
    val violations: List<Violation>,
)

/**
 * What a generation read. The service stores nothing, so two generations for one version can
 * differ; these values are what explains the difference later.
 */
data class GenerationInputs(
    val licenseRegistryCommit: String?,
    val generatedAt: Instant,
    val serviceVersion: String,
)

data class Trust(
    val level: TrustLevel,
    val reasons: List<TrustReason>,
)

data class TrustReason(
    val reason: String,
    val fix: String,
)

/** Whether the library list of a component version is complete. */
enum class TrustLevel {
    HIGH,
    MEDIUM,
    LOW,
    NONE,
}

/** The verdict on one third-party library, from the license registry. */
enum class Verdict {
    ALLOWED,
    NOT_ALLOWED,

    /** No license is known for the library. */
    UNKNOWN,

    /** A file nobody could name as a library. */
    UNIDENTIFIED,
}

/** A library whose verdict is anything but [Verdict.ALLOWED], and the released files it ships in. */
data class Violation(
    val library: String,
    val verdict: Verdict,
    val licenses: List<String>,
    val files: List<String>,
)
