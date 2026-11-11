// Pure: the inventory and the policy are pushed in (design, Decision 1). Their own clients are
// `implementation` there, so none of them reaches this module's sources.
dependencies {
    api(project(":license-inventory"))
    api(project(":license-policy-registry"))

    testImplementation(platform("org.junit:junit-bom:${project.property("junit.version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
