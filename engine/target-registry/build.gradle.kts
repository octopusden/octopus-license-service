dependencies {
    implementation(
        "org.octopusden.octopus.infrastructure:components-registry-service-client:" +
            project.property("components-registry-service-client.version"),
    )
    // Feign arrives at runtime with the registry client; compiled against only to tell its failures apart.
    compileOnly("io.github.openfeign:feign-core:${project.property("components-registry-feign.version")}")
    implementation("org.octopusden.octopus.releng:versions-api:${project.property("versions-api.version")}")

    testImplementation(platform("org.junit:junit-bom:${project.property("junit.version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
