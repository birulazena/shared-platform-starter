package com.hz.logistics.parentservice.autoconfigure.tracing

import com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import io.micrometer.tracing.Span as MicrometerSpan
import io.micrometer.tracing.TraceContext
import io.micrometer.tracing.Tracer
import io.opentelemetry.api.trace.Span as OpenTelemetrySpan
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.lang.reflect.Proxy

class W3cPropagationTest {

    private val propagator = W3CTraceContextPropagator.getInstance()

    @Test
    fun `continues a valid remote traceparent as a remote parent`() {
        val extracted = propagator.extract(
            Context.root(),
            mapOf("traceparent" to VALID_TRACE_PARENT),
            mapGetter,
        )

        val spanContext = OpenTelemetrySpan.fromContext(extracted).spanContext

        assertThat(spanContext.isValid).isTrue()
        assertThat(spanContext.isRemote).isTrue()
        assertThat(spanContext.traceId).isEqualTo(TRACE_ID)
        assertThat(spanContext.spanId).isEqualTo(PARENT_SPAN_ID)
        assertThat(spanContext.traceFlags.isSampled).isTrue()
    }

    @Test
    fun `ignores missing and malformed headers then injects a fresh valid context`() {
        listOf(
            emptyMap(),
            mapOf("traceparent" to "00-00000000000000000000000000000000-$PARENT_SPAN_ID-01"),
            mapOf("traceparent" to "this-is-not-a-traceparent"),
        ).forEach { carrier ->
            val extracted = propagator.extract(Context.root(), carrier, mapGetter)
            assertThat(OpenTelemetrySpan.fromContext(extracted).spanContext.isValid).isFalse()
        }

        val freshContext = SpanContext.create(
            FRESH_TRACE_ID,
            FRESH_SPAN_ID,
            TraceFlags.getSampled(),
            TraceState.getDefault(),
        )
        val outboundHeaders = linkedMapOf<String, String>()
        propagator.inject(
            Context.root().with(OpenTelemetrySpan.wrap(freshContext)),
            outboundHeaders,
            mapSetter,
        )

        assertThat(outboundHeaders["traceparent"])
            .isEqualTo("00-$FRESH_TRACE_ID-$FRESH_SPAN_ID-01")
    }

    @Test
    fun `preserves valid tracestate during W3C extraction`() {
        val extracted = propagator.extract(
            Context.root(),
            mapOf(
                "traceparent" to VALID_TRACE_PARENT,
                "tracestate" to "hz=priority,tenant=west",
            ),
            mapGetter,
        )

        val traceState = OpenTelemetrySpan.fromContext(extracted).spanContext.traceState

        assertThat(traceState.get("hz")).isEqualTo("priority")
        assertThat(traceState.get("tenant")).isEqualTo("west")
    }

    @Test
    fun `accepts inclusive sampling bounds`() {
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PlatformAutoConfiguration::class.java,
                    PlatformTracingAutoConfiguration::class.java,
                ),
            )
            .withPropertyValues("logistics.parent-service.tracing.sampling-probability=0.0")
            .run { context -> assertThat(context).hasNotFailed() }

        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PlatformAutoConfiguration::class.java,
                    PlatformTracingAutoConfiguration::class.java,
                ),
            )
            .withPropertyValues("logistics.parent-service.tracing.sampling-probability=1.0")
            .run { context -> assertThat(context).hasNotFailed() }
    }

    @Test
    fun `exposes the same current trace and span identifiers to correlation consumers`() {
        val correlation = PlatformCorrelationContext(tracerWith(TRACE_ID, FRESH_SPAN_ID)).current()

        assertThat(correlation).isEqualTo(PlatformCorrelationContext.Correlation(TRACE_ID, FRESH_SPAN_ID))
    }

    private fun tracerWith(traceId: String, spanId: String): Tracer {
        val traceContext = Proxy.newProxyInstance(
            TraceContext::class.java.classLoader,
            arrayOf(TraceContext::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "traceId" -> traceId
                "spanId" -> spanId
                "parentId" -> null
                "sampled" -> true
                else -> null
            }
        } as TraceContext
        val span = Proxy.newProxyInstance(
            MicrometerSpan::class.java.classLoader,
            arrayOf(MicrometerSpan::class.java),
        ) { _, method, _ ->
            if (method.name == "context") traceContext else null
        } as MicrometerSpan
        return Proxy.newProxyInstance(
            Tracer::class.java.classLoader,
            arrayOf(Tracer::class.java),
        ) { _, method, _ ->
            if (method.name == "currentSpan") span else null
        } as Tracer
    }

    private companion object {
        const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
        const val PARENT_SPAN_ID = "00f067aa0ba902b7"
        const val FRESH_TRACE_ID = "a3ce929d0e0e47364bf92f3577b34da6"
        const val FRESH_SPAN_ID = "0af7651916cd43dd"
        const val VALID_TRACE_PARENT = "00-$TRACE_ID-$PARENT_SPAN_ID-01"

        val mapGetter = object : TextMapGetter<Map<String, String>> {
            override fun keys(carrier: Map<String, String>): Iterable<String> = carrier.keys

            override fun get(carrier: Map<String, String>?, key: String): String? =
                carrier?.entries?.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
        }

        val mapSetter = object : TextMapSetter<MutableMap<String, String>> {
            override fun set(carrier: MutableMap<String, String>?, key: String, value: String) {
                carrier?.put(key, value)
            }
        }
    }
}
