plugins {
    alias(libs.plugins.spring.boot)
}

// ── Integration-test source set ───────────────────────────────────────────────
// src/it/java + src/it/resources run in the `integrationTest` task.
// Inherits all testImplementation/testRuntimeOnly deps from the unit-test scope.
sourceSets {
    create("it") {
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        // Make keycloak/realm-export.json available as a classpath resource
        resources.srcDir(rootDir.resolve("keycloak"))
    }
}

val itImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
@Suppress("UNUSED_VARIABLE")
val itRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    // Internal library
    implementation(project(":auth-library"))

    // Gateway & Security
    implementation(libs.spring.cloud.gateway)
    implementation(libs.spring.oauth2.resource)
    implementation(libs.spring.actuator)

    // Database — JDBC DataSource (Flyway migrations only; not in request path)
    implementation(libs.spring.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)
    runtimeOnly(libs.postgresql.driver)

    // Database — R2DBC (reactive runtime queries: policy engine)
    implementation(libs.spring.r2dbc)
    runtimeOnly(libs.r2dbc.postgresql)

    // mTLS outbound — Netty SslContext (version from Spring Boot BOM)
    implementation(libs.netty.handler)

    // OBO token creation — JJWT (api on compile path; impl+jackson at runtime via auth-library)
    implementation(libs.jjwt.api)

    // Unit test
    testImplementation(libs.spring.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.reactor.test)

    // Integration-test — Testcontainers + WireMock + RestAssured
    itImplementation(libs.testcontainers.junit5)
    itImplementation(libs.testcontainers.postgresql)
    itImplementation(libs.keycloak.testcontainers)
    itImplementation(libs.rest.assured)
    itImplementation(libs.wiremock.standalone)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("gateway-service.jar")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}

// ── integrationTest task ──────────────────────────────────────────────────────
tasks.register<Test>("integrationTest") {
    description = "Runs E2E integration tests (Testcontainers: Postgres + Keycloak; WireMock for downstream)"
    group       = "verification"
    testClassesDirs = sourceSets["it"].output.classesDirs
    classpath       = sourceSets["it"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter("test")

    // Docker Desktop on WSL2 exposes API v1.45+ only on /var/run/docker.sock.
    // docker-java (used by Testcontainers) defaults to API 1.41 → 400 error.
    // Explicitly set the version so Testcontainers can connect.
    environment("DOCKER_HOST",        "unix:///var/run/docker.sock")
    environment("DOCKER_API_VERSION", "1.45")

    // Print test events to console for CI visibility
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
