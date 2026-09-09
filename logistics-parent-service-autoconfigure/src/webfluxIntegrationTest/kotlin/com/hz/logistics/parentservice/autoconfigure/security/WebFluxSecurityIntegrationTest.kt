package com.hz.logistics.parentservice.autoconfigure.security

import com.hz.logistics.parentservice.autoconfigure.support.mockJwt
import com.hz.logistics.parentservice.autoconfigure.properties.PlatformProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.stereotype.Service
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

@SpringBootTest(
    classes = [WebFluxSecurityFixtureApplication::class],
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "logistics.parent-service.security.issuer=https://identity.example.test/realms/logistics",
        "logistics.parent-service.security.public-endpoints[0]=/public/**",
        "logistics.parent-service.security.role-claims-path=realm_access.roles",
        "management.endpoints.web.base-path=/manage",
        "management.endpoints.web.exposure.include=health,info",
    ],
)
class WebFluxSecurityIntegrationTest(
) {

    @LocalServerPort
    private var port: Int = 0

    private val webTestClient: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @Test
    fun `denies protected requests by default with a safe problem response`() {
        webTestClient.get().uri("/protected").exchange()
            .expectStatus().isUnauthorized
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.traceId").isNotEmpty()
    }

    @Test
    fun `permits configured public paths and default health and info endpoints`() {
        webTestClient.get().uri("/public/ping").exchange().expectStatus().isOk
        webTestClient.get().uri("/manage/health").exchange().expectStatus().isOk
        webTestClient.get().uri("/manage/info").exchange().expectStatus().isOk
    }

    @Test
    fun `accepts a mock jwt and applies nested role authorities`() {
        webTestClient.get().uri("/protected")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.authorities[0]").isEqualTo("ROLE_dispatcher")
            .jsonPath("$.authorities[1]").isEqualTo("ROLE_planner")
    }

    @Test
    fun `accepts a nested string role with the default prefix`() {
        webTestClient.get().uri("/protected")
            .header(HttpHeaders.AUTHORIZATION, "Bearer nested-string")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.authorities[0]").isEqualTo("ROLE_driver")
    }

    @Test
    fun `automatically authorizes delayed empty and scheduled reactive publishers`() {
        webTestClient.get().uri("/methods/reactive/pre-delayed")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("pre-authorized")

        webTestClient.get().uri("/methods/reactive/pre-empty")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
            .exchange()
            .expectStatus().isOk
            .expectBody().isEmpty

        webTestClient.get().uri("/methods/reactive/post-scheduled")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("ROLE_dispatcher")

        webTestClient.get().uri("/methods/reactive/post-scheduled")
            .header(HttpHeaders.AUTHORIZATION, "Bearer missing-role")
            .exchange()
            .expectStatus().isForbidden

        webTestClient.get().uri("/methods/reactive/pre-delayed")
            .header(HttpHeaders.AUTHORIZATION, "Bearer missing-role")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `reuses mapped WebFlux role and scope authorities in method expressions`() {
        webTestClient.get().uri("/methods/default-role")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
            .exchange()
            .expectStatus().isOk
        webTestClient.get().uri("/methods/default-role")
            .header(HttpHeaders.AUTHORIZATION, "Bearer missing-role")
            .exchange()
            .expectStatus().isForbidden
        webTestClient.get().uri("/methods/scope")
            .header(HttpHeaders.AUTHORIZATION, "Bearer scope")
            .exchange()
            .expectStatus().isOk
        webTestClient.get().uri("/methods/scope")
            .header(HttpHeaders.AUTHORIZATION, "Bearer scp")
            .exchange()
            .expectStatus().isOk
        webTestClient.get().uri("/methods/scope")
            .header(HttpHeaders.AUTHORIZATION, "Bearer missing-permission")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `rejects an invalid signature through the common problem contract`() {
        webTestClient.get().uri("/protected")
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-signature")
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
    }

    @Test
    fun `rejects an expired token through the common problem contract`() {
        webTestClient.get().uri("/protected")
            .header(HttpHeaders.AUTHORIZATION, "Bearer expired")
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
    }

    @Test
    fun `rejects an issuer mismatched token through the common problem contract`() {
        webTestClient.get().uri("/protected")
            .header(HttpHeaders.AUTHORIZATION, "Bearer issuer-mismatch")
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
    }
}

@SpringBootTest(
    classes = [WebFluxSecurityFixtureApplication::class],
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "logistics.parent-service.security.issuer=https://identity.example.test/realms/logistics",
        "logistics.parent-service.security.role-claims-path=realm_access.roles",
        "logistics.parent-service.security.role-prefix=APP_",
    ],
)
class WebFluxSecurityCustomPrefixIntegrationTest(
) {

    @LocalServerPort
    private var port: Int = 0

    private val webTestClient: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @Test
    fun `applies a custom role prefix in the active WebFlux bearer flow`() {
        webTestClient.get().uri("/protected")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.authorities[0]").isEqualTo("APP_dispatcher")
            .jsonPath("$.authorities[1]").isEqualTo("APP_planner")

        webTestClient.get().uri("/methods/custom-role")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
            .exchange()
            .expectStatus().isOk
        webTestClient.get().uri("/methods/custom-role")
            .header(HttpHeaders.AUTHORIZATION, "Bearer missing-role")
            .exchange()
            .expectStatus().isForbidden
    }
}

@SpringBootTest(
    classes = [WebFluxSecurityFixtureApplication::class],
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "logistics.parent-service.security.issuer=https://identity.example.test/realms/logistics",
        "logistics.parent-service.security.public-actuator-endpoints=false",
        "management.endpoints.web.base-path=/manage",
        "management.endpoints.web.exposure.include=health,info",
    ],
)
class WebFluxSecurityActuatorOptOutIntegrationTest(
) {

    @LocalServerPort
    private var port: Int = 0

    private val webTestClient: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @Test
    fun `protects actuator endpoints when their public default is disabled`() {
        webTestClient.get().uri("/manage/health").exchange().expectStatus().isUnauthorized
        webTestClient.get().uri("/manage/info").exchange().expectStatus().isUnauthorized
    }
}

