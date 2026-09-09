package com.hz.logistics.parentservice.autoconfigure.properties

import jakarta.validation.Valid
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.validation.annotation.Validated

/**
 * Public configuration aggregate for the shared platform.
 *
 * Only the five capability namespaces below are supported. There is deliberately
 * no compatibility alias for an older or differently shaped root.
 */
@Validated
@ConfigurationProperties(prefix = "logistics.parent-service")
class PlatformProperties {

    /** Security defaults and JWT resource-server settings. */
    @field:Valid
    @field:NestedConfigurationProperty
    var security: SecurityProperties = SecurityProperties()

    /** W3C propagation, local correlation, and optional OTLP export settings. */
    @field:Valid
    @field:NestedConfigurationProperty
    var tracing: TracingProperties = TracingProperties()

    /** Micrometer platform policy settings. */
    @field:Valid
    @field:NestedConfigurationProperty
    var metrics: MetricsProperties = MetricsProperties()

    /** RFC 7807-compatible error response settings. */
    @field:Valid
    @field:NestedConfigurationProperty
    var errors: ErrorProperties = ErrorProperties()

    /** Structured logging and pre-sink redaction settings. */
    @field:Valid
    @field:NestedConfigurationProperty
    var logging: LoggingProperties = LoggingProperties()
}
