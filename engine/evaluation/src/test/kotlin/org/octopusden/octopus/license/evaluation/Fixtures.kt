package org.octopusden.octopus.license.evaluation

import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.inventory.model.ReleasedFile
import org.octopusden.octopus.license.inventory.model.SelectionReason
import org.octopusden.octopus.license.policy.impl.parser.LicenseRegistryParser
import org.octopusden.octopus.license.target.model.ComponentVersion

const val ZIP = "org.example.portal:sample-portal:zip"
const val IMAGE = "sample/portal:2.4.0-118"

val zip = released(ZIP, FileKind.MAVEN, SelectionReason.DECLARED)
val image = released(IMAGE, FileKind.DOCKER, SelectionReason.DOCKER_BUILD)

private fun released(
    entry: String,
    kind: FileKind,
    selectedBy: SelectionReason,
) = ReleasedFile(entry, kind, setOf(selectedBy), status = FileStatus.READ, evidence = setOf(Evidence.BINARY_SCAN))

fun library(
    id: String,
    licenses: Set<String> = emptySet(),
    foundIn: Set<String> = setOf(ZIP),
    ours: Boolean = false,
) = FoundLibrary(PackageId.parse(id), licenses, setOf(Evidence.BINARY_SCAN), foundIn, ours)

/** The design's worked example registry: `Apache-2.0` and `MIT` allowed, one synonym, one override. */
val policy =
    LicenseRegistryParser.parse(
        mapOf(
            "licenses-whitelist.txt" to "Apache-2.0|MIT",
            "merges.txt" to "Apache-2.0|The Apache Software License, Version 2.0",
            "thirdparty-licenses.properties" to "org.example--sample-lib--[1.0,2.0)=MIT",
        ),
        "a1b2c3d",
    )

fun inventory(
    vararg libraries: FoundLibrary,
    files: List<ReleasedFile> = listOf(zip, image),
) = LicenseInventory(ComponentVersion("sample-portal", "2.4.0"), emptyList(), files, libraries.toList(), emptyList())
