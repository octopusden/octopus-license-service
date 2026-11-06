package org.octopusden.octopus.license.target.impl.describer

/**
 * The facts the registry's component-version call gives, in this module's own terms, so the rules
 * and the parsing never see the client's DTOs.
 */
internal data class RegistryComponent(
    val id: String,
    val version: String,
    val archived: Boolean,
    val external: Boolean,
    val buildSystem: String,
    val labels: Set<String>,
    val gav: String?,
    val docker: String?,
    val buildVersion: String?,
)
