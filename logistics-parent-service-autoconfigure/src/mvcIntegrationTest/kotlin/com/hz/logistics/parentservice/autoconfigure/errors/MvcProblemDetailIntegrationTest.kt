package com.hz.logistics.parentservice.autoconfigure.errors

import com.fasterxml.jackson.databind.ObjectMapper
import com.hz.logistics.parentservice.autoconfigure.logging.PlatformLogSanitizer
import com.hz.logistics.parentservice.autoconfigure.metrics.PlatformMetricsCustomizer
import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import com.hz.logistics.parentservice.autoconfigure.support.mockJwt
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.server.ResponseStatusException

@SpringBootTest(
    classes = [MvcProblemDetailFixtureApplication::class],
    properties = [
        "logistics.parent-service.security.issuer=https://identity.example.test/realms/logistics",
        "logistics.parent-service.security.public-endpoints[0]=/problem/method-authentication",
        "logistics.parent-service.errors.detail-policy=SAFE",
        "management.otlp.metrics.export.enabled=false",
    ],
)
@AutoConfigureMockMvc
class MvcProblemDetailIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = ObjectMapper()

    @Test
    fun rendersEquivalentSafeProblemsForAuthenticationAuthorizationClientAndServerFailures() {
        assertProblem(mockMvc.perform(get("/problem/secured")).andReturn().response, 401, "/problem/secured")
        assertProblem(
            mockMvc.perform(get("/problem/secured").header(HttpHeaders.AUTHORIZATION, "Bearer trusted"))
                .andReturn()
                .response,
            403,
            "/problem/secured",
        )
        assertProblem(
            mockMvc.perform(get("/problem/client").header(HttpHeaders.AUTHORIZATION, "Bearer trusted"))
                .andReturn()
                .response,
            400,
            "/problem/client",
        )
        assertProblem(
            mockMvc.perform(get("/problem/unhandled").header(HttpHeaders.AUTHORIZATION, "Bearer trusted"))
                .andReturn()
                .response,
            500,
            "/problem/unhandled",
        )
        assertProblem(
            mockMvc.perform(get("/problem/method-authentication")).andReturn().response,
            403,
            "/problem/method-authentication",
        )
    }

    @Test
    fun prefersProblemJsonForUnsupportedAcceptAndNeverEchoesTheSensitiveCorpus() {
        val response = mockMvc.perform(
            get("/problem/unhandled")
                .header(HttpHeaders.AUTHORIZATION, "Bearer trusted")
                .accept(MediaType.APPLICATION_XML),
        )
            .andReturn()
            .response

        assertProblem(response, 500, "/problem/unhandled")
    }

    @Test
    fun leavesCommittedResponsesAloneInsteadOfWritingASecondProblemBody() {
        val response = mockMvc.perform(
            get("/problem/committed").header(HttpHeaders.AUTHORIZATION, "Bearer trusted"),
        ).andReturn().response

        assertThat(response.status).isEqualTo(HttpStatus.OK.value())
        assertThat(response.contentAsString).isEqualTo("already-written")
        assertSensitiveCorpusIsAbsent(response.contentAsString)
    }

    private fun assertProblem(
        response: org.springframework.mock.web.MockHttpServletResponse,
        expectedStatus: Int,
        expectedInstance: String,
    ) {
        assertThat(response.status).isEqualTo(expectedStatus)
        assertThat(response.contentType).contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE)

        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body.path("type").asText()).startsWith("urn:hz-logistics:problem:")
        assertThat(body.path("title").asText()).isNotBlank()
        assertThat(body.path("status").asInt()).isEqualTo(expectedStatus)
        assertThat(body.path("detail").asText()).isNotBlank()
        assertThat(body.path("instance").asText()).isEqualTo(expectedInstance)
        assertThat(body.path("traceId").asText()).matches("[0-9a-f]{32}")
        assertSensitiveCorpusIsAbsent(response.contentAsString)
    }

    private fun assertSensitiveCorpusIsAbsent(value: String) {
        assertThat(value).doesNotContain(*SENSITIVE_CORPUS)
    }

    private companion object {
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
    classes = [MvcApplicationOwnedProblemHandlerFixtureApplication::class],
    properties = [
        "logistics.parent-service.security.enabled=false",
        "management.otlp.metrics.export.enabled=false",
    ],
)
@AutoConfigureMockMvc
class MvcApplicationOwnedProblemHandlerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var problemDetailFactories: Map<String, PlatformProblemDetailFactory>

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun applicationProblemFactoryAndHandlerReplaceOnlyTheMvcErrorContribution() {
        val response = mockMvc.perform(get("/problem/unhandled")).andReturn().response

        assertThat(problemDetailFactories).containsOnlyKeys("applicationProblemDetailFactory")
        assertThat(applicationContext.getBeansOfType(PlatformMetricsCustomizer::class.java)).hasSize(1)
        assertThat(applicationContext.getBeansOfType(PlatformLogSanitizer::class.java)).hasSize(1)
        assertThat(response.status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value())
        assertThat(response.contentType).contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        assertThat(response.contentAsString).contains("urn:application:problem:override")
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(MvcProblemDetailFixtureController::class)
class MvcProblemDetailFixtureApplication {

    @Bean
    fun jwtDecoder(): JwtDecoder = JwtDecoder { token ->
        if (token == "trusted") {
            mockJwt()
        } else {
            throw BadJwtException("invalid test token")
        }
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(MvcProblemDetailFixtureController::class, MvcApplicationProblemAdvice::class)
class MvcApplicationOwnedProblemHandlerFixtureApplication {

    @Bean
    fun applicationProblemDetailFactory(): PlatformProblemDetailFactory =
        PlatformProblemDetailFactory(PlatformCorrelationContext())

    @Bean
    fun applicationSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http.authorizeHttpRequests { it.anyRequest().permitAll() }.build()
}

@RestController
class MvcProblemDetailFixtureController {

    @GetMapping("/problem/secured")
    @PreAuthorize("hasAuthority('ROLE_admin')")
    fun secured(): Map<String, String> = mapOf("status" to "ok")

    @GetMapping("/problem/method-authentication")
    @PreAuthorize("isAuthenticated()")
    fun methodAuthentication(): Map<String, String> = mapOf("status" to "ok")

    @GetMapping("/problem/client")
    fun clientFailure(): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, sensitiveDetail())

    @GetMapping("/problem/unhandled")
    fun unhandledFailure(): Nothing = throw IllegalStateException(sensitiveDetail())

    @GetMapping("/problem/committed")
    fun committedFailure(response: HttpServletResponse): Nothing {
        response.writer.write("already-written")
        response.flushBuffer()
        throw IllegalStateException(sensitiveDetail())
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
class MvcApplicationProblemAdvice {

    @ExceptionHandler(IllegalStateException::class)
    fun applicationOwnedError(): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        problem.type = java.net.URI.create("urn:application:problem:override")
        problem.title = "Application error"
        problem.detail = "The application owns this response."
        problem.setProperty("traceId", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem)
    }
}
