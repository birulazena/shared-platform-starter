package com.hz.logistics.parentservice.autoconfigure.logging

import com.fasterxml.jackson.databind.ObjectMapper
import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Bean
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest(
    classes = [LoggingRuntimeFixtureApplication::class],
    webEnvironment = WebEnvironment.NONE,
    properties = [
        "logistics.parent-service.security.enabled=false",
        "logistics.parent-service.logging.redaction-mask=[MASKED]",
        "management.otlp.metrics.export.enabled=false",
    ],
)
@ExtendWith(OutputCaptureExtension::class)
class LoggingRuntimeCompatibilityTest {

    @Autowired
    private lateinit var openTelemetry: OpenTelemetry

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun clearCapturedRecords() {
        LoggingRuntimeProbe.exporter.clear()
    }

    @Test
    fun writesOneSanitizedJsonEventWithCorrelationToConsoleAndOpenTelemetry(output: CapturedOutput) {
        val canary = "runtime-password-canary"
        val span = openTelemetry.getTracer("logging-runtime-test").spanBuilder("logging-runtime-span").startSpan()

        span.makeCurrent().use {
            MDC.put("customerEmail", "runtime.customer@example.test")
            LoggerFactory.getLogger("runtime.compatibility")
                .info("runtime-json-canary password={} Authorization: Bearer runtime-bearer-canary", canary)
            MDC.clear()
        }
        span.end()

        val consoleLine = output.out.lines()
            .lastOrNull { it.contains("runtime-json-canary") }
        assertThat(consoleLine).isNotNull

        val consoleEvent = objectMapper.readTree(consoleLine)
        assertThat(consoleEvent.path("@timestamp").asText()).isNotBlank()
        assertThat(consoleEvent.path("level").asText()).isEqualTo("INFO")
        assertThat(consoleEvent.path("logger_name").asText()).isEqualTo("runtime.compatibility")
        assertThat(consoleEvent.path("thread_name").asText()).isNotBlank()
        assertThat(consoleEvent.path("traceId").asText()).isEqualTo(span.spanContext.traceId)
        assertThat(consoleEvent.path("spanId").asText()).isEqualTo(span.spanContext.spanId)

        val consoleBytes = consoleLine.orEmpty()
        val otelRecords = LoggingRuntimeProbe.exporter.records
        assertThat(otelRecords).isNotEmpty
        val otelBytes = otelRecords.joinToString(" ") { record ->
            record.body.asString() + " " + record.attributes.asMap().toString()
        }
        listOf(canary, "runtime-bearer-canary", "runtime.customer@example.test").forEach { rawCanary ->
            assertThat(consoleBytes).doesNotContain(rawCanary)
            assertThat(otelBytes).doesNotContain(rawCanary)
        }
        assertThat(consoleBytes).contains("[MASKED]")
        assertThat(otelBytes).contains("[MASKED]")
        assertThat(otelRecords.last().spanContext.traceId).isEqualTo(span.spanContext.traceId)
    }
}

@SpringBootTest(
    classes = [UnavailableOtelLoggingFixtureApplication::class],
    webEnvironment = WebEnvironment.NONE,
    properties = [
        "logistics.parent-service.security.enabled=false",
        "logistics.parent-service.logging.redaction-mask=[MASKED]",
        "management.otlp.metrics.export.enabled=false",
    ],
)
@ExtendWith(OutputCaptureExtension::class)
class UnavailableOtelLoggingCompatibilityTest {

    @Autowired
    private lateinit var correlationContext: PlatformCorrelationContext

    @Test
    fun unavailableOpenTelemetryLoggingDoesNotPreventSanitizedConsoleOutput(output: CapturedOutput) {
        LoggerFactory.getLogger("runtime.unavailable")
            .info("unavailable-otel-canary secret={}", "unavailable-secret-canary")

        val consoleLine = output.out.lines()
            .lastOrNull { it.contains("unavailable-otel-canary") }
        assertThat(consoleLine).isNotNull
        assertThat(ObjectMapper().readTree(consoleLine).path("message").asText())
            .doesNotContain("unavailable-secret-canary")
            .contains("[MASKED]")
    }

    @Test
    fun scopedFallbackCorrelationReachesThePreSinkBoundaryAndDoesNotLeak(output: CapturedOutput) {
        val fallbackTraceId = correlationContext.withExecutionScope {
            LoggerFactory.getLogger("runtime.fallback")
                .info("fallback-correlation-canary")
            correlationContext.requiredTraceId()
        }
        LoggerFactory.getLogger("runtime.fallback").info("post-fallback-correlation-canary")

        val fallbackLine = output.out.lines().last { it.contains("\"message\":\"fallback-correlation-canary\"") }
        val postScopeLine = output.out.lines().last { it.contains("post-fallback-correlation-canary") }
        assertThat(ObjectMapper().readTree(fallbackLine).path("traceId").asText()).isEqualTo(fallbackTraceId)
        assertThat(ObjectMapper().readTree(postScopeLine).path("traceId").isMissingNode).isTrue()
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
class LoggingRuntimeFixtureApplication {

    @Bean
    fun applicationOpenTelemetry(): OpenTelemetry = LoggingRuntimeProbe.openTelemetry(LoggingRuntimeProbe.exporter)
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
class UnavailableOtelLoggingFixtureApplication {

    @Bean
    fun unavailableOpenTelemetry(): OpenTelemetry = LoggingRuntimeProbe.openTelemetry(FailingLogRecordExporter())
}

private object LoggingRuntimeProbe {

    val exporter = RecordingLogRecordExporter()

    fun openTelemetry(exporter: LogRecordExporter): OpenTelemetry {
        val loggerProvider = SdkLoggerProvider.builder()
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
            .build()
        return OpenTelemetrySdk.builder().setLoggerProvider(loggerProvider).build()
    }
}

private class RecordingLogRecordExporter : LogRecordExporter {

    private val collected = CopyOnWriteArrayList<LogRecordData>()

    val records: List<LogRecordData>
        get() = collected.toList()

    override fun export(logs: Collection<LogRecordData>): CompletableResultCode {
        collected.addAll(logs)
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()

    fun clear() {
        collected.clear()
    }
}

private class FailingLogRecordExporter : LogRecordExporter {

    override fun export(logs: Collection<LogRecordData>): CompletableResultCode =
        CompletableResultCode.ofFailure()

    override fun flush(): CompletableResultCode = CompletableResultCode.ofFailure()

    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofFailure()
}
