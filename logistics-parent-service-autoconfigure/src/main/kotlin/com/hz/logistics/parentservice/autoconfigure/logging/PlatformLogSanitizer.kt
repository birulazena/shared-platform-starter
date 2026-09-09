package com.hz.logistics.parentservice.autoconfigure.logging

import com.hz.logistics.parentservice.autoconfigure.properties.LoggingProperties
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale

/**
 * Application-owned replacement point for structured log sanitization.
 *
 * A supplied implementation backs off only the platform sanitizer policy. The
 * logging pipeline remains responsible for preserving structured JSON,
 * correlation fields, and the immutable baseline redaction categories
 * (credentials, authorization, tokens, and secrets) before any sink receives
 * an event.
 */
fun interface PlatformLogSanitizer {

    /** Return a sanitized, preferably immutable view of [event]. */
    fun sanitize(event: PlatformLogEvent): PlatformLogEvent

    companion object {
        /** A pass-through implementation useful when composing test policies. */
        @JvmField
        val IDENTITY: PlatformLogSanitizer = PlatformLogSanitizer { it }
    }
}

/**
 * Stack-neutral structured event passed through the platform sanitizer SPI.
 * Values are intentionally represented as opaque objects so maps, lists, and
 * throwable projections can be sanitized without binding the public API to a
 * particular Logback implementation.
 */
data class PlatformLogEvent(
    val message: String,
    val arguments: List<Any?> = emptyList(),
    val fields: Map<String, Any?> = emptyMap(),
    val throwable: Throwable? = null,
    val traceId: String? = null,
    val spanId: String? = null,
)

/**
 * Default baseline-plus-configuration sanitizer used by the logging pipeline.
 *
 * It makes an immutable projection rather than editing a Logback event in
 * place.  That keeps the fan-out boundary safe when one event is consumed by
 * both console JSON and OpenTelemetry appenders.
 */
