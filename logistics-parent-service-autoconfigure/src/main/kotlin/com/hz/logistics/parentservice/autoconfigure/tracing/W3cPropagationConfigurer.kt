package com.hz.logistics.parentservice.autoconfigure.tracing

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapPropagator

/**
 * Supplies the required W3C Trace Context propagator to Boot's OpenTelemetry
 * SDK. The OpenTelemetry implementation treats absent or malformed carriers as
 * an invalid root context, allowing server instrumentation to start a safe new
 * trace instead of failing the request.
 */
class W3cPropagationConfigurer {

    private val w3c: TextMapPropagator = W3CTraceContextPropagator.getInstance()

    fun contextPropagators(): ContextPropagators = ContextPropagators.create(w3c)
}
