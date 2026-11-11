package org.octopusden.octopus.license.inventory.impl.port

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.inventory.impl.FakeArtifactStore
import org.octopusden.octopus.license.inventory.impl.stored

class ArtifactStoreTest {
    @Test
    @DisplayName("a Maven file by coordinates is asked at the repository layout g/a/v/a-v[-classifier].ext")
    fun byCoordinates() {
        val zip = stored("sample-portal-2.4.0-118.zip")
        val store = FakeArtifactStore(zip)

        assertEquals(zip, store.byCoordinates("org.example.portal", "sample-portal", "2.4.0-118", "zip"))
        assertEquals(null, store.byCoordinates("org.example.portal", "sample-portal", "2.4.0-118", "zip", "linux"))
        assertEquals(
            listOf(
                "path:org/example/portal/sample-portal/2.4.0-118/sample-portal-2.4.0-118.zip",
                "path:org/example/portal/sample-portal/2.4.0-118/sample-portal-2.4.0-118-linux.zip",
            ),
            store.asked,
        )
    }
}
