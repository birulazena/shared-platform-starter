package com.hz.logistics.parentservice.autoconfigure.tracing

import com.hz.logistics.parentservice.autoconfigure.properties.OtlpProtocol
import com.hz.logistics.parentservice.autoconfigure.properties.TracingProperties
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpGrpcSpanExporterBuilderCustomizer
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpHttpSpanExporterBuilderCustomizer
import java.util.Locale

/**
 * Adapts the platform's canonical OTLP contract to the exporter builders used
 * by Spring Boot's OpenTelemetry tracing support. Values are applied directly
 * and are intentionally never emitted to diagnostics, particularly headers.
 */
class OtlpTracingCustomizer(
    private val properties: TracingProperties,
) : OtlpHttpSpanExporterBuilderCustomizer, OtlpGrpcSpanExporterBuilderCustomizer {

    /** Build the optional exporter consumed by Boot's asynchronous batch processor. */
    fun createExporter(): SpanExporter = when (properties.otlp.protocol) {
        OtlpProtocol.HTTP_PROTOBUF -> OtlpHttpSpanExporter.builder()
            .also(::customize)
            .build()

        OtlpProtocol.GRPC -> OtlpGrpcSpanExporter.builder()
            .also(::customize)
            .build()
    }

    override fun customize(builder: OtlpHttpSpanExporterBuilder) {
        configure(
            endpoint = { builder.setEndpoint(it) },
            timeout = { builder.setTimeout(it) },
            connectTimeout = { builder.setConnectTimeout(it) },
            compression = { builder.setCompression(it) },
            header = { name, value -> builder.addHeader(name, value) },
        )
    }

    override fun customize(builder: OtlpGrpcSpanExporterBuilder) {
        configure(
            endpoint = { builder.setEndpoint(it) },
            timeout = { builder.setTimeout(it) },
            connectTimeout = { builder.setConnectTimeout(it) },
            compression = { builder.setCompression(it) },
            header = { name, value -> builder.addHeader(name, value) },
        )
    }

    private fun configure(
        endpoint: (String) -> Unit,
        timeout: (java.time.Duration) -> Unit,
        connectTimeout: (java.time.Duration) -> Unit,
        compression: (String) -> Unit,
        header: (String, String) -> Unit,
    ) {
        val otlp = properties.otlp
        endpoint(requireNotNull(otlp.endpoint) { "An OTLP endpoint is required to create an exporter" }.toString())
        timeout(otlp.timeout)
        connectTimeout(otlp.timeout)
        compression(otlp.compression.name.lowercase(Locale.ROOT))
        otlp.headers.forEach(header)
    }
}
