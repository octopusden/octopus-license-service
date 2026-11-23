package org.octopusden.octopus.license.target.impl.describer

internal fun registryComponent(
    id: String = "sample-app",
    version: String = "1.4.12",
    archived: Boolean = false,
    external: Boolean = true,
    buildSystem: String = "GRADLE",
    testComponent: Boolean = false,
    labels: Set<String> = emptySet(),
    gav: String? = null,
    docker: String? = null,
    buildVersion: String? = null,
) = RegistryComponent(id, version, archived, external, buildSystem, testComponent, labels, gav, docker, buildVersion)
