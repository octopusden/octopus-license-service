apply(from = rootProject.file("gradle/publishing.gradle.kts"))

dependencies {
    api(project(":core"))
    implementation(platform("tools.jackson:jackson-bom:${project.property("jackson.version")}"))
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("io.github.openfeign:feign-core:${project.property("openfeign.version")}")
    implementation("io.github.openfeign:feign-jackson3:${project.property("openfeign.version")}")
    testImplementation(platform("org.junit:junit-bom:${project.property("junit.version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
