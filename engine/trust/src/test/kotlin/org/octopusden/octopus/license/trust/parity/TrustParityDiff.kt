package org.octopusden.octopus.license.trust.parity

/** Every way the service's trust differs from the prototype's, one line each (D10). */
object TrustParityDiff {
    fun diff(
        prototype: TrustParityView,
        service: TrustParityView,
    ): List<String> =
        listOfNotNull("trust: level: prototype ${prototype.level}, service ${service.level}".takeIf { prototype.level != service.level }) +
            (prototype.reasons - service.reasons).sorted().map { "trust: reason only in the prototype: $it" } +
            (service.reasons - prototype.reasons).sorted().map { "trust: reason only in the service: $it" } +
            (prototype.files.keys + service.files.keys)
                .filter { prototype.files[it] != service.files[it] }
                .map { "trust: file $it: prototype ${prototype.files[it] ?: "not graded"}, service ${service.files[it] ?: "not graded"}" }
}
