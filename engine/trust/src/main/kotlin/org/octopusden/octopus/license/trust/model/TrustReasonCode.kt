package org.octopusden.octopus.license.trust.model

/**
 * Why a list may be incomplete, with what it means and how to fix it: the prototype's reason table,
 * worded for this service (D2). Raised for the component, for a released file, or both; the level and
 * texts are the same wherever.
 */
enum class TrustReasonCode(
    val level: TrustLevel,
    val title: String,
    val meaning: String,
    val fix: String,
) {
    NO_ARTIFACT_IN_REGISTRY(
        TrustLevel.NONE,
        "Nothing found to check",
        "There is no build named after the component for this version ({name}_ee/_ie, {name}_ee_docker), and the " +
            "registry lists no Artifactory file or docker image, or the build publishes nothing that matches the " +
            "registry's GAV or maven-artifacts pattern.",
        "Name the build after the component, or register the released artifacts (GAV, docker image or " +
            "maven-artifacts pattern) in the components registry.",
    ),
    ARTIFACT_NOT_FOUND(
        TrustLevel.NONE,
        "Registered file doesn't exist",
        "The registry names a file, but Artifactory has no such file for this version. The coordinates are " +
            "outdated or wrong.",
        "Correct the GAV in the components registry.",
    ),
    NOT_SCANNABLE(
        TrustLevel.NONE,
        "File can't be checked",
        "The file exists, but Xray can't look inside it (e.g. .wim, ansible .tar.gz, .pom), or it was uploaded " +
            "without a build record.",
        "Publish an SBOM for it, or check it by hand.",
    ),
    PARTIAL_ARTIFACTS(
        TrustLevel.LOW,
        "Only some files checked",
        "The component releases several files and only some could be checked, so the list covers only part of " +
            "what we ship.",
        "Fix the files that failed (see the files' trust).",
    ),
    NO_THIRD_PARTY_FOUND(
        TrustLevel.LOW,
        "Nothing found inside",
        "We opened the files and found no outside libraries. For C/C++ programs this almost always means the " +
            "libraries are compiled in and invisible, not that there are none.",
        "Publish an SBOM from the build, or confirm by hand that it really uses no outside code.",
    ),
    ECOSYSTEM_NOT_DETECTED(
        TrustLevel.LOW,
        "Expected libraries are missing",
        "The registry says the component contains a language (JavaScript, Python, Go, Dart, Swift, C/C++, C#), " +
            "but no library of that kind was found, by Xray or in an SBOM. They are usually bundled or compiled " +
            "in, which hides them. Sometimes the registry label is simply inaccurate.",
        "Publish an SBOM for that part, or correct the registry label.",
    ),
    FULL_LIST_UNAVAILABLE(
        TrustLevel.LOW,
        "Full library list not available",
        "Some libraries are of a kind our sources can't list completely, such as Dart, iOS or C/C++ code " +
            "compiled into an app. Without an SBOM we only see the few that Xray still recognises, so the list " +
            "is probably incomplete.",
        "Publish an SBOM from the build (cdxgen reads pubspec.lock, Podfile.lock and the like).",
    ),
    UNIDENTIFIED_BINARIES(
        TrustLevel.MEDIUM,
        "Some files couldn't be identified",
        "At least 10% of the outside files were recognised only by checksum (renamed, repackaged or very new), " +
            "so their license is a guess from text found inside them.",
        "Review those files and add registry overrides for the ones that matter.",
    ),
    UNKNOWN_LICENSES(
        TrustLevel.MEDIUM,
        "Licenses unknown",
        "We know which libraries are there, but Xray has no license for at least 10% of them. This is common for " +
            "iOS and Dart libraries.",
        "Add the licenses through registry overrides, or publish an SBOM that carries them.",
    ),
    NO_BUILD_RECORD(
        TrustLevel.MEDIUM,
        "No build record",
        "Xray scanned the file, but no build was found for it. Libraries the build would have recorded, such as " +
            "those merged into a fat jar, bundled JavaScript or Android packages, can't be added.",
        "Publish build-info for the build that produces this file, or set its build.name and build.number " +
            "properties.",
    ),

    /** The service's own: the prototype skipped an SBOM it could not read without a word (D2). */
    SBOM_UNREADABLE(
        TrustLevel.LOW,
        "Part of the build's library list couldn't be read",
        "The build published an SBOM that is missing from Artifactory or is not valid CycloneDX, so the libraries it lists " +
            "are not in this report.",
        "Republish a valid CycloneDX SBOM from the build.",
    ),
    BUILD_LIST_ONLY(
        TrustLevel.LOW,
        "Only the build's list used",
        "Xray hasn't indexed the file, so its libraries come only from the build's dependency list. Anything the " +
            "build didn't record, such as copied-in files, is missed.",
        "Add the repository to Xray's indexed resources, or reindex the file.",
    ),
}
