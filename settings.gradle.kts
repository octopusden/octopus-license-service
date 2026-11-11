pluginManagement {
    plugins {
        kotlin("jvm") version (extra["kotlin.version"] as String)
        kotlin("plugin.spring") version (extra["kotlin.version"] as String)
        id("org.springframework.boot") version (extra["spring-boot.version"] as String)
        id("com.bmuschko.docker-spring-boot-application") version (extra["docker-plugin.version"] as String)
        id("com.gradleup.shadow") version (extra["shadow.version"] as String)
        id("io.github.gradle-nexus.publish-plugin") version (extra["nexus-publish.version"] as String)
        id("org.octopusden.octopus-quality") version (extra["octopus-quality.version"] as String)
        id("dev.detekt") version (extra["detekt.version"] as String)
        id("org.jlleitschuh.gradle.ktlint") version (extra["ktlint-gradle.version"] as String)
        id("org.jetbrains.kotlinx.kover") version (extra["kover.version"] as String)
        id("org.sonarqube") version (extra["sonarqube.version"] as String)
        id("org.gradle.toolchains.foojay-resolver-convention") version (extra["foojay-resolver.version"] as String)
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "octopus-license-service"

include(":core")

include(":client")

include(":license-service")
project(":license-service").projectDir = file("server")

include(":license-runner")
project(":license-runner").projectDir = file("runner")

include(":license-target-registry")
project(":license-target-registry").projectDir = file("engine/target-registry")

include(":license-policy-registry")
project(":license-policy-registry").projectDir = file("engine/policy-registry")

include(":license-inventory")
project(":license-inventory").projectDir = file("engine/inventory")

include(":license-evaluation")
project(":license-evaluation").projectDir = file("engine/evaluation")
