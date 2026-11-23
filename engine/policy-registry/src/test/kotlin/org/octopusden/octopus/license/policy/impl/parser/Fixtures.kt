package org.octopusden.octopus.license.policy.impl.parser

import org.octopusden.octopus.license.policy.model.LicensePolicy

const val COMMIT = "a1b2c3d"

/** The files of a sample license registry; a test names only the files it varies. */
fun registryFiles(
    whitelist: String? = "Apache-2.0|MIT",
    hidden: String? = "BORLAND",
    merges: String? = "Apache-2.0|The Apache Software License, Version 2.0|Apache 2\nMIT|The MIT License",
    overrides: String? = "",
    texts: String? = "Apache-2.0=licenses/APACHE-2.0.txt",
    textFiles: Map<String, String> = mapOf("licenses/APACHE-2.0.txt" to "Apache License text"),
): Map<String, String> =
    listOfNotNull(
        whitelist?.let { "licenses-whitelist.txt" to it },
        hidden?.let { "licenses-hidden.txt" to it },
        merges?.let { "merges.txt" to it },
        overrides?.let { "thirdparty-licenses.properties" to it },
        texts?.let { "licenses.properties" to it },
    ).toMap() + textFiles

fun policy(files: Map<String, String> = registryFiles()): LicensePolicy = LicenseRegistryParser.parse(files, COMMIT)
