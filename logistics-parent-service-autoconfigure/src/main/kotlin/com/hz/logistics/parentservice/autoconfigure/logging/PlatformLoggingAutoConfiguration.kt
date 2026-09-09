package com.hz.logistics.parentservice.autoconfigure.logging

import ch.qos.logback.classic.LoggerContext
import com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import com.hz.logistics.parentservice.autoconfigure.properties.PlatformProperties
import com.hz.logistics.parentservice.autoconfigure.tracing.PlatformTracingAutoConfiguration
import io.opentelemetry.api.OpenTelemetry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean

/**
 * Connects property binding and application customisation to the Logback
 * pre-sink boundary. An application-supplied Logback resource has no platform
 * fan-out appender to configure and therefore keeps complete precedence.
 */
@AutoConfiguration
@AutoConfigureAfter(PlatformAutoConfiguration::class, PlatformTracingAutoConfiguration::class)
@ConditionalOnProperty(
    prefix = "logistics.parent-service.logging",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class PlatformLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PlatformLogSanitizer::class)
    fun platformLogSanitizer(properties: PlatformProperties): PlatformLogSanitizer =
        DefaultPlatformLogSanitizer(properties.logging)

    /**
     * The wrapper invokes the user policy but applies baseline-plus-configured
     * sanitization both before and after it. A replacement can add policy, but
     * it cannot reintroduce a credential that a sink could serialize.
     */
    @Bean
    fun platformLoggingPipeline(
        properties: PlatformProperties,
        sanitizerProvider: ObjectProvider<PlatformLogSanitizer>,
        openTelemetryProvider: ObjectProvider<OpenTelemetry>,
        correlationContext: PlatformCorrelationContext,
    ): PlatformLoggingPipeline {
        val configuredPolicy = sanitizerProvider.getObject()
        val baseline = DefaultPlatformLogSanitizer(properties.logging)
        val enforced = BaselineEnforcingLogSanitizer(baseline, configuredPolicy)
        val redactors = installedRedactors()
        redactors.forEach {
            it.setSanitizer(enforced)
            it.setCorrelationContext(correlationContext)
            it.setPipelineEnabled(properties.logging.enabled)
            it.setConsoleEnabled(properties.logging.consoleEnabled)
        }

        val installer = OpenTelemetryLogbackInstaller()
        if (properties.logging.otelEnabled) {
            openTelemetryProvider.ifAvailable { openTelemetry ->
                redactors.forEach { redactor -> installer.install(redactor, openTelemetry) }
            }
        } else {
            redactors.forEach(installer::remove)
        }
        return PlatformLoggingPipeline
    }

    private fun installedRedactors(): List<RedactingFanOutAppender> {
        val context = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return emptyList()
        return context.loggerList.flatMap { logger ->
            generateSequence(logger.iteratorForAppenders()) { null }
                .flatMap { iterator -> iterator.asSequence() }
                .filterIsInstance<RedactingFanOutAppender>()
                .toList()
        }.distinct()
    }
}

/** Marker bean proving the logging contribution was configured. */
object PlatformLoggingPipeline

private class BaselineEnforcingLogSanitizer(
    private val baseline: PlatformLogSanitizer,
    private val configuredPolicy: PlatformLogSanitizer,
) : PlatformLogSanitizer {
    override fun sanitize(event: PlatformLogEvent): PlatformLogEvent =
        baseline.sanitize(configuredPolicy.sanitize(baseline.sanitize(event)))
}
