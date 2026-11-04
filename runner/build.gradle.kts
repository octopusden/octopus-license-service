plugins {
    id("com.gradleup.shadow")
}

apply(from = rootProject.file("gradle/publishing.gradle.kts"))

val mainClassName = "org.octopusden.octopus.license.runner.LicenseRunnerCommandKt"

// The thin jar stays: the TeamCity release tooling expects <module>-<version>.jar from every module.
tasks.named<Jar>("jar") {
    manifest.attributes("Main-Class" to mainClassName)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    // Clikt's terminal detection loads JNA natives; JDK 24+ warns on every run unless allowed.
    manifest.attributes("Main-Class" to mainClassName, "Enable-Native-Access" to "ALL-UNNAMED")
    mergeServiceFiles()
}

tasks.named("build") {
    dependsOn("shadowJar")
}

val zipMetarunners = tasks.register<Zip>("zipMetarunners") {
    archiveFileName.set("metarunners.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    val tokens = mapOf("group" to project.group.toString(), "name" to project.name, "version" to project.version.toString())
    inputs.properties(tokens)
    from(layout.projectDirectory.dir("metarunners")) {
        expand(tokens)
    }
}

configure<PublishingExtension> {
    publications.named<MavenPublication>("maven") {
        artifact(zipMetarunners) {
            classifier = "metarunners"
            extension = "zip"
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/octopusden/octopus-license-service")
            credentials {
                username = System.getenv("GITHUB_PACKAGES_USERNAME")
                password = System.getenv("GITHUB_PACKAGES_TOKEN")
            }
        }
    }
}

dependencies {
    implementation(project(":client"))
    implementation("com.github.ajalt.clikt:clikt:${project.property("clikt.version")}")
    implementation(platform("tools.jackson:jackson-bom:${project.property("jackson.version")}"))
    implementation("tools.jackson.module:jackson-module-kotlin")
    testImplementation(platform("org.junit:junit-bom:${project.property("junit.version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
