plugins {
    alias(libs.plugins.spring.boot)
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

    // Test
    testImplementation(libs.spring.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.reactor.test)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("gateway-service.jar")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}
