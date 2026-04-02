import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    alias(libs.plugins.spring.boot)         apply false
    alias(libs.plugins.spring.dependency.mgmt) apply false
}

// ---------------------------------------------------------------------------
// Capture versions at root scope where the catalog IS available
// ---------------------------------------------------------------------------
val springCloudVersion: String = libs.versions.springCloud.get()
val springBootVersion: String  = libs.versions.springBoot.get()

// ---------------------------------------------------------------------------
// Shared configuration for ALL subprojects
// ---------------------------------------------------------------------------
subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    group   = "com.zte"
    version = "0.1.0-SNAPSHOT"

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
            mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf(
            "--enable-preview",   // Java 21 preview features (virtual threads etc.)
            "-Xlint:all",
            "-Xlint:-preview"
        ))
    }

    tasks.withType<Test>().configureEach {
        jvmArgs("--enable-preview")
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
        }
    }
}

// ---------------------------------------------------------------------------
// Libraries should NOT produce a fat BootJar — plain jar only
// ---------------------------------------------------------------------------
project(":auth-library") {
    tasks.withType<BootJar>().configureEach { enabled = false }
    tasks.withType<Jar>().configureEach { enabled = true }
}
