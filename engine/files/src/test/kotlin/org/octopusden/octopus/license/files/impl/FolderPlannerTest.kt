package org.octopusden.octopus.license.files.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.octopusden.octopus.license.evaluation.model.LibraryVerdict
import org.octopusden.octopus.license.files.APP
import org.octopusden.octopus.license.files.COMMONS_IO
import org.octopusden.octopus.license.files.FOO_CORE
import org.octopusden.octopus.license.files.IMAGE
import org.octopusden.octopus.license.files.OWN_LIB
import org.octopusden.octopus.license.files.REACT
import org.octopusden.octopus.license.files.SLF4J
import org.octopusden.octopus.license.files.assessment
import org.octopusden.octopus.license.files.file
import org.octopusden.octopus.license.files.lib
import org.octopusden.octopus.license.inventory.model.FileStatus

class FolderPlannerTest {
    private fun plan(
        files: List<org.octopusden.octopus.license.inventory.model.ReleasedFile>,
        libraries: List<org.octopusden.octopus.license.evaluation.model.AssessedLibrary> = emptyList(),
    ) = FolderPlanner.plan(assessment(files, libraries))

    @Test
    @DisplayName("a folder per read file, in listing order (D2)")
    fun readFiles() {
        val folders = plan(listOf(file(IMAGE), file(APP)))
        assertEquals(listOf(IMAGE, APP), folders.map { it.releasedFile })
    }

    @ParameterizedTest
    @EnumSource(FileStatus::class, names = ["READ"], mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("no folder for a file that was not read (D2)")
    fun notRead(status: FileStatus) {
        assertEquals(emptyList<String>(), plan(listOf(file(status = status))).map { it.releasedFile })
    }

    @Test
    @DisplayName("a Maven file's folder is its Artifactory path's last segment")
    fun mavenName() {
        assertEquals(listOf("payments-app-1.4.0.jar"), plan(listOf(file())).map { it.name })
    }

    @Test
    @DisplayName("an image's folder is its entry with / and : as _, since its manifest's name says nothing")
    fun imageName() {
        assertEquals(listOf("payments_ui_1.4.0"), plan(listOf(file(IMAGE))).map { it.name })
    }

    @Test
    @DisplayName("a repeated folder name gets -2, then -3, in listing order")
    fun repeatedName() {
        val folders = plan(listOf(file("g:a:jar"), file("g:b:jar"), file("g:c:jar")))
        assertEquals(listOf("payments-app-1.4.0.jar", "payments-app-1.4.0.jar-2", "payments-app-1.4.0.jar-3"), folders.map { it.name })
    }

    @Test
    @DisplayName("a suffixed name never takes a name another file has on its own: every folder is distinct (D2)")
    fun suffixNeverCollides() {
        val folders = plan(listOf(file("a/b:2"), file("a_b:2"), file("a_b:2-2")))
        assertEquals(listOf("a_b_2", "a_b_2-2", "a_b_2-2-2"), folders.map { it.name })
    }

    @Test
    @DisplayName("no folder takes the name of the top-level NOT-CHECKED.txt: an archive cannot hold a path as file and folder (review)")
    fun notCheckedNameReserved() {
        val folders = plan(listOf(file(APP, path = "repo/g/a/1.0/NOT-CHECKED.txt")))
        assertEquals(listOf("NOT-CHECKED.txt-2"), folders.map { it.name })
    }

    @Test
    @DisplayName("a library is in the folder of each file it was found in")
    fun foundIn() {
        val folders =
            plan(
                listOf(file(APP), file(IMAGE)),
                listOf(lib(COMMONS_IO), lib(SLF4J, foundIn = setOf(APP, IMAGE)), lib(REACT, listOf("MIT"), foundIn = setOf(IMAGE))),
            )
        assertEquals(
            listOf(listOf(COMMONS_IO, SLF4J), listOf(SLF4J, REACT)),
            folders.map { folder -> folder.libraries.map { it.library.id.toString() } },
        )
    }

    @Test
    @DisplayName("our own libraries are left out; every other verdict is listed (D3)")
    fun verdicts() {
        val folder =
            plan(
                listOf(file()),
                listOf(
                    lib(OWN_LIB, emptyList(), LibraryVerdict.INTERNAL),
                    lib(COMMONS_IO),
                    lib(FOO_CORE, emptyList()),
                    lib("pkg:maven/org.gpl/gpl-lib@1.0", listOf("GPL-3.0"), LibraryVerdict.NOT_ALLOWED),
                    lib("generic://sha256:${"a".repeat(64)}/blob.dll", emptyList(), LibraryVerdict.UNIDENTIFIED),
                ),
            ).single()
        assertEquals(
            listOf(LibraryVerdict.UNIDENTIFIED, LibraryVerdict.UNKNOWN, LibraryVerdict.ALLOWED, LibraryVerdict.NOT_ALLOWED),
            folder.libraries.map { it.verdict },
        )
    }

    @Test
    @DisplayName("a read file with no third-party library still gets a folder")
    fun emptyFile() {
        val folder = plan(listOf(file()), listOf(lib(OWN_LIB, emptyList(), LibraryVerdict.INTERNAL))).single()
        assertEquals(emptyList<Any>(), folder.libraries)
    }
}
