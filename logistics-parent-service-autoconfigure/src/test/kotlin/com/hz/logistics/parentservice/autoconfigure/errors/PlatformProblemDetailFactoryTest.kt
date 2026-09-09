package com.hz.logistics.parentservice.autoconfigure.errors

import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import com.hz.logistics.parentservice.autoconfigure.properties.ErrorProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class PlatformProblemDetailFactoryTest {

    @Test
    fun `constructs the stable problem fields and keeps body status equal to HTTP status`() {
        val traceId = "4bf92f3577b34da6a3ce929d0e0e4736"
        val factory = PlatformProblemDetailFactory(
            PlatformCorrelationContext(fallbackTraceIdSupplier = { traceId }),
        )

        val problem = factory.unauthorized("/api/shipments?token=should-not-leak")

        assertThat(problem.type.toString()).isEqualTo("urn:hz-logistics:problem:unauthorized")
        assertThat(problem.title).isEqualTo("Unauthorized")
        assertThat(problem.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
        assertThat(problem.detail)
            .isEqualTo("Authentication is required or the access token is invalid.")
        assertThat(problem.instance).hasToString("/api/shipments")
        assertThat(problem.properties.orEmpty()[PlatformProblemDetailFactory.TRACE_ID_PROPERTY])
            .isEqualTo(traceId)
        assertThat(factory.mediaType).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON)

        val response = factory.responseEntity(HttpStatus.FORBIDDEN, "/api/shipments")
        assertThat(response.statusCode.value()).isEqualTo(response.body?.status)
        assertThat(response.headers.contentType).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON)
    }

    @Test
    fun `sanitizes instance to an application path without query or fragment`() {
        val factory = PlatformProblemDetailFactory(
            PlatformCorrelationContext(fallbackTraceIdSupplier = TRACE_ID_SUPPLIER),
        )

        assertThat(factory.create(400, "/orders/42?customerEmail=alice@example.test#details").instance)
            .hasToString("/orders/42")
        assertThat(factory.create(400, "/orders/42#details?secret=hidden").instance)
            .hasToString("/orders/42")

        listOf(
            null,
            "",
            "orders/42",
            "//external.example/orders",
            "https://external.example/orders",
            "/orders/42\\backup",
            "/orders/\u0000",
        ).forEach { requestPath ->
            assertThat(factory.create(400, requestPath).instance)
                .describedAs("requestPath=%s", requestPath)
                .isNull()
        }
    }

    @Test
    fun `uses a valid fallback trace when normal tracing is unavailable`() {
        val fallback = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val fallbackFactory = PlatformProblemDetailFactory(
            PlatformCorrelationContext(fallbackTraceIdSupplier = { fallback }),
        )

        assertThat(fallbackFactory.internalError().properties.orEmpty()[PlatformProblemDetailFactory.TRACE_ID_PROPERTY])
            .isEqualTo(fallback)

        // A factory cannot echo malformed or missing correlation values. The
        // fallback path still produces a W3C-valid, non-zero trace ID.
        val generated = PlatformProblemDetailFactory(
            PlatformCorrelationContext(fallbackTraceIdSupplier = { "not-a-w3c-trace-id" }),
        ).internalError()
            .properties.orEmpty()[PlatformProblemDetailFactory.TRACE_ID_PROPERTY] as String
        assertThat(generated).matches("[0-9a-f]{32}")
        assertThat(generated).isNotEqualTo("0".repeat(32))
    }

    @Test
    fun `generic policy ignores arbitrary details and safe policy redacts sensitive details`() {
        val detail = "password=canary-password Authorization: Bearer canary-token " +
            "jwt=eyJhbGciOiJub25lIn0.eyJzdWIiOiJjYW5hcnkifQ.signature-canary secret=canary-secret"

        val generic = PlatformProblemDetailFactory(
            PlatformCorrelationContext(fallbackTraceIdSupplier = TRACE_ID_SUPPLIER),
            ErrorProperties(),
        )
        assertThat(generic.createSafe(HttpStatus.BAD_REQUEST, "/validation", detail).detail)
            .isEqualTo("The request is invalid.")

        val safeProperties = ErrorProperties().apply {
            detailPolicy = ErrorProperties.DetailPolicy.SAFE
        }
        val safe = PlatformProblemDetailFactory(
            PlatformCorrelationContext(fallbackTraceIdSupplier = TRACE_ID_SUPPLIER),
            safeProperties,
        )

        val redacted = safe.createSafe(HttpStatus.BAD_REQUEST, "/validation", detail).detail
        assertThat(redacted).doesNotContain(
            "canary-password",
            "canary-token",
            "canary-secret",
            "signature-canary",
        )
        assertThat(redacted).contains(PlatformProblemDetailFactory.REDACTION_MASK)
    }

    @Test
    fun `safe policy removes exception class and stack content`() {
        val safeProperties = ErrorProperties().apply {
            detailPolicy = ErrorProperties.DetailPolicy.SAFE
        }
        val factory = PlatformProblemDetailFactory(
            PlatformCorrelationContext(fallbackTraceIdSupplier = TRACE_ID_SUPPLIER),
            safeProperties,
        )

        val detail = "IllegalStateException: leaked at com.hz.logistics.SecretController.handle(SecretController.kt:42) " +
            "Caused by: com.example.SecretFailure customer@example.test +48123456789"
        val sanitized = factory.createSafe(HttpStatus.INTERNAL_SERVER_ERROR, "/failure", detail).detail

        assertThat(sanitized)
            .doesNotContain(
                "IllegalStateException",
                "com.hz.logistics.SecretController.handle",
                "Caused by:",
                "customer@example.test",
                "+48123456789",
            )
            .contains(PlatformProblemDetailFactory.REDACTION_MASK)
    }

    @Test
    fun `fallback correlation is stable inside a scope and cleared at its boundary`() {
        val correlation = PlatformCorrelationContext(fallbackTraceIdSupplier = TRACE_ID_SUPPLIER)
        val factory = PlatformProblemDetailFactory(correlation)

        correlation.withExecutionScope {
            val first = factory.internalError().properties.orEmpty()[PlatformProblemDetailFactory.TRACE_ID_PROPERTY]
            val second = correlation.requiredTraceId()
            assertThat(second).isEqualTo(first)
        }

        assertThat(correlation.requiredTraceId()).isEqualTo(TRACE_ID_SUPPLIER())
        correlation.endExecutionScope()
    }

    @Test
    fun `current correlation exposes only a scoped fallback and clears it afterwards`() {
        val correlation = PlatformCorrelationContext(fallbackTraceIdSupplier = TRACE_ID_SUPPLIER)

        assertThat(correlation.current()).isNull()
        val scope = correlation.openExecutionScope()
        assertThat(correlation.current()?.traceId).isEqualTo(scope.traceId)

        correlation.closeExecutionScope(scope)
        assertThat(correlation.current()).isNull()
    }

    companion object {
        private val TRACE_ID_SUPPLIER = { "cccccccccccccccccccccccccccccccc" }
    }
}
