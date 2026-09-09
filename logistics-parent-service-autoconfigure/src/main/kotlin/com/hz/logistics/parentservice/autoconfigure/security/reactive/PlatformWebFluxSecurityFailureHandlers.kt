package com.hz.logistics.parentservice.autoconfigure.security.reactive

import com.hz.logistics.parentservice.autoconfigure.errors.PlatformProblemDetailFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

/** Non-blocking Reactive adapters for the platform's shared problem contract. */
class PlatformWebFluxSecurityFailureHandlers(
    private val problemDetailFactory: PlatformProblemDetailFactory,
) {

    val authenticationEntryPoint: ServerAuthenticationEntryPoint = ServerAuthenticationEntryPoint { exchange, _ ->
        writeProblem(exchange, HttpStatus.UNAUTHORIZED, problemDetailFactory.unauthorized(exchange.path()))
    }

    val accessDeniedHandler: ServerAccessDeniedHandler = ServerAccessDeniedHandler { exchange, _ ->
        writeProblem(exchange, HttpStatus.FORBIDDEN, problemDetailFactory.forbidden(exchange.path()))
    }

    private fun writeProblem(exchange: ServerWebExchange, status: HttpStatus, problem: ProblemDetail): Mono<Void> {
        val response = exchange.response
        if (response.isCommitted) {
            return Mono.empty()
        }
        response.statusCode = status
        response.headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        response.headers.contentType = PlatformProblemDetailFactory.PROBLEM_MEDIA_TYPE
        val buffer: DataBuffer = response.bufferFactory().wrap(problem.toPlatformJson().toByteArray(StandardCharsets.UTF_8))
        return response.writeWith(Mono.just(buffer))
    }

    private fun ServerWebExchange.path(): String = request.path.pathWithinApplication().value()
}

private fun ProblemDetail.toPlatformJson(): String = buildString {
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
            else -> if (character.code < 0x20) {
                append("\\u%04x".format(character.code))
            } else {
                append(character)
            }
        }
    }
}
