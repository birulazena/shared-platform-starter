package com.hz.logistics.parentservice.autoconfigure.tracing

import com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.properties.OtlpCompression
import com.hz.logistics.parentservice.autoconfigure.properties.OtlpProperties
import com.hz.logistics.parentservice.autoconfigure.properties.OtlpProtocol
import com.hz.logistics.parentservice.autoconfigure.properties.PlatformProperties
import com.hz.logistics.parentservice.autoconfigure.support.ControlledOtlpCollector
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

class OtlpConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                PlatformAutoConfiguration::class.java,
                PlatformTracingAutoConfiguration::class.java,
            ),
        )

    @Test
    fun `keeps local tracing eligible when no OTLP endpoint is configured`() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(properties(context).tracing.otlp.endpoint).isNull()
            assertThat(context).doesNotHaveBean("platformOtlpTracingCustomizer")
        }
    }

    @Test
    fun `binds canonical HTTP and gRPC exporter settings without using Boot properties`() {
        contextRunner
            .withPropertyValues(
                "logistics.parent-service.tracing.otlp.endpoint=https://collector.example.test:4318/v1/traces",
                "logistics.parent-service.tracing.otlp.protocol=HTTP_PROTOBUF",
                "logistics.parent-service.tracing.otlp.headers.Authorization=Bearer should-not-appear-in-diagnostics",
                "logistics.parent-service.tracing.otlp.timeout=5s",
                "logistics.parent-service.tracing.otlp.compression=NONE",
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(properties(context).tracing.otlp.protocol).isEqualTo(OtlpProtocol.HTTP_PROTOBUF)
                assertThat(properties(context).tracing.otlp.timeout).isEqualTo(Duration.ofSeconds(5))
                assertThat(properties(context).tracing.otlp.compression).isEqualTo(OtlpCompression.NONE)
                assertThat(properties(context).tracing.otlp.headers).containsKey("Authorization")
                assertThat(context).hasBean("platformOtlpTracingCustomizer")
            }

        contextRunner
            .withPropertyValues(
                "logistics.parent-service.tracing.otlp.endpoint=http://collector.example.test:4317",
                "logistics.parent-service.tracing.otlp.protocol=GRPC",
                "logistics.parent-service.tracing.otlp.compression=GZIP",
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(properties(context).tracing.otlp.protocol).isEqualTo(OtlpProtocol.GRPC)
                assertThat(properties(context).tracing.otlp.compression).isEqualTo(OtlpCompression.GZIP)
                assertThat(context).hasBean("platformOtlpTracingCustomizer")
            }
    }

    @Test
    fun `rejects invalid exporter headers and timeout values`() {
        val headers = OtlpProperties().apply {
            this.headers = mapOf("Authorization" to " ")
        }
        assertThat(headers.areHeadersValid()).isFalse()

        contextRunner
            .withPropertyValues("logistics.parent-service.tracing.otlp.timeout=0s")
            .run { context -> assertThat(context).hasFailed() }
    }

    @Test
    fun `maps sampling probability one to an always-record sampler`() {
        contextRunner
            .withPropertyValues("logistics.parent-service.tracing.sampling-probability=1.0")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(properties(context).tracing.samplingProbability).isEqualByComparingTo("1.0")
                assertThat(context).hasBean("platformTracingSampler")
            }
    }

    @Test
    fun `backs off tracing customization for an application owned OpenTelemetry instance`() {
        contextRunner
            .withUserConfiguration(ApplicationOwnedOpenTelemetry::class.java)
            .withPropertyValues(
                "logistics.parent-service.tracing.otlp.endpoint=http://collector.example.test:4318/v1/traces",
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(OpenTelemetry::class.java)
                assertThat(context).doesNotHaveBean("platformOtlpTracingCustomizer")
            }
    }

    @Test
    fun `builds the configured HTTP and gRPC exporters without performing request-time export`() {
        val httpProperties = PlatformProperties().apply {
            tracing.otlp.endpoint = java.net.URI.create("http://collector.example.test:4318/v1/traces")
            tracing.otlp.protocol = OtlpProtocol.HTTP_PROTOBUF
            tracing.otlp.headers = mapOf("Authorization" to "Bearer test-only")
            tracing.otlp.timeout = Duration.ofSeconds(3)
            tracing.otlp.compression = OtlpCompression.NONE
        }
        val grpcProperties = PlatformProperties().apply {
            tracing.otlp.endpoint = java.net.URI.create("http://collector.example.test:4317")
            tracing.otlp.protocol = OtlpProtocol.GRPC
        }

        assertThat(OtlpTracingCustomizer(httpProperties.tracing).createExporter())
            .isInstanceOf(OtlpHttpSpanExporter::class.java)
        assertThat(OtlpTracingCustomizer(grpcProperties.tracing).createExporter())
            .isInstanceOf(OtlpGrpcSpanExporter::class.java)
    }

    @Test
    fun `exports to a delayed canonical HTTP collector off the calling thread`() {
        ControlledOtlpCollector(responseDelay = Duration.ofMillis(250)).start().use { collector ->
            val properties = PlatformProperties().apply {
                tracing.otlp.endpoint = collector.traceEndpoint
                tracing.otlp.protocol = OtlpProtocol.HTTP_PROTOBUF
                tracing.otlp.headers = mapOf("x-otlp-api-key" to "test-only-secret")
                tracing.otlp.timeout = Duration.ofSeconds(2)
                tracing.otlp.compression = OtlpCompression.GZIP
            }
            val processor = BatchSpanProcessor.builder(OtlpTracingCustomizer(properties.tracing).createExporter())
                .setScheduleDelay(Duration.ofMillis(10))
                .build()
            val tracerProvider = SdkTracerProvider.builder().addSpanProcessor(processor).build()
            try {
                val start = System.nanoTime()
                tracerProvider.get("otlp-configuration-test").spanBuilder("delayed-export").startSpan().end()

                assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(100))
                assertThat(collector.awaitRequest(Duration.ofSeconds(2))).isTrue()
                assertThat(collector.requests.single().headers)
                    .containsEntry("X-otlp-api-key", listOf("test-only-secret"))
            } finally {
                tracerProvider.close()
            }
        }
    }

    private fun properties(context: org.springframework.boot.test.context.assertj.AssertableApplicationContext): PlatformProperties =
        context.getBean(PlatformProperties::class.java)

    @Configuration(proxyBeanMethods = false)
    class ApplicationOwnedOpenTelemetry {

        @Bean
        fun applicationOpenTelemetry(): OpenTelemetry = OpenTelemetry.noop()
    }
}
