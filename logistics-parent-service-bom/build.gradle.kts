import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-platform`
    id("java-base")
    id("maven-publish")
}

javaPlatform {
    allowDependencies()
}

publishing {
    publications {
        create<MavenPublication>("mavenBom") {
            from(components["javaPlatform"])
        }
    }
}

val springBootVersion: String by project
val opentelemetryLogbackAppenderVersion: String by project
val kotlinVersion: String by project
val projectGroup: String by project
val platformVersion: String by project

val bomTestCompileClasspath by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}
val bomTestRuntimeClasspath by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(bomTestCompileClasspath)
}
val bomTestCompilerClasspath by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

// `java-platform` does not apply the Java plugin, so its standalone Kotlin
// verification suite must select the platform baseline explicitly.
val javaToolchains = extensions.getByType<JavaToolchainService>()
val java21Launcher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))

    constraints {
        // The imported Boot BOM owns its managed versions. Keeping the
        // supported coordinates here makes them available to consumers and
        // lets every platform module declare them without a version.
        api("$projectGroup:logistics-parent-service-autoconfigure:$platformVersion")
        api("$projectGroup:logistics-parent-service-starter:$platformVersion")

        api("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
        api("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
        api("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")

        api("org.springframework.boot:spring-boot")
        api("org.springframework.boot:spring-boot-autoconfigure")
        api("org.springframework.boot:spring-boot-actuator-autoconfigure")
        api("org.springframework.boot:spring-boot-starter-actuator")
        api("org.springframework.boot:spring-boot-starter-opentelemetry")
        api("org.springframework.boot:spring-boot-starter-security")
        api("org.springframework.boot:spring-boot-starter-test")
        api("org.springframework.boot:spring-boot-starter-validation")
        api("org.springframework.boot:spring-boot-starter-web")
        api("org.springframework.boot:spring-boot-starter-webflux")

        api("org.springframework.security:spring-security-config")
        api("org.springframework.security:spring-security-core")
        api("org.springframework.security:spring-security-oauth2-jose")
        api("org.springframework.security:spring-security-oauth2-resource-server")
        api("org.springframework.security:spring-security-test")
        api("org.springframework.security:spring-security-web")
        api("org.springframework:spring-web")
        api("org.springframework:spring-webflux")
        api("org.springframework:spring-webmvc")
        api("io.projectreactor:reactor-core")
        api("jakarta.servlet:jakarta.servlet-api")
        api("ch.qos.logback:logback-classic")
        api("com.fasterxml.jackson.core:jackson-databind")
        api("org.junit.jupiter:junit-jupiter-api")
        api("org.junit.jupiter:junit-jupiter-engine")
        api("org.junit.platform:junit-platform-launcher")

        // This appender is not managed by Spring Boot and is intentionally
        // pinned to the approved compatibility-tested alpha release.
        api("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:$opentelemetryLogbackAppenderVersion")
    }

    // A java-platform project cannot apply the Java or Kotlin JVM plugin, so
    // its Kotlin JUnit suite is compiled explicitly below.  The test consumes
    // this platform exactly as a downstream application would.
    bomTestCompileClasspath(enforcedPlatform(project(":logistics-parent-service-bom")))
    bomTestCompileClasspath("org.jetbrains.kotlin:kotlin-stdlib")
    bomTestCompileClasspath("org.jetbrains.kotlin:kotlin-test")
    bomTestCompileClasspath("org.junit.jupiter:junit-jupiter-api")
    bomTestCompileClasspath("org.springframework.boot:spring-boot-starter-opentelemetry")
    bomTestCompileClasspath("org.springframework.boot:spring-boot-starter-security")
    bomTestCompileClasspath("ch.qos.logback:logback-classic")
    bomTestCompileClasspath("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0")
    bomTestRuntimeClasspath("org.junit.jupiter:junit-jupiter-engine")
    bomTestRuntimeClasspath("org.junit.platform:junit-platform-launcher")
    bomTestCompilerClasspath(enforcedPlatform(project(":logistics-parent-service-bom")))
    bomTestCompilerClasspath("org.jetbrains.kotlin:kotlin-compiler-embeddable")
}

val bomTestClassesDirectory = layout.buildDirectory.dir("classes/kotlin/bomTest")

val compileBomTestKotlin by tasks.registering(JavaExec::class) {
    description = "Compiles the Kotlin BOM dependency-resolution test suite."
    group = "verification"
    classpath = bomTestCompilerClasspath
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    javaLauncher.set(java21Launcher)

    val sourceFiles = fileTree("src/test/kotlin") { include("**/*.kt") }
    inputs.files(sourceFiles)
    outputs.dir(bomTestClassesDirectory)

    doFirst {
        bomTestClassesDirectory.get().asFile.mkdirs()
        args(
            "-no-stdlib",
            "-no-reflect",
            "-classpath",
            bomTestCompileClasspath.asPath,
            "-jvm-target",
            // The suite verifies the same Java 21 baseline as the published
            // Kotlin implementation; it must not silently compile as Java 17.
            "21",
            "-d",
            bomTestClassesDirectory.get().asFile.absolutePath,
            *sourceFiles.files.map { it.absolutePath }.toTypedArray(),
        )
    }
}

val bomTest by tasks.registering(Test::class) {
    description = "Runs the BOM dependency-resolution test suite."
    group = "verification"
    dependsOn(compileBomTestKotlin)
    javaLauncher.set(java21Launcher)
    testClassesDirs = files(bomTestClassesDirectory)
    classpath = files(bomTestClassesDirectory) + bomTestRuntimeClasspath
    binaryResultsDirectory.set(layout.buildDirectory.dir("test-results/bomTest/binary"))
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/bomTest"))
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/bomTest"))
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(bomTest)
}
