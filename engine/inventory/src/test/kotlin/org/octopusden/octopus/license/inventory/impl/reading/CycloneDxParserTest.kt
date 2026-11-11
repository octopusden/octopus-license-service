package org.octopusden.octopus.license.inventory.impl.reading

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.model.PackageId

class CycloneDxParserTest {
    @Test
    @DisplayName("each component's purl, without qualifiers, with its license ids, names or expressions")
    fun components() {
        val sbom =
            """
            {"bomFormat":"CycloneDX","components":[
              {"name":"lodash","purl":"pkg:npm/lodash@4.17.21?vcs_url=x","licenses":[{"license":{"id":"MIT"}}]},
              {"name":"leaflet","purl":"pkg:npm/leaflet@1.9.4","licenses":[{"license":{"name":"BSD-2-Clause License"}}]},
              {"name":"dual","purl":"pkg:npm/dual@1.0.0","licenses":[{"expression":"MIT OR Apache-2.0"}]},
              {"name":"none","purl":"pkg:npm/none@1.0.0"},
              {"name":"no purl"},
              {"name":"empty purl","purl":""}
            ]}
            """.trimIndent()

        assertEquals(
            mapOf(
                PackageId.parse("pkg:npm/lodash@4.17.21") to setOf("MIT"),
                PackageId.parse("pkg:npm/leaflet@1.9.4") to setOf("BSD-2-Clause License"),
                PackageId.parse("pkg:npm/dual@1.0.0") to setOf("MIT OR Apache-2.0"),
                PackageId.parse("pkg:npm/none@1.0.0") to emptySet(),
            ),
            CycloneDxParser.parse(sbom.toByteArray()),
        )
    }

    @Test
    @DisplayName("a component listed twice keeps the licenses of its last entry")
    fun lastWins() {
        val sbom = """{"components":[{"purl":"pkg:npm/a@1","licenses":[{"license":{"id":"MIT"}}]},{"purl":"pkg:npm/a@1"}]}"""

        assertEquals(mapOf(PackageId.parse("pkg:npm/a@1") to emptySet<String>()), CycloneDxParser.parse(sbom.toByteArray()))
    }

    @Test
    @DisplayName("SBOM file names: CycloneDX suffixes only")
    fun sbomNames() {
        assertEquals(
            listOf(true, true, true, true, false),
            listOf("portal.cdx.json", "bom.json", "portal-sbom.json", "portal-cyclonedx.json", "portal.json").map(CycloneDxParser::isSbom),
        )
    }
}
