package org.octopusden.octopus.license.inventory.impl.adapter.xray

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.inventory.impl.adapter.StubJfrog
import org.octopusden.octopus.license.inventory.impl.adapter.StubJfrog.Companion.COMPONENTS
import org.octopusden.octopus.license.inventory.impl.adapter.StubJfrog.Companion.STATUS
import org.octopusden.octopus.license.inventory.impl.adapter.StubJfrog.Companion.SUMMARY
import org.octopusden.octopus.license.inventory.impl.adapter.noWaitCalls
import org.octopusden.octopus.license.inventory.impl.port.data.StoredFile
import org.octopusden.octopus.license.inventory.model.PackageId
import java.time.Duration

class XrayAdaptersTest {
    private val jfrog = StubJfrog()
    private val sleeps = mutableListOf<Duration>()
    private val zip =
        StoredFile("rnd-maven-release-local", "org/example/portal/sample-portal/2.4.0", "sample-portal-2.4.0.zip", "0a1b", "9f8e")

    private val summary =
        200 to
            """{"artifacts":[{"general":{"name":"sample-portal-2.4.0.zip"},"licenses":[
                 {"name":"Apache-2.0","components":["gav://org.springframework:spring-core:6.1.4","gav://com.google.guava:guava:32.1.2-jre"]},
                 {"name":"MIT","components":["gav://com.google.guava:guava:32.1.2-jre"]}]}]}"""

    private fun scanner(budget: Duration = Duration.ofMinutes(5)) =
        XrayBinaryScanner(jfrog.xray, noWaitCalls(), budget, Duration.ofSeconds(10)) { sleeps += it }

    private fun status(sca: String) = 200 to """{"overall":{"status":"$sca"},"details":{"sca":{"status":"$sca"}}}"""

    @AfterEach
    fun stop() = jfrog.close()

    @Test
    @DisplayName("a finished scan is read: each component with the licenses it is listed under")
    fun done() {
        jfrog.answer(STATUS, status("DONE"))
        jfrog.answer(SUMMARY, summary)

        assertEquals(
            mapOf(
                PackageId.parse("pkg:maven/org.springframework/spring-core@6.1.4") to setOf("Apache-2.0"),
                PackageId.parse("pkg:maven/com.google.guava/guava@32.1.2-jre") to setOf("Apache-2.0", "MIT"),
            ),
            scanner().scan(zip),
        )
        assertEquals(
            """{"repo":"rnd-maven-release-local","path":"org/example/portal/sample-portal/2.4.0/sample-portal-2.4.0.zip"}""",
            jfrog.requests(STATUS).single().body,
        )
        assertEquals(
            """{"paths":["default/rnd-maven-release-local/org/example/portal/sample-portal/2.4.0/sample-portal-2.4.0.zip"]}""",
            jfrog.requests(SUMMARY).single().body,
        )
        assertEquals(emptyList<Duration>(), sleeps)
    }

    @Test
    @DisplayName("a pending scan is asked again every poll interval until done")
    fun pendingThenDone() {
        jfrog.answer(STATUS, status("PENDING"), status("SCANNING"), status("DONE"))
        jfrog.answer(SUMMARY, summary)

        assertEquals(2, scanner().scan(zip)?.size)
        assertEquals(listOf(Duration.ofSeconds(10), Duration.ofSeconds(10)), sleeps)
    }

    @Test
    @DisplayName("the license (SCA) status decides, not the overall one")
    fun scaDecides() {
        jfrog.answer(STATUS, 200 to """{"overall":{"status":"PENDING"},"details":{"sca":{"status":"DONE"}}}""")
        jfrog.answer(SUMMARY, summary)

        scanner().scan(zip)

        assertEquals(emptyList<Duration>(), sleeps)
    }

    @Test
    @DisplayName("not supported: no wait, and Xray's error for the path means not indexed")
    fun notSupported() {
        jfrog.answer(STATUS, status("NOT_SUPPORTED"))
        jfrog.answer(
            SUMMARY,
            200 to
                """{"artifacts":[],"errors":[{"identifier":"default/x","error":"Artifact doesn't exist or not indexed/cached in Xray"}]}""",
        )

        assertEquals(null, scanner().scan(zip))
        assertEquals(emptyList<Duration>(), sleeps)
    }

