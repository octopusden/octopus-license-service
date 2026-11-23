package org.octopusden.octopus.license.core.dto

import java.time.Instant

/**
 * Enough of the report for a caller to decide a build outcome without opening the license files.
 *
 * @param missingLicenseTexts the licenses a listed library uses whose text no license file holds,
 * each once; empty when every text was written.
 * @param licenseDisagreements third-party libraries whose verdict depends on which source is
 * believed; the verdict in [violations] and [libraryCounts] is the one on all names together.
 */
data class LicenseReportSummary(
    val inputs: GenerationInputs,
    val trust: Trust,
    val libraryCounts: Map<Verdict, Int>,
    val violations: List<Violation>,
    val missingLicenseTexts: List<MissingLicenseText> = emptyList(),
    val licenseDisagreements: List<LicenseDisagreement> = emptyList(),
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

/**
 * Whether the library list is complete: for the component, and for each released file.
 *
 * @param level the lowest of [reasons], [TrustLevel.HIGH] when there are none; the files' levels never lower it.
 * @param files every released file that was selected for the scan, in listing order.
 */
data class Trust(
    val level: TrustLevel,
    val reasons: List<TrustReason>,
    val files: List<FileTrust>,
)

/**
 * One reason the list may be incomplete. [level], [title], [meaning] and [fix] are fixed per [code];
 * [detail] says which files or libraries it is about.
 *
 * [code] is a string, not an enum: the service adds codes as its rules change, and a client built
 * before a code existed still reads it, with the level and texts that come with it.
 */
data class TrustReason(
    val code: String,
    val level: TrustLevel,
    val title: String,
    val detail: String,
    val meaning: String,
    val fix: String,
)

/** One released file's trust. Codes only: the component's [TrustReason]s carry the details. */
data class FileTrust(
    val file: String,
    val level: TrustLevel,
    val reasons: List<String>,
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

/**
 * A library whose verdict is anything but [Verdict.ALLOWED], and the released files it ships in.
 *
 * @param preliminary a CHECK report's violation found only in files Xray has not scanned yet; a
 * build does not fail on it. Never set in a FULL report.
 */
data class Violation(
    val library: String,
    val verdict: Verdict,
    val licenses: List<String>,
    val files: List<String>,
    val preliminary: Boolean = false,
)

/**
 * @param reason why, as text so a newer service can add a reason an older caller still reads:
 * `NO_REGISTRY_ENTRY`, the license registry gives no text; `DOWNLOAD_FAILED`, the registry gives
 * only a URL and it could not be downloaded.
 * @param url the text's URL, for `DOWNLOAD_FAILED` only.
 */
data class MissingLicenseText(
    val license: String,
    val reason: String,
    val url: String? = null,
)

/**
 * A library whose sources name licenses that, judged apart, give different verdicts: Xray's
 * `GPL-3.0` against an SBOM's `MIT`, for example. [verdict] is the one the report uses, on all names
 * together, where one allowed name wins.
 *
 * @param bySource source (`BINARY_SCAN`, `SBOM`, `LICENSE_DATABASE`) to its canonical license names.
 */
data class LicenseDisagreement(
    val library: String,
    val verdict: Verdict,
    val bySource: Map<String, List<String>>,
    val files: List<String>,
)
