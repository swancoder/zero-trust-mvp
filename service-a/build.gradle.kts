plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(libs.spring.web)
    implementation(libs.spring.actuator)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("service-a.jar")
}
