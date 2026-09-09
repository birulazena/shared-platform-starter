package com.hz.logistics.parentservice.autoconfigure.tracing

import com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import com.hz.logistics.parentservice.autoconfigure.properties.PlatformProperties
import io.micrometer.tracing.otel.bridge.Slf4JEventListener
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SdkTracerProviderBuilderCustomizer
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpGrpcSpanExporterBuilderCustomizer
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpHttpSpanExporterBuilderCustomizer
import org.springframework.context.annotation.Bean

/**
 * Platform tracing contribution built on Spring Boot's OpenTelemetry starter.
 * It supplies only portable defaults: W3C propagation, sampling, optional OTLP
 * export, MDC correlation, and Reactor context propagation. An application-owned
 * OpenTelemetry SDK remains authoritative and disables this contribution only.
 */
@AutoConfiguration
@AutoConfigureAfter(PlatformAutoConfiguration::class)
@AutoConfigureBefore(
    name = [
        "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration",
        "org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration",
    ],
)
@ConditionalOnClass(
    value = [
        OpenTelemetry::class,
        ContextPropagators::class,
        Sampler::class,
    ],
)
@ConditionalOnProperty(
    prefix = "logistics.parent-service.tracing",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@ConditionalOnMissingBean(
    value = [
        OpenTelemetry::class,
        SdkTracerProvider::class,
        ContextPropagators::class,
        SdkTracerProviderBuilderCustomizer::class,
        OtlpHttpSpanExporterBuilderCustomizer::class,
        OtlpGrpcSpanExporterBuilderCustomizer::class,
    ],
)
class PlatformTracingAutoConfiguration {

    /** Parent-based ratio sampling preserves a remote parent decision. */
    @Bean
    @ConditionalOnMissingBean(Sampler::class)
    fun platformTracingSampler(properties: PlatformProperties): Sampler =
        Sampler.parentBased(Sampler.traceIdRatioBased(properties.tracing.samplingProbability.toDouble()))

    @Bean
    @ConditionalOnMissingBean(ContextPropagators::class)
    fun platformW3cContextPropagators(): ContextPropagators =
        W3cPropagationConfigurer().contextPropagators()

    @Bean
    @ConditionalOnMissingBean(Slf4JEventListener::class)
    fun platformTraceCorrelationConfigurer(
        correlationContext: PlatformCorrelationContext,
    ): TraceCorrelationConfigurer = TraceCorrelationConfigurer(correlationContext)

    @Bean
    @ConditionalOnClass(name = ["reactor.core.publisher.Hooks"])
    fun platformReactiveTraceContextBridge(): ReactiveTraceContextBridge = ReactiveTraceContextBridge()

    /** An exporter exists only when the canonical endpoint selects one. */
    @Bean
    @ConditionalOnProperty(
        prefix = "logistics.parent-service.tracing",
        name = ["otlp.endpoint"],
    )
    fun platformOtlpTracingCustomizer(properties: PlatformProperties): OtlpTracingCustomizer =
        OtlpTracingCustomizer(properties.tracing)

    /**
     * Supplies a bounded, asynchronous processor for the canonical exporter.
     * Its short internal interval keeps export out of request threads while
     * allowing a healthy collector to receive traces promptly. An application
     * exporter or processor remains authoritative.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "logistics.parent-service.tracing",
        name = ["otlp.endpoint"],
    )
    @ConditionalOnMissingBean(value = [SpanExporter::class, BatchSpanProcessor::class])
    fun platformOtlpSpanProcessor(
        properties: PlatformProperties,
        customizer: OtlpTracingCustomizer,
    ): BatchSpanProcessor = BatchSpanProcessor
        .builder(customizer.createExporter())
        .setExporterTimeout(properties.tracing.otlp.timeout)
        .setScheduleDelay(PLATFORM_EXPORT_SCHEDULE_DELAY)
        .build()

    private companion object {
        val PLATFORM_EXPORT_SCHEDULE_DELAY: java.time.Duration = java.time.Duration.ofMillis(100)
    }
}
