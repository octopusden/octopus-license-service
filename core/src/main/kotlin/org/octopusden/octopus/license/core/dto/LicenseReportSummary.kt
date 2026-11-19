package org.octopusden.octopus.license.core.dto

import java.time.Instant

/**
 * Enough of the report for a caller to decide a build outcome without opening the license files.
 *
 * @param missingLicenseTexts the licenses a listed library uses whose text no license file holds,
 * each once; empty when every text was written.
 */
data class LicenseReportSummary(
    val inputs: GenerationInputs,
    val trust: Trust,
    val libraryCounts: Map<Verdict, Int>,
    val violations: List<Violation>,
    val missingLicenseTexts: List<MissingLicenseText> = emptyList(),
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
 */
data class TrustReason(
    val code: TrustReasonCode,
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
    val reasons: List<TrustReasonCode>,
)

enum class TrustReasonCode {
    NO_ARTIFACT_IN_REGISTRY,
    ARTIFACT_NOT_FOUND,
    NOT_SCANNABLE,
    PARTIAL_ARTIFACTS,
    NO_THIRD_PARTY_FOUND,
    ECOSYSTEM_NOT_DETECTED,
    FULL_LIST_UNAVAILABLE,
    UNIDENTIFIED_BINARIES,
    UNKNOWN_LICENSES,
    NO_BUILD_RECORD,
    BUILD_LIST_ONLY,
}

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

/** @param url the text's URL, for [MissingTextReason.DOWNLOAD_FAILED] only. */
data class MissingLicenseText(
    val license: String,
    val reason: MissingTextReason,
    val url: String? = null,
)

enum class MissingTextReason {
    /** The license registry gives no text for the license. */
    NO_REGISTRY_ENTRY,

    /** The registry gives the text only as a URL, and it could not be downloaded. */
    DOWNLOAD_FAILED,
}
