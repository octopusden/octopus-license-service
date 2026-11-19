package org.octopusden.octopus.license.files.impl

import org.octopusden.octopus.license.evaluation.model.AssessedLibrary
import org.octopusden.octopus.license.inventory.model.PackageId
import org.octopusden.octopus.license.policy.model.LicensePolicy
import org.octopusden.octopus.license.policy.model.LicenseText
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * `licenses.xml` in the layout of the plugin's `LicenseSummaryWriter`: the same elements, the same
 * comment for a library with no license, the same two-space indentation (D5).
 */
internal object LicensesXml {
    const val FILE_NAME = "licenses.xml"
    private const val GENERIC = "generic"

    fun render(
        libraries: List<AssessedLibrary>,
        policy: LicensePolicy,
    ): ByteArray {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val dependencies = document.element("dependencies")
        document.appendChild(document.element("licenseSummary").also { it.appendChild(dependencies) })
        libraries.forEach { dependencies.appendChild(document.dependency(it, policy)) }
        return ByteArrayOutputStream()
            .also { out ->
                TransformerFactory
                    .newInstance()
                    .newTransformer()
                    .apply {
                        setOutputProperty(OutputKeys.INDENT, "yes")
                        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
                        setOutputProperty(OutputKeys.ENCODING, Charsets.UTF_8.name())
                    }.transform(DOMSource(document), StreamResult(out))
            }.toByteArray()
    }

    private fun Document.dependency(
        library: AssessedLibrary,
        policy: LicensePolicy,
    ): Element {
        val (groupId, artifactId, version) = coordinates(library.library.id)
        val dependency = element("dependency")
        dependency.appendChild(element("groupId", groupId))
        dependency.appendChild(element("artifactId", artifactId))
        version?.let { dependency.appendChild(element("version", it)) }
        val licenses = element("licenses")
        if (library.licenses.isEmpty()) {
            licenses.appendChild(createComment("No license information available. "))
        }
        library.licenses.forEach { name ->
            val license = element("license")
            license.appendChild(element("name", name))
            location(policy.text(name))?.let { license.appendChild(element("url", it)) }
            licenses.appendChild(license)
        }
        dependency.appendChild(licenses)
        return dependency
    }

    private fun coordinates(id: PackageId): Triple<String, String, String?> =
        when (id) {
            is PackageId.Purl ->
                id.mavenCoordinates()?.let { Triple(it.groupId, it.artifactId, it.version) } ?: Triple(id.type, id.name, id.version)
            is PackageId.ChecksumOnly -> Triple(GENERIC, id.fileName, null)
            is PackageId.Unrecognized -> Triple(id.type, id.raw, null)
        }

    private fun location(text: LicenseText?) =
        when (text) {
            is LicenseText.InRegistry -> text.path
            is LicenseText.AtUrl -> text.url
            null -> null
        }

    private fun Document.element(
        name: String,
        text: String? = null,
    ): Element = createElement(name).also { element -> text?.let { element.appendChild(createTextNode(it)) } }
}
