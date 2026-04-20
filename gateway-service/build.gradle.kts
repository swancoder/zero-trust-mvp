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

    // Test
    testImplementation(libs.spring.test)
    testImplementation(libs.spring.security.test)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("gateway-service.jar")
}
