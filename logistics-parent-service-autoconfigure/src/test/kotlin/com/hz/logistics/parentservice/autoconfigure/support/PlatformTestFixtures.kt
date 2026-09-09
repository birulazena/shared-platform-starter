package com.hz.logistics.parentservice.autoconfigure.support

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant

/** Reusable building blocks shared by context and integration test suites. */
object PlatformTestFixtures {

    fun contextRunner(vararg configurations: Class<*>): ApplicationContextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(*configurations))

    fun mvcContextRunner(vararg configurations: Class<*>): WebApplicationContextRunner =
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(*configurations))

    fun webFluxContextRunner(vararg configurations: Class<*>): ReactiveWebApplicationContextRunner =
        ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(*configurations))

    /** Build a deterministic JWT with valid time and issuer claims. */
    fun mockJwt(
        subject: String = "fixture-user",
        issuer: String = DEFAULT_ISSUER,
        tokenValue: String = DEFAULT_TOKEN,
        claims: Map<String, Any?> = emptyMap(),
        now: Instant = FIXTURE_TIME,
    ): Jwt {
        val builder = Jwt.withTokenValue(tokenValue)
            .header("alg", "none")
            .subject(subject)
            .issuer(issuer)
            .issuedAt(now.minusSeconds(60))
            .expiresAt(now.plusSeconds(300))
        claims.forEach { (name, value) ->
            // Spring Security 7.1 declares JWT claim values as non-null.
            // A null fixture value is represented by an absent claim, which
            // matches the platform's expected authorization outcome.
            value?.let { builder.claim(name, it) }
        }
        return builder.build()
    }

    fun mockJwtAuthentication(
        jwt: Jwt = mockJwt(),
        authorities: Collection<GrantedAuthority> = emptyList(),
    ): JwtAuthenticationToken = JwtAuthenticationToken(jwt, authorities.toList())

    fun authorities(vararg roles: String): List<GrantedAuthority> =
        roles.map(::SimpleGrantedAuthority)

    /**
     * Represent the two Spring-managed client builders used by acceptance
     * fixtures. The builders are supplied by the application context; this
     * helper deliberately does not construct an unmanaged client directly.
     */
    data class ManagedClientBuilders(
        val restClient: RestClient.Builder,
        val webClient: WebClient.Builder,
    )

    fun managedClientBuilders(
        restClientBuilder: RestClient.Builder,
        webClientBuilder: WebClient.Builder,
    ): ManagedClientBuilders = ManagedClientBuilders(restClientBuilder, webClientBuilder)

    fun simpleMeterRegistry(): SimpleMeterRegistry = SimpleMeterRegistry()

    fun controlledOtlpCollector(): ControlledOtlpCollector = ControlledOtlpCollector().start()

    const val DEFAULT_ISSUER = "https://identity.example.test/realms/logistics"
    const val DEFAULT_TOKEN = "fixture-token"
    val FIXTURE_TIME: Instant = Instant.parse("2026-01-01T00:00:00Z")
}

/** Top-level aliases keep fixtures concise in Kotlin test classes. */
fun mockJwt(
    subject: String = "fixture-user",
    issuer: String = PlatformTestFixtures.DEFAULT_ISSUER,
    tokenValue: String = PlatformTestFixtures.DEFAULT_TOKEN,
    claims: Map<String, Any?> = emptyMap(),
): Jwt = PlatformTestFixtures.mockJwt(subject, issuer, tokenValue, claims)

fun simpleMeterRegistry(): SimpleMeterRegistry = PlatformTestFixtures.simpleMeterRegistry()
