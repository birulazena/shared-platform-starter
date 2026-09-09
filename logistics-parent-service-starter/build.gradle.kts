import org.gradle.api.GradleException
import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-library`
    kotlin("jvm")
    id("maven-publish")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

dependencies {
    api(platform(project(":logistics-parent-service-bom")))
    api(project(":logistics-parent-service-autoconfigure"))

    api("org.springframework.boot:spring-boot-starter-actuator")
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-opentelemetry")
    api("org.springframework.security:spring-security-oauth2-resource-server")
    api("org.springframework.security:spring-security-oauth2-jose")
    api("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        // Mockito 5.20 imports the JUnit 5.13 BOM and conflicts with the
        // JUnit 6 line managed by Spring Boot 4.1.0. These contract tests do
        // not use Mockito.
        exclude(group = "org.mockito", module = "mockito-junit-jupiter")
    }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val verifyThinStarter by tasks.registering {
    group = "verification"
    description = "Verifies that the starter remains source-free and web-stack neutral."

    val implementationSources = fileTree("src/main") {
        include("**/*.java", "**/*.kt")
    }
    inputs.files(implementationSources)

    doLast {
        check(implementationSources.files.isEmpty()) {
            "The thin starter must not contain implementation sources: " +
                implementationSources.files.joinToString()
        }

        val forbiddenModules = setOf(
            "org.springframework.boot:spring-boot-starter-web",
            "org.springframework.boot:spring-boot-starter-webflux",
            "org.springframework:spring-webmvc",
            "org.springframework:spring-webflux",
        )
        val resolvedModules = configurations.runtimeClasspath.get()
            .resolvedConfiguration
            .resolvedArtifacts
            .map { artifact -> "${artifact.moduleVersion.id.group}:${artifact.name}" }
            .toSet()
        val selectedWebModules = resolvedModules.intersect(forbiddenModules)

        if (selectedWebModules.isNotEmpty()) {
            throw GradleException(
                "The thin starter must not select a web stack: ${selectedWebModules.joinToString()}",
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyThinStarter)
}