    @Test
    @DisplayName("not scanned: no wait, the summary is read as it is")
    fun notScanned() {
        jfrog.answer(STATUS, status("NOT_SCANNED"))
        jfrog.answer(SUMMARY, summary)

        assertEquals(2, scanner().scan(zip)?.size)
        assertEquals(emptyList<Duration>(), sleeps)
    }

    @Test
    @DisplayName("a status Xray answers with a client error does not stop the scan: the summary decides")
    fun statusClientError() {
        jfrog.answer(STATUS, 400 to """{"error":"Resource does not exist in Artifactory"}""")
        jfrog.answer(SUMMARY, summary)

        assertEquals(2, scanner().scan(zip)?.size)
    }

    @Test
    @DisplayName(
        "still pending when the scan's budget is spent: the summary is read as it is, and the budget is shared by the scan's files",
    )
    fun budgetSpent() {
        jfrog.answer(STATUS, status("PENDING"))
        jfrog.answer(SUMMARY, 200 to """{"artifacts":[],"errors":[{"identifier":"default/x","error":"not indexed"}]}""")
        val scanner = scanner(budget = Duration.ofSeconds(25))

        assertEquals(null, scanner.scan(zip))
        assertEquals(listOf(Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofSeconds(5)), sleeps)

        scanner.scan(zip.copy(name = "other.zip"))
        assertEquals(3, sleeps.size)
    }

    @Test
    @DisplayName("a package Xray lists under no license is not found from the scan (D10.10)")
    fun noLicenseEntry() {
        jfrog.answer(STATUS, status("DONE"))
        jfrog.answer(SUMMARY, 200 to """{"artifacts":[{"general":{"name":"a.zip"},"licenses":[]}]}""")

        assertEquals(emptyMap<PackageId, Set<String>>(), scanner().scan(zip))
    }

    @Test
    @DisplayName("an Xray server error on the summary fails the scan as Xray unavailable")
    fun summaryUnavailable() {
        jfrog.answer(STATUS, status("DONE"))
        jfrog.answer(SUMMARY, 503 to "")

        assertEquals("Xray", assertThrows<InventorySourceUnavailableException> { scanner().scan(zip) }.system)
    }

    @Test
    @DisplayName("250 packages are asked about in three calls of 100, 100 and 50; answers come back without the scheme")
    fun batches() {
        jfrog.answer(COMPONENTS, 200 to """{"artifacts":[{"general":{"component_id":"g:a-0:1"},"licenses":[{"name":"MIT"}]}]}""")
        val packages = (0 until 250).map { PackageId.Purl("maven", "g/a-$it", "1") }

        val licenses = XrayLicenseDatabase(jfrog.xray, noWaitCalls()).licensesOf(packages)

        assertEquals(listOf(100, 100, 50), jfrog.requests(COMPONENTS).map { Regex("component_id").findAll(it.body).count() })
        assertEquals(mapOf(PackageId.Purl("maven", "g/a-0", "1") to setOf("MIT")), licenses)
    }

    @Test
    @DisplayName("npm ids are asked as npm://name:version; other types are not asked")
    fun npmIds() {
        jfrog.answer(
            COMPONENTS,
            200 to """{"artifacts":[{"general":{"component_id":"@angular/core:17.3.1"},"licenses":[{"name":"MIT"}]}]}""",
        )

        val licenses =
            XrayLicenseDatabase(jfrog.xray, noWaitCalls())
                .licensesOf(listOf(PackageId.Purl("npm", "@angular/core", "17.3.1"), PackageId.Purl("pypi", "requests", "2.31.0")))

        assertEquals("""{"component_details":[{"component_id":"npm://@angular/core:17.3.1"}]}""", jfrog.requests(COMPONENTS).single().body)
        assertEquals(mapOf(PackageId.Purl("npm", "@angular/core", "17.3.1") to setOf("MIT")), licenses)
    }

    @Test
    @DisplayName("a failed batch leaves its packages without licenses; the other batches still answer")
    fun failedBatch() {
        jfrog.answer(
            COMPONENTS,
            500 to "",
            500 to "",
            500 to "",
            200 to """{"artifacts":[{"general":{"component_id":"g:a-100:1"},"licenses":[{"name":"MIT"}]}]}""",
        )
        val packages = (0 until 101).map { PackageId.Purl("maven", "g/a-$it", "1") }

        val licenses = XrayLicenseDatabase(jfrog.xray, noWaitCalls()).licensesOf(packages)

        assertEquals(mapOf(PackageId.Purl("maven", "g/a-100", "1") to setOf("MIT")), licenses)
    }
}