@SpringBootTest(
    classes = [WebFluxApplicationOwnedSecurityFixtureApplication::class],
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = ["logistics.parent-service.security.role-claims-path=realm_access.roles"],
)
class WebFluxApplicationOwnedSecurityIntegrationTest(
) {

    @LocalServerPort
    private var port: Int = 0

    private val webTestClient: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @Autowired
    private lateinit var securityWebFilterChains: Map<String, SecurityWebFilterChain>

    @Test
    fun `backs off platform WebFlux security when the application supplies its own chain`() {
        assertThat(securityWebFilterChains).containsOnlyKeys("applicationSecurityWebFilterChain")
        webTestClient.get().uri("/methods/default-role")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
            .exchange()
            .expectStatus().isOk
        webTestClient.get().uri("/methods/default-role")
            .header(HttpHeaders.AUTHORIZATION, "Bearer missing-role")
            .exchange()
            .expectStatus().isForbidden
        webTestClient.get().uri("/methods/default-role").exchange().expectStatus().isUnauthorized
    }
}

@SpringBootTest(
    classes = [WebFluxSecurityDisabledFixtureApplication::class],
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = ["logistics.parent-service.security.enabled=false"],
)
class WebFluxSecurityDisabledMethodIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    private val webTestClient: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @Test
    fun `does not enforce annotated reactive methods when platform security is disabled`() {
        webTestClient.get().uri("/methods/default-role")
            .exchange()
            .expectStatus().isOk
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(WebFluxSecurityFixtureController::class, WebFluxMethodSecurityFixtureController::class, WebFluxMethodSecurityFixture::class)
class WebFluxSecurityFixtureApplication {

    @Bean
    fun reactiveJwtDecoder(): ReactiveJwtDecoder =
        ReactiveJwtDecoder { token ->
            when (token) {
                "trusted", "valid" -> Mono.just(
                    mockJwt(
                        tokenValue = token,
                        claims = mapOf("realm_access" to mapOf("roles" to listOf("dispatcher", "planner"))),
                    ),
                )
                "nested-string" -> Mono.just(
                    mockJwt(
                        tokenValue = token,
                        claims = mapOf("realm_access" to mapOf("roles" to "driver")),
                    ),
                )
                "missing-role" -> Mono.just(
                    mockJwt(
                        tokenValue = token,
                        claims = mapOf("realm_access" to mapOf("roles" to listOf("planner"))),
                    ),
                )
                "scope-only", "scope" -> Mono.just(
                    mockJwt(
                        tokenValue = token,
                        claims = mapOf("scope" to "shipments.read"),
                    ),
                )
                "scp" -> Mono.just(
                    mockJwt(
                        tokenValue = token,
                        claims = mapOf("scp" to listOf("shipments.read")),
                    ),
                )
                "missing-permission" -> Mono.just(
                    mockJwt(
                        tokenValue = token,
                        claims = mapOf("scope" to "shipments.write"),
                    ),
                )
                "invalid-signature" -> Mono.error(BadJwtException("invalid signature"))
                "expired" -> Mono.error(BadJwtException("token expired"))
                "issuer-mismatch" -> Mono.error(BadJwtException("issuer mismatch"))
                else -> Mono.error(BadJwtException("unknown test token"))
            }
        }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(WebFluxSecurityFixtureController::class, WebFluxMethodSecurityFixtureController::class, WebFluxMethodSecurityFixture::class)
class WebFluxApplicationOwnedSecurityFixtureApplication {

    @Bean
    fun reactiveJwtDecoder(): ReactiveJwtDecoder = WebFluxSecurityFixtureApplication().reactiveJwtDecoder()

    @Bean
    fun applicationSecurityWebFilterChain(
        http: ServerHttpSecurity,
        reactiveJwtDecoder: ReactiveJwtDecoder,
        properties: PlatformProperties,
    ): SecurityWebFilterChain {
        val authenticationConverter = PlatformJwtAuthenticationConverter(RoleClaimsAuthorityMapper(properties.security))

        return http
            .csrf { it.disable() }
            .authorizeExchange { it.anyExchange().authenticated() }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt ->
                    jwt.jwtDecoder(reactiveJwtDecoder)
                        .jwtAuthenticationConverter(ReactiveJwtAuthenticationConverterAdapter(authenticationConverter))
                }
            }
            .build()
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(WebFluxMethodSecurityFixtureController::class, WebFluxMethodSecurityFixture::class)
class WebFluxSecurityDisabledFixtureApplication {

    @Bean
    fun applicationSecurityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http.authorizeExchange { it.anyExchange().permitAll() }.build()
}

@RestController
class WebFluxSecurityFixtureController {

    @GetMapping("/public/ping")
    fun publicEndpoint(): Mono<Map<String, String>> = Mono.just(mapOf("status" to "ok"))

    @GetMapping("/protected")
    fun protectedEndpoint(authentication: Authentication?): Mono<Map<String, List<String>>> =
        Mono.just(mapOf("authorities" to authentication?.authorities?.mapNotNull { it.authority }.orEmpty()))
}

@RestController
class WebFluxMethodSecurityFixtureController(
    private val methodSecurityFixture: WebFluxMethodSecurityFixture,
) {

    @GetMapping("/methods/reactive/pre-delayed")
    fun preDelayed(): Mono<String> = methodSecurityFixture.preDelayed()

    @GetMapping("/methods/reactive/pre-empty")
    fun preEmpty(): Mono<String> = methodSecurityFixture.preEmpty()

    @GetMapping("/methods/reactive/post-scheduled")
    fun postScheduled(): Mono<String> = methodSecurityFixture.postScheduled()

    @GetMapping("/methods/default-role")
    fun defaultRole(): Mono<String> = methodSecurityFixture.defaultRole()

    @GetMapping("/methods/custom-role")
    fun customRole(): Mono<String> = methodSecurityFixture.customRole()

    @GetMapping("/methods/scope")
    fun scope(): Mono<String> = methodSecurityFixture.scope()
}

@Service
class WebFluxMethodSecurityFixture {

    @PreAuthorize("hasAuthority('ROLE_dispatcher')")
    fun preDelayed(): Mono<String> =
        Mono.delay(Duration.ofMillis(10)).thenReturn("pre-authorized")

    @PreAuthorize("hasAuthority('ROLE_dispatcher')")
    fun preEmpty(): Mono<String> = Mono.empty()

    @PostAuthorize("hasAuthority('ROLE_dispatcher')")
    fun postScheduled(): Mono<String> =
        Mono.deferContextual {
            ReactiveSecurityContextHolder.getContext()
                .map { securityContext ->
                    securityContext.authentication?.authorities.orEmpty()
                        .mapNotNull { it.authority }
                        .singleOrNull { it == "ROLE_dispatcher" }
                        ?: "missing-context"
                }
                .defaultIfEmpty("missing-context")
        }
            .subscribeOn(Schedulers.boundedElastic())

    @PreAuthorize("hasAuthority('ROLE_dispatcher')")
    fun defaultRole(): Mono<String> = Mono.just("default-role")

    @PreAuthorize("hasAuthority('APP_dispatcher')")
    fun customRole(): Mono<String> = Mono.just("custom-role")

    @PreAuthorize("hasAuthority('SCOPE_shipments.read')")
    fun scope(): Mono<String> = Mono.just("scope")
}