class DefaultPlatformLogSanitizer(
    private val properties: LoggingProperties,
) : PlatformLogSanitizer {

    init {
        require(properties.redactionMask.isNotBlank()) { "The log redaction mask must not be blank." }
        require(properties.additionalSensitiveFields.all(String::isNotBlank)) {
            "Additional sensitive field names must not be blank."
        }
        require(properties.additionalSensitivePaths.all(::isValidPath)) {
            "Additional sensitive paths must be non-blank dot-separated paths."
        }
    }

    private val configuredFields = properties.additionalSensitiveFields
        .map(::normalize)
        .toSet()

    private val configuredPaths = properties.additionalSensitivePaths
        .map { path -> path.split('.').map { segment -> segment.lowercase(Locale.ROOT) } }
        .toSet()

    override fun sanitize(event: PlatformLogEvent): PlatformLogEvent = PlatformLogEvent(
        message = sanitizeText(event.message),
        arguments = immutableList(event.arguments.map { value -> sanitizeValue(value, emptyList()) }),
        fields = immutableMap(sanitizeMap(event.fields, emptyList())),
        throwable = sanitizeThrowable(event.throwable, IdentityHashMap()),
        traceId = event.traceId,
        spanId = event.spanId,
    )

    private fun sanitizeMap(source: Map<*, *>, parentPath: List<String>): Map<String, Any?> {
        val sanitized = LinkedHashMap<String, Any?>(source.size)
        source.forEach { (key, value) ->
            val name = key?.toString().orEmpty()
            val path = parentPath + name.lowercase(Locale.ROOT)
            sanitized[name] = if (isSensitiveKey(name) || path in configuredPaths) {
                properties.redactionMask
            } else {
                sanitizeValue(value, path)
            }
        }
        return sanitized
    }

    private fun sanitizeValue(value: Any?, path: List<String>): Any? = when (value) {
        null -> null
        is CharSequence -> sanitizeText(value.toString())
        is Map<*, *> -> immutableMap(sanitizeMap(value, path))
        is Iterable<*> -> immutableList(value.map { item -> sanitizeValue(item, path) })
        is Array<*> -> immutableList(value.map { item -> sanitizeValue(item, path) })
        is Throwable -> sanitizeThrowable(value, IdentityHashMap())
        else -> value
    }

    private fun sanitizeThrowable(
        throwable: Throwable?,
        visited: IdentityHashMap<Throwable, SanitizedThrowable>,
    ): Throwable? {
        if (throwable == null) {
            return null
        }
        visited[throwable]?.let { return it }

        // Exception class names and stack frames reveal implementation details;
        // the logging contract only needs a safe, complete cause projection.
        val projection = SanitizedThrowable(sanitizeText(throwable.message.orEmpty()))
        visited[throwable] = projection
        projection.stackTrace = emptyArray()
        throwable.cause?.let { cause -> projection.initCause(sanitizeThrowable(cause, visited)) }
        throwable.suppressed.forEach { suppressed ->
            sanitizeThrowable(suppressed, visited)?.let(projection::addSuppressed)
        }
        return projection
    }

    private fun sanitizeText(value: String): String = value
        .replace(BEARER_OR_BASIC_PATTERN) { match -> "${match.groupValues[1]}${properties.redactionMask}" }
        .replace(JWT_PATTERN, properties.redactionMask)
        .replace(SENSITIVE_ASSIGNMENT_PATTERN) { match -> "${match.groupValues[1]}=${properties.redactionMask}" }
        .replace(SENSITIVE_VALUE_PATTERN, properties.redactionMask)
        .replace(EMAIL_PATTERN, properties.redactionMask)
        .replace(PHONE_PATTERN, properties.redactionMask)

    private fun isSensitiveKey(name: String): Boolean {
        val normalized = normalize(name)
        return normalized in BASELINE_KEYS ||
            normalized in configuredFields ||
            normalized.endsWith("token") ||
            normalized.contains("apikey") ||
            normalized.contains("password") ||
            normalized.contains("secret") ||
            normalized.contains("authorization")
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(SEPARATORS, "")

    private fun isValidPath(path: String): Boolean =
        path.isNotBlank() && path.split('.').all(String::isNotBlank)

    private fun <T> immutableList(values: List<T>): List<T> = Collections.unmodifiableList(values.toList())

    private fun <K, V> immutableMap(values: Map<K, V>): Map<K, V> =
        Collections.unmodifiableMap(LinkedHashMap(values))

    private class SanitizedThrowable(message: String) : RuntimeException(message) {
        override fun fillInStackTrace(): Throwable = this
    }

    private companion object {
        val SEPARATORS = Regex("[-_.\\s]")
        val BASELINE_KEYS = setOf(
            "authorization",
            "proxyauthorization",
            "password",
            "passwd",
            "pwd",
            "accesstoken",
            "refreshtoken",
            "idtoken",
            "token",
            "secret",
            "clientsecret",
            "apikey",
        )
        val BEARER_OR_BASIC_PATTERN = Regex("(?i)\\b((?:bearer|basic)\\s+)[^\\s,;]+")
        val JWT_PATTERN = Regex("(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+(?![A-Za-z0-9_-])")
        val SENSITIVE_ASSIGNMENT_PATTERN = Regex(
            "(?i)\\b(password|passwd|pwd|authorization|access[-_.]?token|refresh[-_.]?token|id[-_.]?token|token|secret|client[-_.]?secret|api[-_.]?key)\\s*[:=]\\s*([^\\s,;]+)",
        )
        val SENSITIVE_VALUE_PATTERN = Regex(
            "(?i)\\b(?:password|passwd|pwd|access[-_.]?token|refresh[-_.]?token|id[-_.]?token|token|secret|client[-_.]?secret|api[-_.]?key)(?:[-_.][A-Za-z0-9]+)+\\b",
        )
        val EMAIL_PATTERN = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
        val PHONE_PATTERN = Regex("(?<!\\d)\\+?\\d[\\d .()-]{6,}\\d(?!\\d)")
    }
}
