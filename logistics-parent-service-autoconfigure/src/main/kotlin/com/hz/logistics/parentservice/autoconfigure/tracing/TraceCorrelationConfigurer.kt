package com.hz.logistics.parentservice.autoconfigure.tracing

import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import io.micrometer.tracing.otel.bridge.Slf4JEventListener

/**
 * Synchronizes the active OpenTelemetry scope into SLF4J MDC using the stable
 * `traceId` and `spanId` keys. The listener removes both keys on scope close,
 * so thread reuse cannot attach stale correlation to another request.
 */
class TraceCorrelationConfigurer(
    private val correlationContext: PlatformCorrelationContext,
) : Slf4JEventListener() {

    /** Shared error adapters use the same source of truth when MDC is absent. */
    fun traceIdForFailure(): String = correlationContext.requiredTraceId()
}
