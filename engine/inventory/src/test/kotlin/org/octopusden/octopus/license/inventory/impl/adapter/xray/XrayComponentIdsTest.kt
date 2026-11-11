package org.octopusden.octopus.license.inventory.impl.adapter.xray

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.octopusden.octopus.license.inventory.model.PackageId

class XrayComponentIdsTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "gav://com.google.guava:guava:32.1.2-jre,   pkg:maven/com.google.guava/guava@32.1.2-jre",
        "gav://..org.example:lib:1.0,               pkg:maven/org.example/lib@1.0",
        "gav://org.example:lib,                     pkg:maven/org.example/lib@",
        "go://golang.org/x/net:0.20.0,              pkg:golang/golang.org/x/net@0.20.0",
        "npm://@angular/core:17.3.1,                pkg:npm/@angular/core@17.3.1",
        "pypi://requests:2.31.0,                    pkg:pypi/requests@2.31.0",
        "rpm://openssl-libs:1:3.0.7-27.el9,         pkg:rpm/openssl-libs:1@3.0.7-27.el9",
        "docker://sha256__abc:latest,               pkg:docker/sha256__abc@latest",
        "conan://zlib:1.3,                          conan://zlib:1.3",
    )
    @DisplayName("an Xray id renders as the prototype's purl; an unknown scheme is kept as it is")
    fun toPackageId(
        xrayId: String,
        rendered: String,
    ) {
        assertEquals(rendered, XrayComponentIds.toPackageId(xrayId).toString())
    }

    @Test
    @DisplayName("a checksum id stays a checksum-only package")
    fun checksum() {
        val raw = "generic://sha256:${"ab".repeat(32)}/lib-1.0.jar"

        assertEquals(PackageId.ChecksumOnly("ab".repeat(32), "lib-1.0.jar"), XrayComponentIds.toPackageId(raw))
    }

    @Test
    @DisplayName("Maven and npm packages are asked about by their Xray ids; other types are not asked")
    fun toXrayId() {
        assertEquals(
            "gav://com.google.guava:guava:32.1.2-jre",
            XrayComponentIds.toXrayId(PackageId.Purl("maven", "com.google.guava/guava", "32.1.2-jre")),
        )
        assertEquals("npm://@angular/core:17.3.1", XrayComponentIds.toXrayId(PackageId.Purl("npm", "@angular/core", "17.3.1")))
        assertEquals(null, XrayComponentIds.toXrayId(PackageId.Purl("pypi", "requests", "2.31.0")))
    }

    @Test
    @DisplayName("Xray answers an id without its scheme")
    fun bare() {
        assertEquals("com.google.guava:guava:32.1.2-jre", XrayComponentIds.bare("gav://com.google.guava:guava:32.1.2-jre"))
        assertEquals("react:18.2.0", XrayComponentIds.bare("react:18.2.0"))
    }
}
