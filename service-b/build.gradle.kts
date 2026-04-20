plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    // Internal library — provides UserContextTokenService, ReloadableSslContextFactory
    implementation(project(":auth-library"))

    // WebFlux: reactive HTTP server (Netty) — consistent with service-a
    implementation(libs.spring.webflux)
    implementation(libs.spring.actuator)

    // OBO token validation
    implementation(libs.jjwt.api)

    testImplementation(libs.spring.test)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("service-b.jar")
}

// bootRun working directory = project root so that ./certs/ relative path resolves correctly
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}
