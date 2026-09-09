package com.hz.logistics.parentservice.autoconfigure.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import io.opentelemetry.api.OpenTelemetry

/**
 * Attaches the approved alpha Logback appender only when it is available at
 * runtime. Keeping the appender type behind reflection is deliberate: the
 * public starter remains usable when an application has no OTel log provider
 * (or has excluded the optional appender artifact).
 */
class OpenTelemetryLogbackInstaller {

    fun install(redactor: RedactingFanOutAppender, openTelemetry: OpenTelemetry) {
        val appender = redactor.getAppender(PLATFORM_OTEL) ?: createAppender(redactor) ?: return
        runCatching {
            appender.javaClass
                .getMethod("setOpenTelemetry", OpenTelemetry::class.java)
                .invoke(appender, openTelemetry)
        }
    }

    fun remove(redactor: RedactingFanOutAppender) {
        redactor.detachAppender(PLATFORM_OTEL)
    }

    @Suppress("UNCHECKED_CAST")
    private fun createAppender(redactor: RedactingFanOutAppender): Appender<ILoggingEvent>? = runCatching {
        val type = Class.forName(OTEL_APPENDER_CLASS, true, redactor.javaClass.classLoader)
        val candidate = type.getDeclaredConstructor().newInstance() as? Appender<ILoggingEvent> ?: return null
        candidate.name = PLATFORM_OTEL
        candidate.start()
        redactor.addAppender(candidate)
        candidate
    }.getOrNull()

    private companion object {
        const val OTEL_APPENDER_CLASS =
            "io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"
    }
}
