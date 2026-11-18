// Pure: the assessment and the labels are pushed in (design, Decision 1). The evaluation brings the
// inventory's model with it; no client reaches this module's sources.
dependencies {
    api(project(":license-evaluation"))

    testImplementation(platform("org.junit:junit-bom:${project.property("junit.version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
