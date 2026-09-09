package com.hz.logistics.parentservice.autoconfigure.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.UnsynchronizedAppenderBase
import ch.qos.logback.core.spi.AppenderAttachable
import ch.qos.logback.core.spi.AppenderAttachableImpl
import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import com.hz.logistics.parentservice.autoconfigure.properties.LoggingProperties
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.slf4j.event.KeyValuePair
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Single pre-sink boundary for the platform logging pipeline.
 *
 * Logback events are mutable and can retain raw arguments, MDC values, and
 * throwable graphs.  This appender never forwards that event.  It constructs
 * one independent, sanitized [LoggingEvent] and sends that *same instance* to
 * every attached sink, so adding an OpenTelemetry sink cannot bypass console
 * redaction.
 */
class RedactingFanOutAppender : UnsynchronizedAppenderBase<ILoggingEvent>(), AppenderAttachable<ILoggingEvent> {

    private val appenders = AppenderAttachableImpl<ILoggingEvent>()

    @Volatile
    private var sanitizer: PlatformLogSanitizer = DefaultPlatformLogSanitizer(LoggingProperties())

    @Volatile
    private var pipelineEnabled: Boolean = true

    @Volatile
    private var consoleEnabled: Boolean = true

    @Volatile
    private var correlationContext: PlatformCorrelationContext? = null

    /** Allows Spring property substitution in the default Logback resource. */
    fun setPipelineEnabled(enabled: Boolean) {
        pipelineEnabled = enabled
    }

    /** Allows Spring property substitution and runtime property binding. */
    fun setConsoleEnabled(enabled: Boolean) {
        consoleEnabled = enabled
    }

    /** Replaces the configurable policy; callers must enforce the baseline. */
    fun setSanitizer(sanitizer: PlatformLogSanitizer) {
        this.sanitizer = sanitizer
    }

    /** Supplies request-scoped fallback correlation when no OTel span exists. */
    fun setCorrelationContext(correlationContext: PlatformCorrelationContext) {
        this.correlationContext = correlationContext
    }

    override fun append(event: ILoggingEvent) {
        if (!pipelineEnabled) {
            return
        }

        val sanitized = sanitizedSnapshot(event) ?: return
        appenders.iteratorForAppenders().forEachRemaining { appender ->
            if (consoleEnabled || appender.name != PLATFORM_JSON_CONSOLE) {
                appender.doAppend(sanitized)
            }
        }
    }

    private fun sanitizedSnapshot(event: ILoggingEvent): LoggingEvent? {
        val originalMdc = event.mdcPropertyMap.orEmpty()
        val originalKeyValues = event.keyValuePairs.orEmpty()
        val correlation = currentCorrelation()
        val fields = LinkedHashMap<String, Any?>(originalMdc.size + originalKeyValues.size + 2)
        fields.putAll(originalMdc)
        originalKeyValues.forEach { pair -> fields[pair.key] = pair.value }
        correlation.traceId?.let { fields[TRACE_ID] = it }
        correlation.spanId?.let { fields[SPAN_ID] = it }

        val sanitized = sanitizer.sanitize(
            PlatformLogEvent(
                message = event.formattedMessage,
                arguments = event.argumentArray?.toList().orEmpty(),
                fields = fields,
                throwable = event.throwableProxy?.toThrowable(),
                traceId = correlation.traceId,
                spanId = correlation.spanId,
            ),
        )

        val logger = loggerFor(event.loggerName) ?: return null
        val snapshot = LoggingEvent(
            RedactingFanOutAppender::class.java.name,
            logger,
            event.level ?: Level.INFO,
            sanitized.message,
            sanitized.throwable,
            emptyArray(),
        )
        snapshot.timeStamp = event.timeStamp
        snapshot.threadName = event.threadName

        val sanitizedMdc = LinkedHashMap<String, String>(originalMdc.size + 2)
        originalMdc.keys.forEach { key ->
            sanitized.fields[key]?.toSafeText()?.let { sanitizedMdc[key] = it }
        }
        correlation.traceId?.let { sanitizedMdc[TRACE_ID] = it }
        correlation.spanId?.let { sanitizedMdc[SPAN_ID] = it }
        snapshot.mdcPropertyMap = Collections.unmodifiableMap(sanitizedMdc)

        val sanitizedKeyValues = originalKeyValues.map { pair ->
            KeyValuePair(pair.key, sanitized.fields[pair.key].toSafeText())
        }
        snapshot.keyValuePairs = Collections.unmodifiableList(sanitizedKeyValues)
        snapshot.prepareForDeferredProcessing()
        return snapshot
    }

    private fun loggerFor(name: String): Logger? =
        (context as? LoggerContext)?.getLogger(name)
            ?: (LoggerFactory.getLogger(name) as? Logger)

    private fun currentCorrelation(): Correlation {
        val spanContext = Span.current().spanContext
        return if (spanContext.isValid) {
            Correlation(spanContext.traceId, spanContext.spanId)
        } else {
            correlationContext?.current()?.let { Correlation(it.traceId, it.spanId) }
                ?: Correlation(null, null)
        }
    }

    override fun addAppender(newAppender: Appender<ILoggingEvent>) = appenders.addAppender(newAppender)

    override fun iteratorForAppenders(): MutableIterator<Appender<ILoggingEvent>> = appenders.iteratorForAppenders()

    override fun getAppender(name: String): Appender<ILoggingEvent>? = appenders.getAppender(name)

    override fun isAttached(appender: Appender<ILoggingEvent>): Boolean = appenders.isAttached(appender)

    override fun detachAndStopAllAppenders() = appenders.detachAndStopAllAppenders()

    override fun detachAppender(appender: Appender<ILoggingEvent>): Boolean = appenders.detachAppender(appender)

    override fun detachAppender(name: String): Boolean = appenders.detachAppender(name)

    private data class Correlation(
        val traceId: String?,
        val spanId: String?,
    )

    private companion object {
        const val TRACE_ID = "traceId"
        const val SPAN_ID = "spanId"
    }
}

/**
 * Rebuilds a throwable graph from Logback's immutable proxy before handing it
 * to the shared sanitizer. Exception class names and stack frames are omitted
 * intentionally: they are not part of the external logging contract.
 */
private fun IThrowableProxy.toThrowable(
    visited: IdentityHashMap<IThrowableProxy, Throwable> = IdentityHashMap(),
): Throwable {
    visited[this]?.let { return it }
    val projected = RuntimeException(message.orEmpty())
    visited[this] = projected
    cause?.let { projected.initCause(it.toThrowable(visited)) }
    suppressed?.forEach { projected.addSuppressed(it.toThrowable(visited)) }
    projected.stackTrace = emptyArray()
    return projected
}

/** Unknown object values are never rendered by an output sink. */
private fun Any?.toSafeText(): String = when (this) {
    null -> ""
    is CharSequence -> toString()
    is Number, is Boolean, is Char -> toString()
    else -> "[REDACTED]"
}

internal const val PLATFORM_JSON_CONSOLE = "PLATFORM_JSON_CONSOLE"
internal const val PLATFORM_OTEL = "PLATFORM_OTEL"
