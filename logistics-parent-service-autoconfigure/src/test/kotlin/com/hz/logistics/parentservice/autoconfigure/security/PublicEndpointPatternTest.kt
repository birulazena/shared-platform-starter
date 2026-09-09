package com.hz.logistics.parentservice.autoconfigure.security

import com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.security.mvc.PlatformMvcSecurityAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.security.reactive.PlatformWebFluxSecurityAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner

class PublicEndpointPatternTest {

    private lateinit var mvcRunner: WebApplicationContextRunner
    private lateinit var webFluxRunner: ReactiveWebApplicationContextRunner

    @BeforeEach
    fun setUp() {
        mvcRunner = WebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PlatformAutoConfiguration::class.java,
                    PlatformMvcSecurityAutoConfiguration::class.java,
                ),
            )
            .withPropertyValues(VALID_ISSUER)
        webFluxRunner = ReactiveWebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PlatformAutoConfiguration::class.java,
                    PlatformWebFluxSecurityAutoConfiguration::class.java,
                ),
            )
            .withPropertyValues(VALID_ISSUER)
    }

    @Test
    fun `matches exact literals question marks and segment stars`() {
        val patterns = PublicEndpointPattern.compileAll(
            listOf(
                "/status",
                "/docs/v?/index",
                "/assets/*.json",
            ),
        )

        assertThat(patterns.matches("/status")).isTrue()
        assertThat(patterns.matches("/docs/v1/index")).isTrue()
        assertThat(patterns.matches("/docs/v2/index")).isTrue()
        assertThat(patterns.matches("/assets/platform.json")).isTrue()
        assertThat(patterns.matches("/assets/.json")).isTrue()

        assertThat(patterns.matches("/status/extra")).isFalse()
        assertThat(patterns.matches("/docs/v10/index")).isFalse()
        assertThat(patterns.matches("/assets/releases/platform.json")).isFalse()
        assertThat(patterns.matches("/assets/platform.yaml")).isFalse()
    }

    @Test
    fun `terminal double star matches zero or more complete trailing segments`() {
        val patterns = PublicEndpointPattern.compileAll(listOf("/public/**"))

        assertThat(patterns.matches("/public")).isTrue()
        assertThat(patterns.matches("/public/")).isTrue()
        assertThat(patterns.matches("/public/images/logo.svg")).isTrue()
        assertThat(patterns.matches("/published")).isFalse()
    }

    @Test
    fun `matches application paths independently of query strings and fragments`() {
        val patterns = PublicEndpointPattern.compileAll(listOf("/docs/*.json"))

        assertThat(patterns.matches("/docs/openapi.json?download=true")).isTrue()
        assertThat(patterns.matches("/docs/openapi.json#section")).isTrue()
        assertThat(patterns.matches("/docs/openapi.yaml?download=true")).isFalse()
    }

    @Test
    fun `overlapping permits are a union and list order does not matter`() {
        val first = PublicEndpointPattern.compileAll(listOf("/public/**", "/public/health"))
        val second = PublicEndpointPattern.compileAll(listOf("/public/health", "/public/**"))
        val paths = listOf("/public/health", "/public/assets/logo.svg", "/private/health")

        paths.forEach { path ->
            assertThat(first.matches(path)).isEqualTo(second.matches(path))
        }
        assertThat(first.matches("/public/health")).isTrue()
        assertThat(first.matches("/public/assets/logo.svg")).isTrue()
        assertThat(first.matches("/private/health")).isFalse()
    }

    @ParameterizedTest(name = "rejects invalid public pattern [{0}]")
    @ValueSource(
        strings = [
            "status",
            "",
            "/",
            "//status",
            "/status//health",
            "/status/../health",
            "/status/%2Fhealth",
            "/status/%2fhealth",
            "/orders/{id}",
            "/orders/{id:[0-9]+}",
            "/public/**/health",
            "/public/**suffix",
        ],
    )
    fun `rejects patterns outside the public grammar`(pattern: String) {
        assertThatIllegalArgumentException()
            .isThrownBy { PublicEndpointPattern.compileAll(listOf(pattern)) }
    }

    @ParameterizedTest(name = "fails active MVC security startup for [{0}]")
    @ValueSource(
        strings = [
            "status",
            "",
            "/",
            "//status",
            "/status//health",
            "/status/./health",
            "/status/../health",
            "/status/%2Fhealth",
            "/status\\health",
            "/orders/{id}",
            "/orders/{id:[0-9]+}",
            "/public/**/health",
            "/public/**suffix",
        ],
    )
    fun `fails active MVC security startup for every prohibited grammar category`(pattern: String) {
        mvcRunner
            .withPropertyValues("logistics.parent-service.security.public-endpoints[0]=$pattern")
            .run { context -> assertThat(context).hasFailed() }
    }

    @ParameterizedTest(name = "fails active WebFlux security startup for [{0}]")
    @ValueSource(
        strings = [
            "status",
            "",
            "/",
            "//status",
            "/status//health",
            "/status/./health",
            "/status/../health",
            "/status/%2Fhealth",
            "/status\\health",
            "/orders/{id}",
            "/orders/{id:[0-9]+}",
            "/public/**/health",
            "/public/**suffix",
        ],
    )
    fun `fails active WebFlux security startup for every prohibited grammar category`(pattern: String) {
        webFluxRunner
            .withPropertyValues("logistics.parent-service.security.public-endpoints[0]=$pattern")
            .run { context -> assertThat(context).hasFailed() }
    }

    private companion object {
        const val VALID_ISSUER =
            "logistics.parent-service.security.issuer=https://identity.example.test/realms/logistics"
    }
}
