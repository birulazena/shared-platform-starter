package com.hz.logistics.parentservice.starter

import com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StarterDependencyContractTest {

    @Test
    fun `exposes auto-configuration and non-web prerequisites without choosing a web stack`() {
        assertThat(PlatformAutoConfiguration::class.java).isNotNull()

        assertThat(classIsPresent("org.springframework.boot.autoconfigure.AutoConfiguration")).isTrue()
        assertThat(classIsPresent("org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider"))
            .isTrue()
        assertThat(classIsPresent("io.micrometer.core.instrument.MeterRegistry")).isTrue()
        assertThat(classIsPresent("io.opentelemetry.api.OpenTelemetry")).isTrue()
        assertThat(classIsPresent("io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter")).isTrue()
        assertThat(classIsPresent("io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"))
            .isTrue()

        assertThat(classIsPresent("org.springframework.web.servlet.DispatcherServlet")).isFalse()
        assertThat(classIsPresent("org.springframework.web.reactive.DispatcherHandler")).isFalse()
    }

    private fun classIsPresent(name: String): Boolean =
        runCatching { Class.forName(name, false, javaClass.classLoader) }.isSuccess
}
