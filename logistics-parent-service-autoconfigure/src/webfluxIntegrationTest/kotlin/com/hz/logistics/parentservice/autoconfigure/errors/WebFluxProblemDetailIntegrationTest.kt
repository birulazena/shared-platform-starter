package com.hz.logistics.parentservice.autoconfigure.errors

import com.fasterxml.jackson.databind.ObjectMapper
import com.hz.logistics.parentservice.autoconfigure.logging.PlatformLogSanitizer
import com.hz.logistics.parentservice.autoconfigure.metrics.PlatformMetricsCustomizer
import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import com.hz.logistics.parentservice.autoconfigure.support.mockJwt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.ApplicationContext
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@SpringBootTest(
    classes = [WebFluxProblemDetailFixtureApplication::class],
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "logistics.parent-service.security.issuer=https://identity.example.test/realms/logistics",
        "logistics.parent-service.security.public-endpoints[0]=/problem/method-authentication",
        "logistics.parent-service.errors.detail-policy=SAFE",
        "management.otlp.metrics.export.enabled=false",
    ],
)
class WebFluxProblemDetailIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    private val objectMapper = ObjectMapper()

    private val client: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
    }

    @Test
    fun rendersEquivalentSafeProblemsForAuthenticationAuthorizationClientAndServerFailures() {
        assertProblem(client.get().uri("/problem/secured").exchange().expectBody().returnResult(), 401, "/problem/secured")
        assertProblem(
            client.get().uri("/problem/secured").header(HttpHeaders.AUTHORIZATION, "Bearer trusted")
                .exchange()
                .expectBody()
                .returnResult(),
            403,
            "/problem/secured",
        )
        assertProblem(
            client.get().uri("/problem/client").header(HttpHeaders.AUTHORIZATION, "Bearer trusted")
                .exchange()
                .expectBody()
                .returnResult(),
            400,
            "/problem/client",
        )
        assertProblem(
            client.get().uri("/problem/unhandled").header(HttpHeaders.AUTHORIZATION, "Bearer trusted")
                .exchange()
                .expectBody()
                .returnResult(),
            500,
            "/problem/unhandled",
        )
        assertProblem(
            client.get().uri("/problem/method-authentication").exchange().expectBody().returnResult(),
            403,
            "/problem/method-authentication",
        )
    }

    @Test
    fun preservesTraceCorrelationAcrossReactiveSchedulingAndPrefersProblemJsonForUnsupportedAccept() {
        val response = client.get().uri("/problem/reactor")
            .header("traceparent", VALID_TRACE_PARENT)
            .header(HttpHeaders.AUTHORIZATION, "Bearer trusted")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody()
            .returnResult()

        assertProblem(response, 500, "/problem/reactor")
        assertThat(objectMapper.readTree(body(response)).path("traceId").asText()).isEqualTo(TRACE_ID)
    }

    @Test
    fun leavesCommittedReactiveResponsesAloneInsteadOfWritingASecondProblemBody() {
        val response = client.get().uri("/problem/committed").header(HttpHeaders.AUTHORIZATION, "Bearer trusted")
            .exchange()
            .expectBody()
            .returnResult()

        assertThat(response.status.value()).isEqualTo(HttpStatus.OK.value())
        assertThat(body(response).toString(Charsets.UTF_8)).isEqualTo("already-written")
        assertSensitiveCorpusIsAbsent(body(response).toString(Charsets.UTF_8))
    }

    private fun assertProblem(
        response: EntityExchangeResult<ByteArray>,
        expectedStatus: Int,
        expectedInstance: String,
    ) {
        assertThat(response.status.value()).isEqualTo(expectedStatus)
        assertThat(response.responseHeaders.contentType?.toString())
            .contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE)

        val problem = objectMapper.readTree(body(response))
        assertThat(problem.path("type").asText()).startsWith("urn:hz-logistics:problem:")
        assertThat(problem.path("title").asText()).isNotBlank()
        assertThat(problem.path("status").asInt()).isEqualTo(expectedStatus)
        assertThat(problem.path("detail").asText()).isNotBlank()
        assertThat(problem.path("instance").asText()).isEqualTo(expectedInstance)
        assertThat(problem.path("traceId").asText()).matches("[0-9a-f]{32}")
        assertSensitiveCorpusIsAbsent(body(response).toString(Charsets.UTF_8))
    }

    private fun assertSensitiveCorpusIsAbsent(value: String) {
        assertThat(value).doesNotContain(*SENSITIVE_CORPUS)
    }

    private fun body(response: EntityExchangeResult<ByteArray>): ByteArray =
        requireNotNull(response.responseBody) { "Expected an error response body" }

    private companion object {
        const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
        const val VALID_TRACE_PARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"

        val SENSITIVE_CORPUS = arrayOf(
            "java.lang.IllegalStateException",
            "at com.hz.logistics",
            "eyJhbGciOiJub25lIn0.eyJzdWIiOiJwcm9ibGVtLWNhbmFyeSJ9.problem-signature",
            "bearer-problem-canary",
            "Authorization: Bearer",
            "password=problem-password-canary",
            "secret=problem-secret-canary",
            "request-body-problem-canary",
            "x-otlp-api-key=problem-otlp-canary",
            "customerEmail=problem@example.test",
            "recipientPhone=+48123456789",
        )
    }
}

