package com.hz.logistics.parentservice.autoconfigure.metrics

import com.hz.logistics.parentservice.autoconfigure.properties.MetricsProperties
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.util.Locale

/**
 * Default Micrometer common-tag policy.
 *
 * The policy is intentionally small: it validates the configured tags and
 * contributes them to an existing registry.  It exposes neither an
 * OpenTelemetry metrics API nor an exporter configuration surface.
 */
class PlatformMetricsPolicy(
    private val properties: MetricsProperties,
) : PlatformMetricsCustomizer {

    override fun customize(registry: MeterRegistry, properties: MetricsProperties) {
        val tags = validatedTags(properties.commonTags)
        if (tags.isNotEmpty()) {
            registry.config().commonTags(tags)
        }
    }

    /** Validate once without leaking a configured value in an exception. */
    fun validatedTags(): List<Tag> = validatedTags(properties.commonTags)

    private fun validatedTags(commonTags: Map<String, String>): List<Tag> = commonTags.entries
        .sortedBy { it.key }
        .map { (name, value) ->
            require(name.isNotBlank() && value.isNotBlank()) {
                "Platform metrics common-tag names and values must be non-blank."
            }
            require(!isSensitiveName(name) && !containsSensitiveValue(value)) {
                "Platform metrics common tags must not contain credential or personal data."
            }
            Tag.of(name.trim(), value.trim())
        }

    private fun isSensitiveName(name: String): Boolean {
        val normalized = name.lowercase(Locale.ROOT).replace(SEPARATOR_PATTERN, "")
        return normalized in EXACT_SENSITIVE_NAMES ||
            normalized.endsWith("token") ||
            normalized.contains("apikey") ||
            normalized.contains("password") ||
            normalized.contains("secret") ||
            normalized.contains("authorization") ||
            normalized.contains("email") ||
            normalized.contains("phone")
    }

    private fun containsSensitiveValue(value: String): Boolean =
        JWT_PATTERN.containsMatchIn(value) ||
            BEARER_PATTERN.containsMatchIn(value) ||
            EMAIL_PATTERN.containsMatchIn(value) ||
            PHONE_PATTERN.containsMatchIn(value) ||
            SENSITIVE_ASSIGNMENT_PATTERN.containsMatchIn(value)

    private companion object {
        val SEPARATOR_PATTERN = Regex("[-_.\\s]")
        val EXACT_SENSITIVE_NAMES = setOf("authorization", "proxyauthorization", "password", "passwd", "pwd", "token", "secret")
        val JWT_PATTERN = Regex("(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+(?![A-Za-z0-9_-])")
        val BEARER_PATTERN = Regex("(?i)\\b(?:bearer|basic)\\s+[^\\s,;]+")
        val EMAIL_PATTERN = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
        val PHONE_PATTERN = Regex("(?<!\\d)\\+?\\d[\\d .()-]{6,}\\d(?!\\d)")
        val SENSITIVE_ASSIGNMENT_PATTERN = Regex(
            "(?i)\\b(?:password|passwd|pwd|authorization|access[-_.]?token|refresh[-_.]?token|id[-_.]?token|token|secret|client[-_.]?secret|api[-_.]?key)\\s*[:=]",
        )
    }
}
