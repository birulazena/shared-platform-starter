package com.hz.logistics.parentservice.autoconfigure.security.mvc

import com.hz.logistics.parentservice.autoconfigure.errors.PlatformProblemDetailFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ProblemDetail
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import java.nio.charset.StandardCharsets

/**
 * Servlet adapters for the shared platform problem contract.
 *
 * The response is written directly so a consumer-selected MVC stack does not
 * need a platform-owned Jackson dependency. Only the public ProblemDetail
 * fields are serialized, keeping the representation stable and safe.
 */
class PlatformMvcSecurityFailureHandlers(
    private val problemDetailFactory: PlatformProblemDetailFactory,
) {

    val authenticationEntryPoint: AuthenticationEntryPoint = AuthenticationEntryPoint { request, response, _ ->
        if (!response.isCommitted) {
            writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED, problemDetailFactory.unauthorized(request.path()))
        }
    }

    val accessDeniedHandler: AccessDeniedHandler = AccessDeniedHandler { request, response, _: AccessDeniedException ->
        if (!response.isCommitted) {
            writeProblem(response, HttpServletResponse.SC_FORBIDDEN, problemDetailFactory.forbidden(request.path()))
        }
    }

    private fun writeProblem(response: HttpServletResponse, status: Int, problem: ProblemDetail) {
        response.status = status
        // OAuth 2.0 resource-server clients rely on this header independently
        // of whether they choose to parse the problem response body.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        response.contentType = PlatformProblemDetailFactory.PROBLEM_MEDIA_TYPE.toString()
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.outputStream.write(problem.toPlatformJson().toByteArray(StandardCharsets.UTF_8))
    }

    private fun HttpServletRequest.path(): String =
        requestURI.orEmpty().removePrefix(contextPath.orEmpty()).ifEmpty { "/" }
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
