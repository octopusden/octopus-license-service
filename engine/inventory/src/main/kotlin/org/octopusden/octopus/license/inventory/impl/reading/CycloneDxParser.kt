package org.octopusden.octopus.license.inventory.impl.reading

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.octopusden.octopus.license.inventory.model.PackageId

/** Reads the components of a CycloneDX JSON SBOM; which build files are SBOMs is decided here too. */
internal object CycloneDxParser {
    private val SUFFIXES = listOf(".cdx.json", "bom.json", "sbom.json", "-cyclonedx.json")
    private val mapper = ObjectMapper()

    fun isSbom(fileName: String) = SUFFIXES.any { fileName.endsWith(it) }

    /** A component listed twice keeps its last entry's licenses (D10.8). */
    fun parse(sbom: ByteArray): Map<PackageId, Set<String>> =
        mapper
            .readTree(sbom)
            .path("components")
            .filter { it.path("purl").isTextual && it.path("purl").asText().isNotEmpty() }
            .associate { component ->
                PackageId.parse(component.path("purl").asText().substringBefore('?')) to licenses(component)
            }

    private fun licenses(component: JsonNode) =
        component
            .path("licenses")
            .mapNotNull { entry ->
                listOf(entry.path("license").path("id"), entry.path("license").path("name"), entry.path("expression"))
                    .firstOrNull { it.isTextual && it.asText().isNotEmpty() }
                    ?.asText()
            }.toSet()
}
