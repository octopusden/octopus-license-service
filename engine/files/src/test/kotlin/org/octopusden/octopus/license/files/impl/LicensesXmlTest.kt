package org.octopusden.octopus.license.files.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.evaluation.model.AssessedLibrary
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.files.COMMONS_IO
import org.octopusden.octopus.license.files.FOO_CORE
import org.octopusden.octopus.license.files.GWT_URL
import org.octopusden.octopus.license.files.REACT
import org.octopusden.octopus.license.files.lib
import org.octopusden.octopus.license.files.policy
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/** The element names and nesting are those of the plugin's `LicenseSummaryWriter` (D5). */
class LicensesXmlTest {
    private fun dependencies(vararg libraries: AssessedLibrary): List<Element> {
        val xml = LicensesXml.render(libraries.toList(), policy())
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.inputStream())
        assertEquals("licenseSummary", document.documentElement.tagName)
        return document.getElementsByTagName("dependency").let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }
    }

    private fun Element.child(name: String): String? = getElementsByTagName(name).item(0)?.textContent

    private fun Element.licenses() =
        getElementsByTagName("license").let { nodes ->
            (0 until nodes.length).map { (nodes.item(it) as Element).let { lic -> lic.child("name") to lic.child("url") } }
        }

    @Test
    @DisplayName("a Maven library is written with its coordinates")
    fun maven() {
        val dependency = dependencies(lib(COMMONS_IO)).single()
        assertEquals(listOf("commons-io", "commons-io", "2.11.0"), listOf("groupId", "artifactId", "version").map { dependency.child(it) })
    }

    @Test
    @DisplayName("a non-Maven package has its purl type as group")
    fun npm() {
        val dependency = dependencies(lib(REACT, listOf("MIT"))).single()
        assertEquals(listOf("npm", "react", "18.2.0"), listOf("groupId", "artifactId", "version").map { dependency.child(it) })
    }

    @Test
    @DisplayName("a checksum-only file is generic, named by its file, with no version")
    fun checksumOnly() {
        val dependency = dependencies(lib("generic://sha256:${"a".repeat(64)}/blob.dll", emptyList(), LibraryVerdict.UNIDENTIFIED)).single()
        assertEquals(listOf("generic", "blob.dll", null), listOf("groupId", "artifactId", "version").map { dependency.child(it) })
    }

    @Test
    @DisplayName("a license's url is its licenses.properties location, registry path or URL")
    fun url() {
        val dependency = dependencies(lib(COMMONS_IO, listOf("Apache-2.0", "GWT"))).single()
        assertEquals(listOf("Apache-2.0" to "licenses/APACHE-2.0.txt", "GWT" to GWT_URL), dependency.licenses())
    }

    @Test
    @DisplayName("a license with no registry entry has a name and no url")
    fun noEntry() {
        val dependency = dependencies(lib(COMMONS_IO, listOf("SSPL-1.0"), LibraryVerdict.NOT_ALLOWED)).single()
        assertEquals(listOf("SSPL-1.0" to null), dependency.licenses())
    }

    @Test
    @DisplayName("no license: the plugin's comment in an empty licenses element")
    fun noLicense() {
        val xml = String(LicensesXml.render(listOf(lib(FOO_CORE, emptyList())), policy()))
        assertTrue("<licenses>\n        <!--No license information available. -->" in xml, xml)
    }

    @Test
    @DisplayName("indented by two, as the plugin's transformer is set")
    fun indented() {
        val xml = String(LicensesXml.render(listOf(lib(COMMONS_IO)), policy()))
        assertTrue("\n  <dependencies>\n    <dependency>\n      <groupId>commons-io</groupId>" in xml, xml)
    }
}
