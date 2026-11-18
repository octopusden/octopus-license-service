package org.octopusden.octopus.license.trust

import org.octopusden.octopus.license.evaluation.model.AssessedLibrary
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.evaluation.model.LicenseAssessment
import org.octopusden.octopus.license.evaluation.model.SkippedPackages
import org.octopusden.octopus.license.inventory.model.BuildRef
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.Finding
import org.octopusden.octopus.license.inventory.model.FindingCode
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.inventory.model.ReleasedFile
import org.octopusden.octopus.license.inventory.model.SelectionReason
import org.octopusden.octopus.license.target.model.ComponentVersion

const val ZIP = "org.example.portal:sample-portal:zip"
const val IMAGE = "sample/portal:2.4.0-118"
const val CONFIG = "org.example.portal:sample-portal-config:yaml"
const val LEGACY = "org.example.portal:sample-legacy:jar"
const val POM = "org.example.portal:sample-portal:pom"

val BOTH_SOURCES = setOf(Evidence.BINARY_SCAN, Evidence.BUILD_RECORD)

fun file(
    entry: String = ZIP,
    status: FileStatus = FileStatus.READ,
    evidence: Set<Evidence> = if (status == FileStatus.READ) BOTH_SOURCES else emptySet(),
    kind: FileKind = FileKind.MAVEN,
    build: BuildRef? = if (Evidence.BUILD_RECORD in evidence) BuildRef("sample-portal_ee", "118") else null,
) = ReleasedFile(
    entry = entry,
    kind = kind,
    selectedBy = if (status == FileStatus.NOT_SELECTED) emptySet() else setOf(SelectionReason.DECLARED),
    build = build,
    status = status,
    evidence = evidence,
)

fun lib(
    id: String,
    verdict: LibraryVerdict = LibraryVerdict.ALLOWED,
    foundIn: Set<String> = setOf(ZIP),
    evidence: Set<Evidence> = setOf(Evidence.BINARY_SCAN),
) = AssessedLibrary(
    FoundLibrary(PackageId.parse(id), emptySet(), evidence, foundIn, verdict == LibraryVerdict.INTERNAL),
    verdict,
    emptyList(),
    false,
)

/** [n] Maven libraries of one [verdict], ids distinct per verdict so batches can be mixed. */
fun libs(
    n: Int,
    verdict: LibraryVerdict = LibraryVerdict.ALLOWED,
    foundIn: Set<String> = setOf(ZIP),
) = (1..n).map { lib("pkg:maven/org.thirdparty/${verdict.name.lowercase()}-$it@1.0", verdict, foundIn) }

fun finding(
    code: FindingCode,
    subject: String,
    detail: String = "",
) = Finding(code, subject, detail)

fun assessment(
    files: List<ReleasedFile> = listOf(file()),
    libraries: List<AssessedLibrary> = emptyList(),
    findings: List<Finding> = emptyList(),
) = LicenseAssessment(
    LicenseInventory(
        ComponentVersion("sample-portal", "2.4.0"),
        emptyList(),
        files,
        libraries.map { it.library }.sortedBy { it.id.toString() },
        findings,
    ),
    libraries.sortedBy { it.library.id.toString() },
    SkippedPackages(0, 0, emptyMap()),
)

fun reasons(vararg pairs: Pair<org.octopusden.octopus.license.trust.model.TrustReasonCode, String>) =
    pairs.map { (code, detail) ->
        org.octopusden.octopus.license.trust.model
            .TrustReason(code, detail)
    }
