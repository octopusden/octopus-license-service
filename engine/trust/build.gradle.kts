// Pure: the assessment and the labels are pushed in (design, Decision 1). The evaluation brings the
// inventory's model with it; no client reaches this module's sources.
dependencies {
    api(project(":license-evaluation"))

    testImplementation(platform("org.junit:junit-bom:${project.property("junit.version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // The trust parity view reads the prototype's JSON with Jackson, which comes with the Artifactory
    // client, as in the evaluation's (design, Decision 10).
    testImplementation(
        "org.octopusden.octopus.octopus-external-systems-clients:artifactory-client:" +
            project.property("artifactory-client.version"),
    )
}

// The end-to-end parity check against a prototype run (design, Decision 10): real systems, so never part of `check`.
val parityTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}
configurations[parityTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[parityTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    "parityTestImplementation"(project(":license-target-registry"))
    "parityTestImplementation"(testFixtures(project(":license-inventory")))
    "parityTestImplementation"(testFixtures(project(":license-evaluation")))
}

tasks.register<Test>("parityTest") {
    description =
        "Compares the inventory, its evaluation and its trust with a prototype run; needs the systems named in PrototypeParityTest."
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
