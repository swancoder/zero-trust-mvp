plugins {
    `java-library`
}

dependencies {
    // Exposed as API so dependents get these transitively
    api(libs.spring.security)
    api(libs.spring.oauth2.resource)

    // X.509 certificate utilities — BouncyCastle
    api(libs.bouncycastle.provider)
    api(libs.bouncycastle.pkix)

    // OBO token creation / validation — JJWT
    api(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // Netty TLS — for ReloadableSslContextFactory (client-side SslContext)
    compileOnly(libs.netty.handler)

    testImplementation(libs.spring.test)
    testImplementation(libs.spring.security.test)
}
