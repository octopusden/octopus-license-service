package org.octopusden.octopus.license.inventory.model

/**
 * [violation] marks the codes the prototype reported as violations rather than notes; grading
 * reads the code either way.
 */
enum class FindingCode(
    val violation: Boolean,
) {
    NOT_SCANNABLE(false),
    NO_PRODUCING_MODULE(false),
    LOCAL_FILE_DEPENDENCY(false),
    UNRESOLVED_INTERNAL_ARCHIVE(false),
    NO_LIBRARIES_IN_FILE(false),
    BUILD_NAME_DIFFERS(false),
    ARTIFACT_NOT_FOUND(true),
    UNSUPPORTED_DISTRIBUTION_ENTRY(true),
    NOT_IN_XRAY(true),
    NO_BUILD_INFO(true),
    NO_DISTRIBUTION(true),
}
