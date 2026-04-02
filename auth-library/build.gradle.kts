plugins {
    `java-library`
}

dependencies {
    // Exposed as API so dependents get these transitively
    api(libs.spring.security)
    api(libs.spring.oauth2.resource)

    testImplementation(libs.spring.test)
    testImplementation(libs.spring.security.test)
}
