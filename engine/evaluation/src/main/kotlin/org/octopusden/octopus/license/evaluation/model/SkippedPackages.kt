package org.octopusden.octopus.license.evaluation.model

/**
 * Packages found but not judged: they come with the operating system of an image, not with our
 * software (D3). Image layers are not packages and are counted nowhere.
 *
 * @param osPackages `rpm`, `deb` and `alpine` packages, wherever found.
 * @param baseImagePython `pypi` packages found only in images.
 * @param perFile released file entry to the number of both kinds found in it; a file with none has
 * no entry (D8).
 */
data class SkippedPackages(
    val osPackages: Int,
    val baseImagePython: Int,
    val perFile: Map<String, Int>,
)
