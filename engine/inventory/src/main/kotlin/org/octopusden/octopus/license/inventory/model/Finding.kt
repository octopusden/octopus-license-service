package org.octopusden.octopus.license.inventory.model

/** Something the scan noticed, with the prototype's subject and detail texts (D3). */
data class Finding(
    val code: FindingCode,
    val subject: String,
    val detail: String = "",
)
