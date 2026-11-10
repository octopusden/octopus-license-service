repositories {
    // The two JFrog clients are published to the local repository until their release (tasks.md 1.4).
    mavenLocal()
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
}
