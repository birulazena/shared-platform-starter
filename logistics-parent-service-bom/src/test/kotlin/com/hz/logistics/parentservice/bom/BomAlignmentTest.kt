package com.hz.logistics.parentservice.bom

import ch.qos.logback.classic.LoggerContext
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.tracing.Tracer
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootVersion
import org.springframework.core.SpringVersion
import org.springframework.security.core.SpringSecurityCoreVersion

class BomAlignmentTest {

    /**
     * The test classpath deliberately declares only the Boot OpenTelemetry
     * starter plus the separately managed Logback appender. It therefore
     * verifies the graph a BOM consumer receives rather than reproducing the
     * Micrometer, OpenTelemetry SDK, or OTLP exporter dependencies directly.
     */
    @Test
    fun `resolves the pinned Boot observability graph and approved appender`() {
        assertEquals("4.1.0", SpringBootVersion.getVersion())
        assertEquals("2.3.21", KotlinVersion.CURRENT.toString())
        assertEquals("7.0.8", SpringVersion.getVersion())
        assertEquals("7.1.0", SpringSecurityCoreVersion.getVersion())
        assertEquals("1.17.0", implementationVersion(MeterRegistry::class.java))
        assertEquals("1.7.0", implementationVersion(Tracer::class.java))
        assertEquals("1.62.0", implementationVersion(GlobalOpenTelemetry::class.java))
        assertEquals("1.62.0", implementationVersion(OtlpHttpSpanExporter::class.java))
        assertEquals("1.5.34", implementationVersion(LoggerContext::class.java))
        assertEquals("2.28.0-alpha", implementationVersion(OpenTelemetryAppender::class.java))

        assertNotNull(Class.forName("io.opentelemetry.sdk.OpenTelemetrySdk"))
        assertNotNull(Class.forName("io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter"))
    }

    private fun implementationVersion(type: Class<*>): String =
        requireNotNull(type.`package`.implementationVersion) {
            "Missing implementation version for ${type.name}"
        }
}
