package org.octopusden.octopus.license.inventory.model

data class BuildRef(
    val name: String,
    val number: String,
) {
    override fun toString() = "$name/$number"
}
