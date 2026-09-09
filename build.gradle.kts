import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    base
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.spring") version "2.3.21" apply false
    kotlin("kapt") version "2.3.21" apply false
    id("org.springframework.boot") version "4.1.0" apply false
}

allprojects {
    group = providers.gradleProperty("projectGroup").get()
    version = providers.gradleProperty("platformVersion").get()
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }

        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
                freeCompilerArgs.add("-Xjsr305=strict")
            }
        }
    }

    plugins.withId("java") {
        extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(21)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

// The root project is an aggregator only. It has no published component or
// implementation dependencies; the three included projects are the platform.
description = "HZ Logistics shared platform build aggregator"

// Keep the root release gate explicit: the aggregator must execute the BOM,
// fast auto-configuration tests, both web-stack suites, and logging tests.
tasks.named("check") {
    dependsOn(subprojects.map { it.tasks.named("check") })
    dependsOn(
        ":logistics-parent-service-autoconfigure:mvcIntegrationTest",
        ":logistics-parent-service-autoconfigure:webfluxIntegrationTest",
        ":logistics-parent-service-autoconfigure:loggingTest",
    )
}