@SpringBootTest(
    classes = [WebFluxApplicationOwnedProblemHandlerFixtureApplication::class],
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "logistics.parent-service.security.enabled=false",
        "management.otlp.metrics.export.enabled=false",
    ],
)
class WebFluxApplicationOwnedProblemHandlerIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var problemDetailFactories: Map<String, PlatformProblemDetailFactory>

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    private val client: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
    }

    @Test
    fun applicationProblemFactoryAndHandlerReplaceOnlyTheReactiveErrorContribution() {
        val response = client.get().uri("/problem/unhandled").exchange().expectBody().returnResult()

        assertThat(problemDetailFactories).containsOnlyKeys("applicationProblemDetailFactory")
        assertThat(applicationContext.getBeansOfType(PlatformMetricsCustomizer::class.java)).hasSize(1)
        assertThat(applicationContext.getBeansOfType(PlatformLogSanitizer::class.java)).hasSize(1)
        assertThat(response.status.value()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value())
        assertThat(response.responseHeaders.contentType?.toString())
            .contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        assertThat(requireNotNull(response.responseBody).toString(Charsets.UTF_8))
            .contains("urn:application:problem:override")
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(WebFluxProblemDetailFixtureController::class)
class WebFluxProblemDetailFixtureApplication {

    @Bean
    fun reactiveJwtDecoder(): ReactiveJwtDecoder = ReactiveJwtDecoder { token ->
        if (token == "trusted") {
            Mono.just(mockJwt())
        } else {
            Mono.error(BadJwtException("invalid test token"))
        }
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(WebFluxProblemDetailFixtureController::class, WebFluxApplicationProblemAdvice::class)
class WebFluxApplicationOwnedProblemHandlerFixtureApplication {

    @Bean
    fun applicationProblemDetailFactory(): PlatformProblemDetailFactory =
        PlatformProblemDetailFactory(PlatformCorrelationContext())

    @Bean
    fun applicationSecurityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http.authorizeExchange { it.anyExchange().permitAll() }.build()
}

@RestController
class WebFluxProblemDetailFixtureController {

    @GetMapping("/problem/secured")
    @PreAuthorize("hasAuthority('ROLE_admin')")
    fun secured(): Mono<Map<String, String>> = Mono.just(mapOf("status" to "ok"))

    @GetMapping("/problem/method-authentication")
    @PreAuthorize("isAuthenticated()")
    fun methodAuthentication(): Mono<Map<String, String>> = Mono.just(mapOf("status" to "ok"))

    @GetMapping("/problem/client")
    fun clientFailure(): Mono<Nothing> = Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, sensitiveDetail()))

    @GetMapping("/problem/unhandled")
    fun unhandledFailure(): Mono<Nothing> = Mono.error(IllegalStateException(sensitiveDetail()))

    @GetMapping("/problem/reactor")
    fun reactiveFailure(): Mono<Nothing> =
        Mono.defer { Mono.error<Nothing>(IllegalStateException(sensitiveDetail())) }
            .publishOn(Schedulers.parallel())

    @GetMapping("/problem/committed")
    fun committedFailure(response: ServerHttpResponse): Mono<Void> {
        val body: DataBuffer = response.bufferFactory().wrap("already-written".toByteArray())
        return response.writeWith(Mono.just(body))
            .then(Mono.error(IllegalStateException(sensitiveDetail())))
    }

    private fun sensitiveDetail(): String =
        "java.lang.IllegalStateException at com.hz.logistics " +
            "Authorization: Bearer bearer-problem-canary " +
            "jwt eyJhbGciOiJub25lIn0.eyJzdWIiOiJwcm9ibGVtLWNhbmFyeSJ9.problem-signature " +
            "password=problem-password-canary secret=problem-secret-canary " +
            "request-body-problem-canary x-otlp-api-key=problem-otlp-canary " +
            "customerEmail=problem@example.test recipientPhone=+48123456789"
}

@ControllerAdvice
class WebFluxApplicationProblemAdvice {

    @ExceptionHandler(IllegalStateException::class)
    fun applicationOwnedError(): Mono<ResponseEntity<ProblemDetail>> {
        val problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        problem.type = java.net.URI.create("urn:application:problem:override")
        problem.title = "Application error"
        problem.detail = "The application owns this response."
        problem.setProperty("traceId", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        return Mono.just(
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem),
        )
    }
}
