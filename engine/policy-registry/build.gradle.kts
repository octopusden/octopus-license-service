dependencies {
    implementation(
        "org.octopusden.octopus.octopus-external-systems-clients:bitbucket-client:" +
            project.property("bitbucket-client.version"),
    )
    // Feign arrives at runtime with the Bitbucket client; compiled against only to tell its failures apart.
    compileOnly("io.github.openfeign:feign-core:${project.property("bitbucket-feign.version")}")
    implementation("org.octopusden.octopus.releng:versions-api:${project.property("versions-api.version")}")

    testImplementation(platform("org.junit:junit-bom:${project.property("junit.version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
