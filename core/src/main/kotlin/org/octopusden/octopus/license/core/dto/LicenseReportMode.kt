package org.octopusden.octopus.license.core.dto

/** How thorough a report is. Regular builds ask for [CHECK]; RC and RELEASE for [FULL]. */
enum class LicenseReportMode {
    /** Waits until Xray has scanned every released file, and produces the license files. */
    FULL,

    /** Judges what Xray knows at that moment, without waiting: quick, but preliminary. */
    CHECK,
}
