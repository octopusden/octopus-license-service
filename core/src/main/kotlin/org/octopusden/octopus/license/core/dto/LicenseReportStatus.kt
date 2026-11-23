package org.octopusden.octopus.license.core.dto

enum class LicenseReportStatus {
    /** Generation has started and has not finished yet; poll again. */
    RUNNING,

    /** Generation finished; the summary is set and the license files can be downloaded. */
    DONE,

    /** The component is not covered by the service; [LicenseReport.exclusionReason] says why. */
    EXCLUDED,

    /** Generation failed; [LicenseReport.error] says why. No license files exist. */
    FAILED,
}
