// Pure: the assessment, the policy and the downloaded texts are pushed in (design, Decision 1).
dependencies {
    api(project(":license-evaluation"))

    testImplementation(platform("org.junit:junit-bom:${project.property("junit.version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
