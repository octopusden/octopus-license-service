package org.octopusden.octopus.license.target

internal fun registryComponent(
    id: String = "dmp-bundle",
    version: String = "1.4.12",
    archived: Boolean = false,
    external: Boolean = true,
    buildSystem: String = "GRADLE",
    labels: Set<String> = emptySet(),
    gav: String? = null,
    docker: String? = null,
    buildVersion: String? = null,
) = RegistryComponent(id, version, archived, external, buildSystem, labels, gav, docker, buildVersion)
