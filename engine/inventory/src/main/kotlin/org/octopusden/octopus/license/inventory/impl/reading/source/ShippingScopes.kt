package org.octopusden.octopus.license.inventory.impl.reading.source

/**
 * Which build-info scopes put a library into what ships. Android has one runtime classpath per
 * release variant and copies libraries into an aar through `…Embed` configurations; Xray cannot
 * read inside either, so those count as shipping too.
 */
internal object ShippingScopes {
    private val RUNTIME = setOf("compile", "runtime", "runtimeClasspath", "embed")
    private val ANDROID_RUNTIME = Regex("Release\\w*RuntimeClasspath$|Embed$")
    private val TEST_OR_DEBUG = Regex("(?i)test|debug")

    fun ships(scope: String) = scope in RUNTIME || (!isTestOrDebug(scope) && ANDROID_RUNTIME.containsMatchIn(scope))

    fun isTestOrDebug(scope: String) = TEST_OR_DEBUG.containsMatchIn(scope)
}
