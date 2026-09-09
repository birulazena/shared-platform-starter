package com.hz.logistics.parentservice.autoconfigure.errors

import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import com.hz.logistics.parentservice.autoconfigure.properties.ErrorProperties
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import java.net.URI

/**
 * Creates the platform's stack-neutral RFC 9457/RFC 7807 problem response.
 *
 * This class only constructs the representation. MVC and WebFlux adapters are
 * responsible for writing it, which keeps the externally visible contract the
 * same without making this factory depend on either web application model.
 */
open class PlatformProblemDetailFactory(
    private val correlationContext: PlatformCorrelationContext = PlatformCorrelationContext(),
    private val errorProperties: ErrorProperties = ErrorProperties(),
) {

    constructor(
        errorProperties: ErrorProperties,
        correlationContext: PlatformCorrelationContext,
    ) : this(correlationContext, errorProperties)

    constructor(errorProperties: ErrorProperties) : this(PlatformCorrelationContext(), errorProperties)

    constructor(correlationContext: PlatformCorrelationContext) : this(correlationContext, ErrorProperties())

    /** Construct a problem using the category implied by its HTTP status. */
    open fun create(
        status: HttpStatusCode,
        requestPath: String? = null,
        detail: String? = null,
        safeDetail: Boolean = false,
    ): ProblemDetail = create(status.value(), requestPath, detail, safeDetail)

    /** Integer overload for adapters that receive a raw status code. */
    open fun create(
        status: Int,
        requestPath: String? = null,
        detail: String? = null,
        safeDetail: Boolean = false,
    ): ProblemDetail {
        val category = ProblemCategory.forStatus(status)
        return create(category, status, requestPath, detail, safeDetail)
    }

    /** Construct a problem with an explicitly selected stable category. */
    open fun create(
        category: ProblemCategory,
        status: Int = category.defaultStatus,
        requestPath: String? = null,
        detail: String? = null,
        safeDetail: Boolean = false,
    ): ProblemDetail {
        val problem = ProblemDetail.forStatus(status)
        problem.type = category.type
        problem.title = category.title
        problem.detail = detailFor(category, detail, safeDetail)
        if (errorProperties.includeInstance) {
            sanitizeRequestPath(requestPath)?.let { problem.instance = it }
        }

        // traceId is intentionally unconditional, including failures that
        // happen before normal tracing creates a span.
        problem.setProperty(TRACE_ID_PROPERTY, correlationContext.traceIdOrCreate())
        return problem
    }

    /** Convenience constructor for an explicitly classified safe detail. */
    open fun createSafe(
        status: HttpStatusCode,
        requestPath: String? = null,
        detail: String,
    ): ProblemDetail = create(status, requestPath, detail, safeDetail = true)

    /** Convenience constructor for authentication failures. */
    open fun unauthorized(requestPath: String? = null, detail: String? = null): ProblemDetail =
        create(ProblemCategory.UNAUTHORIZED, requestPath = requestPath, detail = detail)

    /** Convenience constructor for authorization failures. */
    open fun forbidden(requestPath: String? = null, detail: String? = null): ProblemDetail =
        create(ProblemCategory.FORBIDDEN, requestPath = requestPath, detail = detail)

    /** Convenience constructor for invalid client requests. */
    open fun invalidRequest(requestPath: String? = null, detail: String? = null): ProblemDetail =
        create(ProblemCategory.INVALID_REQUEST, requestPath = requestPath, detail = detail)

    /** Convenience constructor for unhandled application failures. */
    open fun internalError(requestPath: String? = null, detail: String? = null): ProblemDetail =
        create(ProblemCategory.INTERNAL_ERROR, requestPath = requestPath, detail = detail)

    /**
     * Build a response entity for adapters that want the media type applied at
     * the factory boundary. Other adapters can use [PROBLEM_MEDIA_TYPE] when
     * writing a [ProblemDetail] directly.
     */
    open fun responseEntity(
        status: HttpStatusCode,
        requestPath: String? = null,
        detail: String? = null,
        safeDetail: Boolean = false,
    ): ResponseEntity<ProblemDetail> = ResponseEntity
        .status(status)
        .contentType(PROBLEM_MEDIA_TYPE)
        .body(create(status, requestPath, detail, safeDetail))

    /** The one media type used for every platform problem body. */
    val mediaType: MediaType
        get() = PROBLEM_MEDIA_TYPE

    private fun detailFor(
        category: ProblemCategory,
        requestedDetail: String?,
        safeDetail: Boolean,
    ): String {
        if (errorProperties.detailPolicy != ErrorProperties.DetailPolicy.SAFE || !safeDetail) {
            return category.defaultDetail
        }
        val candidate = requestedDetail?.trim().orEmpty()
        if (candidate.isEmpty()) {
            return category.defaultDetail
        }
        return redactSensitiveContent(candidate)
            .takeIf(String::isNotBlank)
            ?: category.defaultDetail
    }

    /**
     * The factory has its own small immutable safety boundary so a caller
     * cannot accidentally place a credential in a SAFE detail before the full
     * logging sanitizer is installed by the later logging tasks.
     */
    private fun redactSensitiveContent(value: String): String = value
        .replace(BEARER_OR_BASIC_PATTERN) { match ->
            "${match.groupValues[1]}$REDACTION_MASK"
        }
        .replace(JWT_PATTERN, REDACTION_MASK)
        .replace(SENSITIVE_ASSIGNMENT_PATTERN) { match ->
            "${match.groupValues[1]}=$REDACTION_MASK"
        }
        .replace(SENSITIVE_VALUE_PATTERN, REDACTION_MASK)
        .replace(EMAIL_PATTERN, REDACTION_MASK)
        .replace(PHONE_PATTERN, REDACTION_MASK)
        .replace(STACK_TRACE_PATTERN, REDACTION_MASK)
        .replace(EXCEPTION_CLASS_PATTERN, REDACTION_MASK)

    private fun sanitizeRequestPath(requestPath: String?): URI? {
        val value = requestPath?.trim().orEmpty()
        if (value.isEmpty() || value.startsWith("//") || !value.startsWith('/')) {
            return null
        }

        // Strip fragment first so a '?' inside a fragment cannot become part
        // of the exposed instance. The resulting URI is always path-only.
        val path = value.substringBefore('#').substringBefore('?')
        if (path.isEmpty() || path.contains('\\') || path.any(Char::isISOControl)) {
            return null
        }
        return runCatching { URI.create(path) }.getOrNull()
    }

    enum class ProblemCategory(
        val type: URI,
        val title: String,
        val defaultStatus: Int,
        val defaultDetail: String,
    ) {
        UNAUTHORIZED(
            URI.create("urn:hz-logistics:problem:unauthorized"),
            "Unauthorized",
            401,
            "Authentication is required or the access token is invalid.",
        ),
        FORBIDDEN(
            URI.create("urn:hz-logistics:problem:forbidden"),
            "Forbidden",
            403,
            "Access to this resource is denied.",
        ),
        INVALID_REQUEST(
            URI.create("urn:hz-logistics:problem:invalid-request"),
            "Invalid Request",
            400,
            "The request is invalid.",
        ),
        INTERNAL_ERROR(
            URI.create("urn:hz-logistics:problem:internal-error"),
            "Internal Server Error",
            500,
            "An unexpected error occurred.",
        );

        companion object {
            fun forStatus(status: Int): ProblemCategory = when {
                status == 401 -> UNAUTHORIZED
                status == 403 -> FORBIDDEN
                status in 400..499 -> INVALID_REQUEST
                else -> INTERNAL_ERROR
            }
        }
    }

    companion object {
        const val TRACE_ID_PROPERTY = "traceId"
        const val REDACTION_MASK = "[REDACTED]"
        @JvmField
        val PROBLEM_MEDIA_TYPE: MediaType = MediaType.APPLICATION_PROBLEM_JSON

        private val BEARER_OR_BASIC_PATTERN =
            Regex("(?i)\\b((?:bearer|basic)\\s+)[^\\s,;]+")
        private val JWT_PATTERN =
            Regex("(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+(?![A-Za-z0-9_-])")
        private val SENSITIVE_ASSIGNMENT_PATTERN = Regex(
            "(?i)\\b(password|passwd|pwd|authorization|access[-_.]?token|refresh[-_.]?token|id[-_.]?token|token|secret|client[-_.]?secret|api[-_.]?key)\\s*[:=]\\s*([^\\s,;]+)",
        )
        private val STACK_TRACE_PATTERN = Regex(
            "(?m)(?:^|\\s)(?:at\\s+[A-Za-z0-9_.$]+\\([^\\r\\n)]*\\)|Caused by:\\s*[^\\r\\n]+|Suppressed:\\s*[^\\r\\n]+)",
        )
        private val EXCEPTION_CLASS_PATTERN = Regex(
            "(?<![A-Za-z0-9_$])(?:(?:[a-z_][A-Za-z0-9_$]*\\.)*)[A-Z][A-Za-z0-9_$]*(?:Exception|Error|Throwable)(?![A-Za-z0-9_$])",
        )
        private val SENSITIVE_VALUE_PATTERN = Regex(
            "(?i)\\b(?:password|passwd|pwd|access[-_.]?token|refresh[-_.]?token|id[-_.]?token|token|secret|client[-_.]?secret|api[-_.]?key)(?:[-_.][A-Za-z0-9]+)+\\b",
        )
        private val EMAIL_PATTERN = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
        private val PHONE_PATTERN = Regex("(?<!\\d)\\+?\\d[\\d .()-]{6,}\\d(?!\\d)")
    }
}
