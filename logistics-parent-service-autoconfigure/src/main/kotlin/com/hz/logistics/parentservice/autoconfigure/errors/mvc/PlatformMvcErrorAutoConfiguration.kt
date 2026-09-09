package com.hz.logistics.parentservice.autoconfigure.errors.mvc

import com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.errors.PlatformProblemDetailFactory
import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.validation.BindException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.server.ResponseStatusException

/** Servlet/MVC adapter for the platform's safe, stack-neutral problem contract. */
@AutoConfiguration
@AutoConfigureAfter(PlatformAutoConfiguration::class)
@ConditionalOnClass(name = ["org.springframework.web.servlet.DispatcherServlet"])
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
    prefix = "logistics.parent-service.errors",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class PlatformMvcErrorAutoConfiguration {

    /**
     * Establishes a fallback ID before MVC tracing/error handling can run and
     * clears it after every dispatch, including an error dispatch on a reused
     * worker thread.
     */
    @Bean
    @ConditionalOnMissingBean(PlatformMvcCorrelationScopeFilter::class)
    fun platformMvcCorrelationScopeFilter(
        correlationContext: PlatformCorrelationContext,
    ): PlatformMvcCorrelationScopeFilter = PlatformMvcCorrelationScopeFilter(correlationContext)

    /**
     * An application controller advice owns MVC error semantics.  That owner
     * is intentionally independent from the shared factory, metrics, tracing,
     * and logging capabilities.
     */
    @Bean
    @ConditionalOnMissingBean(annotation = [ControllerAdvice::class])
    fun platformMvcProblemAdvice(factory: PlatformProblemDetailFactory): PlatformMvcProblemAdvice =
        PlatformMvcProblemAdvice(factory)
}

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class PlatformMvcProblemAdvice(
    private val factory: PlatformProblemDetailFactory,
) {

    @ExceptionHandler(ResponseStatusException::class)
    fun responseStatus(
        exception: ResponseStatusException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<ProblemDetail>? = problem(response, exception.statusCode.value(), request.path())

    @ExceptionHandler(MethodArgumentNotValidException::class, BindException::class)
    fun invalidRequest(
        exception: Exception,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<ProblemDetail>? = problem(response, HttpStatus.BAD_REQUEST.value(), request.path())

    @ExceptionHandler(AccessDeniedException::class)
    fun forbidden(
        exception: AccessDeniedException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<ProblemDetail>? = problem(response, HttpStatus.FORBIDDEN.value(), request.path())

    @ExceptionHandler(AuthenticationCredentialsNotFoundException::class)
    fun methodSecurityForbidden(
        exception: AuthenticationCredentialsNotFoundException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<ProblemDetail>? = problem(response, HttpStatus.FORBIDDEN.value(), request.path())

    /**
     * Do not use exception messages as a response detail: even a configured
     * SAFE policy must not turn a controller, request body, or header into an
     * accidental disclosure channel.
     */
    @ExceptionHandler(Throwable::class)
    fun unhandled(
        exception: Throwable,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<ProblemDetail>? = problem(response, HttpStatus.INTERNAL_SERVER_ERROR.value(), request.path())

    private fun problem(
        response: HttpServletResponse,
        status: Int,
        path: String,
    ): ResponseEntity<ProblemDetail>? {
        if (response.isCommitted) {
            return null
        }
        return ResponseEntity.status(status)
            .contentType(PlatformProblemDetailFactory.PROBLEM_MEDIA_TYPE)
            .body(factory.create(status, requestPath = path))
    }

    private fun HttpServletRequest.path(): String =
        requestURI.orEmpty().removePrefix(contextPath.orEmpty()).ifEmpty { "/" }
}

class PlatformMvcCorrelationScopeFilter(
    private val correlationContext: PlatformCorrelationContext,
) : OncePerRequestFilter(), Ordered {

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun shouldNotFilterAsyncDispatch(): Boolean = false

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: jakarta.servlet.FilterChain,
    ) {
        correlationContext.withExecutionScope {
            filterChain.doFilter(request, response)
        }
    }
}
