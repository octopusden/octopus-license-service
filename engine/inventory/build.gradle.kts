plugins {
    `java-test-fixtures`
}

dependencies {
    api(project(":license-target-registry"))
    implementation(
        "org.octopusden.octopus.octopus-external-systems-clients:artifactory-client:" +
            project.property("artifactory-client.version"),
    )
    implementation(
        "org.octopusden.octopus.octopus-external-systems-clients:xray-client:" +
            project.property("xray-client.version"),
    )
    // Feign arrives at runtime with the clients; compiled against only to tell their failures apart.
    compileOnly("io.github.openfeign:feign-core:${project.property("jfrog-feign.version")}")

    testImplementation(platform("org.junit:junit-bom:${project.property("junit.version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // The parity view and diff, shared with the evaluation's parity check (evaluation design, D11).
    // Jackson reads the prototype's JSON; it comes with the Artifactory client, as in parityTest.
    testFixturesImplementation(
        "org.octopusden.octopus.octopus-external-systems-clients:artifactory-client:" +
            project.property("artifactory-client.version"),
    )
}

// The parity check against a prototype run (design, Decision 16): real systems, so never part of `check`.
val parityTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}
configurations[parityTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[parityTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    "parityTestImplementation"(project(":license-target-registry"))
    "parityTestImplementation"(
        "org.octopusden.octopus.octopus-external-systems-clients:artifactory-client:" +
            project.property("artifactory-client.version"),
    )
}

tasks.register<Test>("parityTest") {
    description = "Compares the license inventory with a prototype run; needs the systems named in PrototypeParityTest."
    group = "verification"
    testClassesDirs = parityTest.output.classesDirs
    classpath = parityTest.runtimeClasspath
    useJUnitPlatform()
    outputs.upToDateWhen { false }
}

// Kover would otherwise run every Test task, this one included, to build its coverage report.
extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
    currentProject {
        instrumentation {
            disabledForTestTasks.add("parityTest")
        }
    }
}
