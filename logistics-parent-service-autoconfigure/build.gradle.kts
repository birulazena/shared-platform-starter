import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.Test

plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("kapt")
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
    implementation(platform(project(":logistics-parent-service-bom")))
    compileOnly(platform(project(":logistics-parent-service-bom")))
    kapt(platform(project(":logistics-parent-service-bom")))

    implementation("org.springframework.boot:spring-boot")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    // Supplies the shared health/info endpoint matchers without selecting a
    // Servlet or Reactive web implementation.
    implementation("org.springframework.boot:spring-boot-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    // The starter supplies Boot's SDK auto-configuration. These two optional
    // implementation artifacts are required only because this module exposes
    // its canonical W3C/OTLP settings through Boot's supported tracing SPI.
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("org.springframework.security:spring-security-core")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")

    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("org.springframework:spring-webflux")
    // Needed only to type the reactive ErrorWebExceptionHandler SPI. Consumers
    // still choose WebFlux themselves; this adds no transitive web stack.
    compileOnly("org.springframework.boot:spring-boot-webflux")
    compileOnly("org.springframework.security:spring-security-config")
    compileOnly("org.springframework.security:spring-security-web")
    compileOnly("org.springframework.security:spring-security-oauth2-resource-server")
    compileOnly("org.springframework.security:spring-security-oauth2-jose")
    compileOnly("io.projectreactor:reactor-core")
    // The public starter supplies these APIs at runtime. Spring Boot does not
    // ship the OTel Logback appender, so it remains a separate platform input.
    compileOnly("ch.qos.logback:logback-classic")
    compileOnly("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        // Keep the Boot-managed JUnit 6 platform aligned; no current suite
        // uses Mockito's JUnit 5 integration.
        exclude(group = "org.mockito", module = "mockito-junit-jupiter")
    }
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("org.springframework:spring-web")
    testImplementation("org.springframework:spring-webflux")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testRuntimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    kapt("org.springframework.boot:spring-boot-configuration-processor")
}

val customTestSourceSets = listOf(
    "mvcIntegrationTest",
    "webfluxIntegrationTest",
    "loggingTest",
)

val customTestTasks = customTestSourceSets.map { sourceSetName ->
    val sourceSet = sourceSets.create(sourceSetName) {
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
        // Suites may share fixture classes, but must not inherit the unit test
        // runtime: it deliberately contains both web APIs for condition tests.
        compileClasspath += sourceSets["test"].output
        runtimeClasspath += sourceSets["test"].output
    }

    // Logging tests are stack-neutral and intentionally retain the shared
    // test dependencies. MVC and WebFlux adoption tests instead declare only
    // their selected stack below.
    if (sourceSetName == "loggingTest") {
        configurations.named("${sourceSetName}Implementation") {
            extendsFrom(configurations.testImplementation.get())
        }
        configurations.named("${sourceSetName}CompileOnly") {
            extendsFrom(configurations.testCompileOnly.get())
        }
        configurations.named("${sourceSetName}RuntimeOnly") {
            extendsFrom(configurations.testRuntimeOnly.get())
        }
    }

    tasks.register<Test>(sourceSetName) {
        description = "Runs the $sourceSetName test suite."
        group = "verification"
        dependsOn(sourceSet.classesTaskName)
        testClassesDirs = sourceSet.output.classesDirs
        classpath = sourceSet.runtimeClasspath
        useJUnitPlatform()
    }
}

dependencies {
    // Each adoption suite consumes the same public starter a service uses and
    // chooses its own web stack explicitly. The common fast-test classpath
    // intentionally remains broader so T020 can exercise both APIs together.
    add("mvcIntegrationTestImplementation", project(":logistics-parent-service-starter"))
    add("mvcIntegrationTestImplementation", "org.springframework.boot:spring-boot-starter-web")
    // Boot 4 keeps the managed RestClient.Builder auto-configuration in its
    // dedicated module; the MVC tracing contract verifies that managed client.
    add("mvcIntegrationTestImplementation", "org.springframework.boot:spring-boot-restclient")
    add("mvcIntegrationTestImplementation", "org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito", module = "mockito-junit-jupiter")
    }
    add("mvcIntegrationTestImplementation", "org.springframework.boot:spring-boot-webmvc-test")
    add("mvcIntegrationTestImplementation", "org.springframework.security:spring-security-test")
    add("mvcIntegrationTestImplementation", "com.fasterxml.jackson.core:jackson-databind")
    add("mvcIntegrationTestRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    add("webfluxIntegrationTestImplementation", project(":logistics-parent-service-starter"))
    add("webfluxIntegrationTestImplementation", "org.springframework.boot:spring-boot-starter-webflux")
    // Boot 4 exposes the managed WebClient.Builder from this dedicated module;
    // the reactive tracing suite must exercise the managed client path.
    add("webfluxIntegrationTestImplementation", "org.springframework.boot:spring-boot-webclient")
    add("webfluxIntegrationTestImplementation", "org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito", module = "mockito-junit-jupiter")
    }
    add("webfluxIntegrationTestImplementation", "org.springframework.security:spring-security-test")
    add("webfluxIntegrationTestImplementation", "com.fasterxml.jackson.core:jackson-databind")
    add("webfluxIntegrationTestRuntimeOnly", "org.junit.platform:junit-platform-launcher")

    testImplementation("org.springframework:spring-webmvc")
    testRuntimeOnly("jakarta.servlet:jakarta.servlet-api")
}

// A green module check includes every custom suite; none may silently become
// an orphaned source set as the platform grows.
tasks.named("check") {
    dependsOn(customTestTasks)
}
