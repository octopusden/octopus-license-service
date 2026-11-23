package org.octopusden.octopus.license.files

import org.octopusden.octopus.license.evaluation.model.AssessedLibrary
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.evaluation.model.LicenseAssessment
import org.octopusden.octopus.license.evaluation.model.SkippedPackages
import org.octopusden.octopus.license.inventory.model.Evidence
import org.octopusden.octopus.license.inventory.model.FileKind
import org.octopusden.octopus.license.inventory.model.FileStatus
import org.octopusden.octopus.license.inventory.model.FoundLibrary
import org.octopusden.octopus.license.inventory.model.LicenseInventory
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.inventory.model.ReleasedFile
import org.octopusden.octopus.license.inventory.model.SelectionReason
import org.octopusden.octopus.license.policy.impl.parser.LicenseRegistryParser
import org.octopusden.octopus.license.policy.model.LicensePolicy
import org.octopusden.octopus.license.target.model.ComponentVersion

const val APP = "org.example.payments:payments-app:jar"
const val APP_PATH = "rnd-maven-release-local/org/example/payments/payments-app/1.4.0/payments-app-1.4.0.jar"
const val IMAGE = "payments/ui:1.4.0"

const val COMMONS_IO = "pkg:maven/commons-io/commons-io@2.11.0"
const val SLF4J = "pkg:maven/org.slf4j/slf4j-api@1.7.36"
const val FOO_CORE = "pkg:maven/com.example/foo-core@1.0"
const val REACT = "pkg:npm/react@18.2.0"
const val OWN_LIB = "pkg:maven/org.example.own/lib@2.0"

fun file(
    entry: String = APP,
    status: FileStatus = FileStatus.READ,
    kind: FileKind = if (entry.count { it == ':' } >= 2) FileKind.MAVEN else FileKind.DOCKER,
    path: String? = if (kind == FileKind.MAVEN) APP_PATH else "docker-local/$entry/manifest.json",
    evidence: Set<Evidence> = if (status == FileStatus.READ) setOf(Evidence.BINARY_SCAN) else emptySet(),
) = ReleasedFile(
    entry = entry,
    kind = kind,
    selectedBy = setOf(SelectionReason.DECLARED),
    path = path,
    status = status,
    evidence = evidence,
)

fun lib(
    id: String,
    licenses: List<String> = listOf("Apache-2.0"),
    verdict: LibraryVerdict = if (licenses.isEmpty()) LibraryVerdict.UNKNOWN else LibraryVerdict.ALLOWED,
    foundIn: Set<String> = setOf(APP),
) = AssessedLibrary(
    FoundLibrary(PackageId.parse(id), licenses.toSet(), setOf(Evidence.BINARY_SCAN), foundIn, verdict == LibraryVerdict.INTERNAL),
    verdict,
    licenses,
    false,
)

fun assessment(
    files: List<ReleasedFile> = listOf(file()),
    libraries: List<AssessedLibrary> = emptyList(),
) = LicenseAssessment(
    LicenseInventory(
        ComponentVersion("payments", "1.4.0"),
        emptyList(),
        files,
        libraries.map { it.library }.sortedBy { it.id.toString() },
        emptyList(),
    ),
    libraries.sortedBy { it.library.id.toString() },
    SkippedPackages(0, 0, emptyMap()),
)

const val APACHE_TEXT = "Apache License\nVersion 2.0, January 2004\n"
const val MIT_TEXT = "MIT License\n"
const val GWT_URL = "http://www.gwtproject.org/terms.html"

fun policy(
    texts: String =
        """
        Apache-2.0=licenses/APACHE-2.0.txt
        MIT=licenses/MIT.txt
        CC0=licenses/cc0.html
        GWT=$GWT_URL
        """.trimIndent(),
): LicensePolicy =
    LicenseRegistryParser.parse(
        mapOf(
            "licenses-whitelist.txt" to "Apache-2.0|MIT|CC0|GWT",
            "licenses.properties" to texts,
            "licenses/APACHE-2.0.txt" to APACHE_TEXT,
            "licenses/MIT.txt" to MIT_TEXT,
            "licenses/cc0.html" to "<html>CC0</html>",
        ),
        "a1b2c3d",
    )
