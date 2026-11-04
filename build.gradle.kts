import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.InetAddress
import java.time.Duration
import java.util.zip.CRC32

plugins {
    kotlin("jvm") apply false
    kotlin("plugin.spring") apply false
    id("org.springframework.boot") apply false
    id("com.bmuschko.docker-spring-boot-application") apply false
    id("com.gradleup.shadow") apply false
    id("dev.detekt") apply false
    id("org.jlleitschuh.gradle.ktlint") apply false
    id("org.jetbrains.kotlinx.kover") apply false
    id("io.github.gradle-nexus.publish-plugin")
    id("org.octopusden.octopus-quality")
    id("org.sonarqube")
}

octopusQuality {
    publication {
        enforceCentralPublications.set(true)
        centralPublications.set(
            setOf(
                ":client|maven|org.octopusden.octopus.license:client|[jar, jar:javadoc, jar:sources]",
                ":core|maven|org.octopusden.octopus.license:core|[jar, jar:javadoc, jar:sources]",
                // Routed to GitHub Packages by release.yml (github-packages-publications), never to
                // Central: the publication guard refuses the -all jar there.
                ":license-runner|maven|org.octopusden.octopus.license:license-runner|" +
                    "[jar, jar:all, jar:javadoc, jar:sources, zip:metarunners]",
            ),
        )
    }
    kotlin {
        failOnViolation.set(true)
    }
    // Data classes only: no behaviour of its own to cover. The server and client tests exercise it.
    excludeProjects("core")
}

val defaultVersion = "${
    with(CRC32()) {
        update(InetAddress.getLocalHost().hostName.toByteArray())
        value
    }
}-SNAPSHOT"

allprojects {
    group = "org.octopusden.octopus.license"
    if (version == "unspecified") {
        version = defaultVersion
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(System.getenv("MAVEN_USERNAME"))
            password.set(System.getenv("MAVEN_PASSWORD"))
        }
    }
    transitionCheckOptions {
        maxRetries.set(60)
        delayBetween.set(Duration.ofSeconds(30))
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    // detekt and ktlint are applied per subproject so that qualityStatic finds a `detekt` and a
    // `ktlintCheck` task in every Kotlin module (octopus-quality's hollow-gate guard).
    apply(plugin = "dev.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        withJavadocJar()
        withSourcesJar()
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(25)
        compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
    }

    // octopus-quality configures only the io.gitlab.arturbosch.detekt plugin, and detekt 1.x
    // cannot run on JDK 25, so detekt 2 is configured here: the default rule set, no baseline.
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        buildUponDefaultConfig.set(true)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    extra["signingRequired"] = System.getenv().let {
        it.containsKey("ORG_GRADLE_PROJECT_signingKey") && it.containsKey("ORG_GRADLE_PROJECT_signingPassword")
    }
}
