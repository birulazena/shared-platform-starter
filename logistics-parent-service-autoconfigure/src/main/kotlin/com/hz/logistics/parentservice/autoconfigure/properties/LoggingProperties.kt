package com.hz.logistics.parentservice.autoconfigure.properties

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank

/** Configuration for structured Logback output and immutable pre-sink redaction. */
class LoggingProperties {

    /** Whether the platform logging contribution is eligible to activate. */
    var enabled: Boolean = true

    /** Whether the default structured console sink is enabled. */
    var consoleEnabled: Boolean = true

    /** Whether sanitized events are forwarded to an available OTel log pipeline. */
    var otelEnabled: Boolean = true

    /** Replacement value used by the baseline and configured redaction rules. */
    @field:NotBlank(message = "redaction-mask must not be blank")
    var redactionMask: String = "[REDACTED]"

    /** Additional case-insensitive exact fields, headers, or query parameter names. */
    var additionalSensitiveFields: Set<String> = emptySet()

    /** Additional dot-separated structured paths to redact. */
    var additionalSensitivePaths: Set<String> = emptySet()

    @AssertTrue(message = "additional-sensitive-fields entries must be non-blank")
    fun areSensitiveFieldsValid(): Boolean = additionalSensitiveFields.all(String::isNotBlank)

    @AssertTrue(message = "additional-sensitive-paths entries must be non-blank dot-separated paths")
    fun areSensitivePathsValid(): Boolean = additionalSensitivePaths.all { path ->
        path.isNotBlank() && path.split('.').all(String::isNotBlank)
    }
}
