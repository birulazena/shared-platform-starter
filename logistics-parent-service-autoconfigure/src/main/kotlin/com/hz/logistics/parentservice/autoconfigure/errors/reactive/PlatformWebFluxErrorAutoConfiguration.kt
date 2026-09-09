package com.hz.logistics.parentservice.autoconfigure.errors.reactive

import com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.errors.PlatformProblemDetailFactory
import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.validation.BindException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.NotAcceptableStatusException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

/** Non-blocking WebFlux adapter for the shared ProblemDetail representation. */
@AutoConfiguration
@AutoConfigureAfter(PlatformAutoConfiguration::class)
@AutoConfigureBefore(name = ["org.springframework.boot.webflux.autoconfigure.error.ErrorWebFluxAutoConfiguration"])
@ConditionalOnClass(name = ["org.springframework.web.reactive.DispatcherHandler"])
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(
    prefix = "logistics.parent-service.errors",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class PlatformWebFluxErrorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(annotation = [ControllerAdvice::class])
    fun platformWebFluxMethodSecurityProblemAdvice(factory: PlatformProblemDetailFactory):
        PlatformWebFluxMethodSecurityProblemAdvice = PlatformWebFluxMethodSecurityProblemAdvice(factory)

    /** Opens a Reactor-propagated fallback scope before request handling. */
    @Bean
    @ConditionalOnMissingBean(PlatformWebFluxCorrelationScopeFilter::class)
    fun platformWebFluxCorrelationScopeFilter(
        correlationContext: PlatformCorrelationContext,
    ): PlatformWebFluxCorrelationScopeFilter = PlatformWebFluxCorrelationScopeFilter(correlationContext)

    /**
     * `spring-boot-webflux` is intentionally compile-only. Isolating the
     * ErrorWebExceptionHandler return type prevents a reactive application
     * that only supplies Spring WebFlux APIs from linking an unavailable Boot
     * infrastructure class while conditions are evaluated.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.webflux.error.ErrorWebExceptionHandler"])
    class ErrorHandlerConfiguration {

        @Bean
        @ConditionalOnMissingBean(value = [ErrorWebExceptionHandler::class])
        fun platformWebFluxProblemHandler(factory: PlatformProblemDetailFactory): PlatformWebFluxProblemHandler =
            PlatformWebFluxProblemHandler(factory)
    }
}

/**
 * Handles controller and framework errors ahead of Boot's generic reactive
 * fallback. The body is written directly, so an unsupported Accept header
 * cannot select an unsafe fallback representation.
 */
class PlatformWebFluxProblemHandler(
    private val factory: PlatformProblemDetailFactory,
) : ErrorWebExceptionHandler, Ordered {

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun handle(exchange: ServerWebExchange, error: Throwable): Mono<Void> =
        write(exchange, statusFor(error))

    private fun write(exchange: ServerWebExchange, status: Int): Mono<Void> {
        if (exchange.response.isCommitted) {
            return Mono.empty()
        }
        val path = exchange.request.path.pathWithinApplication().value().ifEmpty { "/" }
        val response = exchange.response
        response.statusCode = HttpStatusCode.valueOf(status)
        response.headers.contentType = PlatformProblemDetailFactory.PROBLEM_MEDIA_TYPE
        val body: DataBuffer = response.bufferFactory().wrap(
            factory.create(status, requestPath = path).toPlatformJson().toByteArray(StandardCharsets.UTF_8),
        )
        return response.writeWith(Mono.just(body))
    }

    private fun statusFor(error: Throwable): Int = when {
        error.findCause<NotAcceptableStatusException>() != null -> HttpStatus.INTERNAL_SERVER_ERROR.value()
        error.findCause<AccessDeniedException>() != null -> HttpStatus.FORBIDDEN.value()
        error.findCause<AuthenticationCredentialsNotFoundException>() != null -> HttpStatus.FORBIDDEN.value()
        error.findCause<BindException>() != null -> HttpStatus.BAD_REQUEST.value()
        error.findCause<ResponseStatusException>() != null ->
            requireNotNull(error.findCause<ResponseStatusException>()).statusCode.value()
        else -> HttpStatus.INTERNAL_SERVER_ERROR.value()
    }

    private inline fun <reified T : Throwable> Throwable.findCause(): T? {
        var candidate: Throwable? = this
        while (candidate != null) {
            if (candidate is T) return candidate
            candidate = candidate.cause
        }
        return null
    }
}

/**
 * Reactive method security raises an authentication-context exception from a
 * controller invocation.  Treat it as an authorization denial when it reaches
 * this advice; missing/invalid bearer credentials are still handled earlier by
 * the resource-server entry point as 401 responses.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class PlatformWebFluxMethodSecurityProblemAdvice(
    private val factory: PlatformProblemDetailFactory,
) {

    @ExceptionHandler(AuthenticationCredentialsNotFoundException::class)
    fun forbidden(exchange: ServerWebExchange): Mono<ResponseEntity<ProblemDetail>> {
        if (exchange.response.isCommitted) {
            return Mono.empty()
        }
        val path = exchange.request.path.pathWithinApplication().value().ifEmpty { "/" }
        return Mono.just(
            ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(PlatformProblemDetailFactory.PROBLEM_MEDIA_TYPE)
                .body(factory.forbidden(path)),
        )
    }
}

class PlatformWebFluxCorrelationScopeFilter(
    private val correlationContext: PlatformCorrelationContext,
) : org.springframework.web.server.WebFilter, Ordered {

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun filter(
        exchange: ServerWebExchange,
        chain: org.springframework.web.server.WebFilterChain,
    ): Mono<Void> = Mono.defer {
        val scope = correlationContext.openExecutionScope()
        chain.filter(exchange).doFinally { correlationContext.closeExecutionScope(scope) }
    }
}

private fun org.springframework.http.ProblemDetail.toPlatformJson(): String = buildString {
    append('{')
    appendString("type", type.toString())
    appendString("title", title.orEmpty())
    append(',').append("\"status\":").append(status)
    appendString("detail", detail.orEmpty())
    instance?.toString()?.let { appendString("instance", it) }
    appendString("traceId", properties?.get(PlatformProblemDetailFactory.TRACE_ID_PROPERTY)?.toString().orEmpty())
    append('}')
}

private fun StringBuilder.appendString(name: String, value: String) {
    if (length > 1) append(',')
    append('"').append(name).append("\":\"").append(value.jsonEscaped()).append('"')
}

private fun String.jsonEscaped(): String = buildString(length) {
    this@jsonEscaped.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
        }
    }
}
