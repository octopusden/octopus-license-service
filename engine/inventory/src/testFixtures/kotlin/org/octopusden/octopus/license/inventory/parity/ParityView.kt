package org.octopusden.octopus.license.inventory.parity

import com.fasterxml.jackson.databind.JsonNode
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.FindingCode
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.inventory.model.SelectionReason

/**
 * One component version as both the prototype (`components/<c>.json`) and the service see it, in
 * the prototype's words, so the two compare field by field (D16). Packages are the prototype's
 * `components` rows: before verdicts, after its stage-11 exclusions, which only drop rows.
 */
data class ParityView(
    val builds: List<String>,
    val files: List<FileView>,
    val packages: Map<String, PackageView>,
    val findings: Set<FindingView>,
) {
    data class FileView(
        val entry: String,
        val kind: String,
        val declared: Boolean,
        val selectedBy: Set<String>,
        val path: String?,
        val build: String?,
        val buildFoundBy: String?,
        val status: String,
        val noBuild: Boolean?,
    )

    data class PackageView(
        val licenses: Set<String>,
        val sources: Set<String>,
        val files: Set<String>,
    )

    data class FindingView(
        val code: String,
        val subject: String,
        val detail: String,
    )

    companion object {
        private val OS_PACKAGE_TYPES = setOf("rpm", "deb", "alpine")
        private val READ_STATUSES = setOf("CHECKED", "BUILD_INFO_ONLY")
        private val CODES = FindingCode.entries.map { it.name }.toSet()

        fun of(inventory: LicenseInventory): ParityView {
            val kinds = inventory.releasedFiles.associate { it.entry to it.kind }
            return ParityView(
                builds = inventory.builds.map { it.toString() },
                files =
                    inventory.releasedFiles.map { file ->
                        val status = status(file.status, file.evidence)
                        FileView(
                            entry = file.entry,
                            kind = kind(file.kind),
                            declared = file.declared,
                            // The prototype names an image's own reason only; whether the registry declares it is `in_registry`.
                            selectedBy = file.selectedBy
                                .filterNot { file.kind == FileKind.DOCKER && it == SelectionReason.DECLARED }
                                .map(
                                    ::selectedBy,
                                ).toSet(),
                            path = file.path,
                            build = file.build?.toString(),
                            buildFoundBy = file.buildFoundBy?.name?.lowercase(),
                            status = status,
                            noBuild = if (status in READ_STATUSES) Evidence.BUILD_RECORD !in file.evidence else null,
                        )
                    },
                packages =
                    inventory.libraries
                        .filter {
                            listed(
                                it.id.type,
                                it.foundIn
                                    .mapNotNull(kinds::get)
                                    .map(::kind)
                                    .toSet(),
                            )
                        }.associate { found ->
                            found.id.toString() to PackageView(found.licenses, found.evidence.map(::source).toSet(), found.foundIn)
                        },
                findings = inventory.findings.map { FindingView(it.code.name, it.subject, it.detail) }.toSet(),
            )
        }

        fun ofPrototype(component: JsonNode): ParityView =
            ParityView(
                builds = component.path("builds").map { it.asText() },
                files =
                    component.path("artifacts").map { artifact ->
                        val status = artifact.path("status").asText()
                        FileView(
                            entry = artifact.path("entry").asText(),
                            kind = artifact.path("kind").asText(),
                            declared = artifact.path("in_registry").asBoolean(),
                            selectedBy = artifact.path("selected_by").map { it.asText() }.toSet(),
                            path = artifact.text("path"),
                            build = artifact.text("build"),
                            buildFoundBy = artifact.text("build_found_by"),
                            status = status,
                            noBuild = if (status in READ_STATUSES) artifact.path("no_build").asBoolean() else null,
                        )
                    },
                packages =
                    component.path("components").associate { row ->
                        row.path("purl").asText() to
                            PackageView(
                                row.path("xray_licences").map { it.asText() }.toSet(),
                                row.path("sources").map { it.asText() }.toSet(),
                                row.path("artifacts").map { it.asText() }.toSet(),
                            )
                    },
                findings =
                    (component.path("violations") + component.path("notes"))
                        .filter { it.path("code").asText() in CODES }
                        .map {
                            FindingView(
                                it.path("code").asText(),
                                it.path("subject").asText(),
                                it.path("detail").asText().licenseSpelling(),
                            )
                        }.toSet(),
            )

        // The prototype's stage 11 leaves docker layers out, counts OS packages, and counts Python seen only in images.
        private fun listed(
            type: String,
            kinds: Set<String>,
        ) = type != "docker" && type !in OS_PACKAGE_TYPES && !(type == "pypi" && kinds == setOf("docker"))

        private fun status(
            status: FileStatus,
            evidence: Set<Evidence>,
        ) = when (status) {
            FileStatus.READ -> if (Evidence.BINARY_SCAN in evidence) "CHECKED" else "BUILD_INFO_ONLY"
            FileStatus.NOT_READABLE -> "NOT_IN_XRAY"
            FileStatus.METADATA -> "SKIPPED_METADATA"
            else -> status.name
        }

        private fun kind(kind: FileKind) =
            when (kind) {
                FileKind.MAVEN -> "gav"
                FileKind.DOCKER -> "docker"
                FileKind.FILE_SHARE -> "file share"
            }

        private fun selectedBy(reason: SelectionReason) =
            when (reason) {
                SelectionReason.DECLARED -> "registry"
                SelectionReason.PATTERN -> "pattern"
                SelectionReason.DOCKER_BUILD -> "docker build"
            }

        private fun source(evidence: Evidence) =
            when (evidence) {
                Evidence.BINARY_SCAN -> "xray-artifact"
                Evidence.SBOM -> "sbom"
                Evidence.BUILD_RECORD -> "build-info"
                Evidence.CHECKSUM_MATCH -> "checksum"
            }

        private fun JsonNode.text(field: String) = path(field).takeIf { it.isTextual }?.asText()

        private fun String.licenseSpelling() = replace("licence", "license")
    }
}
