package org.octopusden.octopus.license.policy.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.octopusden.octopus.license.policy.impl.parser.policy
import org.octopusden.octopus.license.policy.impl.parser.registryFiles

/**
 * The worked example of the design, row by row. The first four rows are the prototype's answers for
 * the same registry. The last is where the service goes further (D5): the prototype ignores case
 * only for `merges.txt`, so it calls `Borland` not allowed; the service matches the hidden list
 * case-insensitively too.
 */
class WorkedExampleTest {
    private val policy = policy(registryFiles(overrides = "org.example--sample-lib--[1.0,2.0)=MIT"))

    @ParameterizedTest(name = "{0} found with [{1}] -> {2} [{3}]")
    @CsvSource(
        delimiter = ';',
        value = [
            "org.example:sample-lib:1.5; GPL-3.0; ALLOWED; MIT",
            "org.example:sample-lib:2.1; GPL-3.0; NOT_ALLOWED; GPL-3.0",
            "org.example:other:1.0; apache 2|GPL-2.0; ALLOWED; Apache-2.0|GPL-2.0",
            "left-pad; Unknown; UNKNOWN; ''",
            "org.example:old:3.0; Borland; ALLOWED; BORLAND",
        ],
    )
    @DisplayName("the design's worked example")
    fun row(
        library: String,
        found: String,
        verdict: LicenseVerdict,
        reported: String,
    ) {
        val coordinates = library.split(":").takeIf { it.size == 3 }?.let { (g, a, v) -> MavenCoordinates(g, a, v) }
        val judgement = policy.judge(found.split("|").toSet(), coordinates)
        assertEquals(verdict, judgement.verdict)
        assertEquals(reported.split("|").filter { it.isNotEmpty() }, judgement.licenses)
    }
}
