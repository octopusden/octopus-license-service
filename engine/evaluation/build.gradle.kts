// Pure: the inventory and the policy are pushed in (design, Decision 1). Their own clients are
// `implementation` there, so none of them reaches this module's sources.
dependencies {
    api(project(":license-inventory"))
    api(project(":license-policy-registry"))

    testImplementation(platform("org.junit:junit-bom:${project.property("junit.version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // The inventory's parity view, and Jackson to read the prototype's JSON (design, Decision 11).
    testImplementation(testFixtures(project(":license-inventory")))
    testImplementation(
        "org.octopusden.octopus.octopus-external-systems-clients:artifactory-client:" +
            project.property("artifactory-client.version"),
    )
}

// The end-to-end parity check against a prototype run (design, Decision 11): real systems, so never part of `check`.
val parityTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}
configurations[parityTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[parityTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    "parityTestImplementation"(project(":license-target-registry"))
}

tasks.register<Test>("parityTest") {
    description = "Compares the inventory and its evaluation with a prototype run; needs the systems named in PrototypeParityTest."
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
