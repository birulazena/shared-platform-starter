package com.hz.logistics.parentservice.autoconfigure.security

import com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.errors.PlatformProblemDetailFactory
import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import com.hz.logistics.parentservice.autoconfigure.security.mvc.PlatformMvcSecurityAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.security.reactive.PlatformWebFluxSecurityAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.FilterChainProxy
import org.springframework.security.web.server.WebFilterChainProxy
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import jakarta.servlet.Filter
import java.lang.reflect.Proxy

class SecurityAutoConfigurationContextTest {

    @Test
    fun `creates only the selected stack security chain`() {
        mvcRunner().run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(SecurityFilterChain::class.java)
            assertThat(context).doesNotHaveBean(SecurityWebFilterChain::class.java)
        }

        webFluxRunner().run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(SecurityWebFilterChain::class.java)
            assertThat(context).doesNotHaveBean(SecurityFilterChain::class.java)
        }
    }

    @Test
    fun `creates MVC method security only for the selected Servlet stack`() {
        mvcRunner().run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasBean(MVC_METHOD_SECURITY_PRE_POST_SENTINEL)
            assertThat(context).doesNotHaveBean(REACTIVE_METHOD_SECURITY_SENTINEL)
            assertThat(context).doesNotHaveBean(LEGACY_REACTIVE_METHOD_SECURITY_CONFIGURATION)
            assertThat(context).doesNotHaveBean(LEGACY_METHOD_SECURITY_INTERCEPTOR)
        }
    }

    @Test
    fun `creates reactive method security only for the selected WebFlux stack`() {
        webFluxRunner().run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasBean(REACTIVE_METHOD_SECURITY_SENTINEL)
            assertThat(context).doesNotHaveBean(MVC_METHOD_SECURITY_PRE_POST_SENTINEL)
            assertThat(context).doesNotHaveBean(MVC_METHOD_SECURITY_SECURED_SENTINEL)
            assertThat(context).doesNotHaveBean(MVC_METHOD_SECURITY_JSR250_SENTINEL)
        }
    }

    @Test
    fun `fails selected stack startup without a valid issuer`() {
        mvcRunner().withoutIssuer().run { context ->
            assertThat(context).hasFailed()
        }

        webFluxRunner().withPropertyValues("logistics.parent-service.security.issuer=issuer.example.test").run { context ->
            assertThat(context).hasFailed()
        }
    }

    @Test
    fun `fails startup for invalid public patterns while platform security is active`() {
        mvcRunner()
            .withPropertyValues("logistics.parent-service.security.public-endpoints[0]=/public/**/health")
            .run { context ->
                assertThat(context).hasFailed()
            }
    }

    @Test
    fun `application owned chains back off only their matching platform branch`() {
        mvcRunner()
            .withUserConfiguration(MvcApplicationSecurityConfiguration::class.java)
            .withoutIssuer()
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(SecurityFilterChain::class.java)
                assertThat(context).hasBean("applicationSecurityFilterChain")
                assertThat(context).hasBean(MVC_METHOD_SECURITY_PRE_POST_SENTINEL)
                assertThat(isFullMatch(context, MVC_METHOD_SECURITY_AUTO_CONFIGURATION)).isTrue()
            }

        webFluxRunner()
            .withUserConfiguration(WebFluxApplicationSecurityConfiguration::class.java)
            .withoutIssuer()
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(SecurityWebFilterChain::class.java)
                assertThat(context).hasBean("applicationSecurityWebFilterChain")
                assertThat(context).hasBean(REACTIVE_METHOD_SECURITY_SENTINEL)
                assertThat(isFullMatch(context, WEBFLUX_METHOD_SECURITY_AUTO_CONFIGURATION)).isTrue()
            }
    }

    @Test
    fun `backs off MVC method security for an application owned matching enablement`() {
        mvcRunner()
            .withUserConfiguration(MvcApplicationSecurityConfiguration::class.java, MvcManualMethodSecurityConfiguration::class.java)
            .withoutIssuer()
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasBean(MVC_METHOD_SECURITY_PRE_POST_SENTINEL)
                assertThat(context).hasBean(MVC_METHOD_SECURITY_SECURED_SENTINEL)
                assertThat(context).hasBean(MVC_METHOD_SECURITY_JSR250_SENTINEL)
                assertThat(isFullMatch(context, MVC_METHOD_SECURITY_AUTO_CONFIGURATION)).isFalse()
            }
    }

    @Test
    fun `backs off MVC method security for every individual manual ownership sentinel`() {
        assertMvcMethodSecurityBacksOffFor(
            MVC_METHOD_SECURITY_PRE_POST_SENTINEL,
            MvcPrePostMethodSecuritySentinelConfiguration::class.java,
        )
        assertMvcMethodSecurityBacksOffFor(
            MVC_METHOD_SECURITY_SECURED_SENTINEL,
            MvcSecuredMethodSecuritySentinelConfiguration::class.java,
        )
        assertMvcMethodSecurityBacksOffFor(
            MVC_METHOD_SECURITY_JSR250_SENTINEL,
            MvcJsr250MethodSecuritySentinelConfiguration::class.java,
        )
    }

    @Test
    fun `backs off reactive authorization-manager method security for matching manual enablement`() {
        webFluxRunner()
            .withUserConfiguration(
                WebFluxApplicationSecurityConfiguration::class.java,
                WebFluxManualMethodSecurityConfiguration::class.java,
            )
            .withoutIssuer()
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasBean(REACTIVE_METHOD_SECURITY_SENTINEL)
                assertThat(isFullMatch(context, WEBFLUX_METHOD_SECURITY_AUTO_CONFIGURATION)).isFalse()
            }
    }

    @Test
    fun `backs off WebFlux method security for every executable manual ownership sentinel`() {
        assertWebFluxMethodSecurityBacksOffFor(
            REACTIVE_METHOD_SECURITY_SENTINEL,
            WebFluxAuthorizationManagerMethodSecuritySentinelConfiguration::class.java,
        )
        assertWebFluxMethodSecurityBacksOffFor(
            LEGACY_REACTIVE_METHOD_SECURITY_CONFIGURATION,
            WebFluxLegacyMethodSecurityConfigurationSentinelConfiguration::class.java,
        )
        assertWebFluxMethodSecurityBacksOffFor(
            LEGACY_METHOD_SECURITY_INTERCEPTOR,
            WebFluxLegacyMethodSecurityInterceptorSentinelConfiguration::class.java,
        )
    }

    @Test
    fun `reuses application decoders and authority converters without disabling default denial`() {
        mvcRunner()
            .withUserConfiguration(MvcDecoderAndConverterConfiguration::class.java)
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(JwtDecoder::class.java)
                assertThat(context).hasSingleBean(Converter::class.java)
                assertThat(context).hasSingleBean(SecurityFilterChain::class.java)

                val mockMvcBuilder: StandaloneMockMvcBuilder =
                    MockMvcBuilders.standaloneSetup(MvcSecurityContextController())
                mockMvcBuilder.addFilters<StandaloneMockMvcBuilder>(context.getBean(FilterChainProxy::class.java))
                val mockMvc = mockMvcBuilder.build()

                mockMvc.perform(get("/context-protected"))
                    .andExpect(status().isUnauthorized)
                mockMvc.perform(
                    get("/context-protected")
                        .header("Authorization", "Bearer application-token"),
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.authorities[0]").value("APPLICATION_MVC"))
            }

        webFluxRunner()
            .withUserConfiguration(WebFluxDecoderAndConverterConfiguration::class.java)
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(ReactiveJwtDecoder::class.java)
                assertThat(context).hasSingleBean(Converter::class.java)
                assertThat(context).hasSingleBean(SecurityWebFilterChain::class.java)

                val webTestClientSpec: WebTestClient.ControllerSpec =
                    WebTestClient.bindToController(WebFluxSecurityContextController())
                webTestClientSpec.webFilter<WebTestClient.ControllerSpec>(context.getBean(WebFilterChainProxy::class.java))
                val webTestClient = webTestClientSpec.build()

                webTestClient.get().uri("/context-protected").exchange()
                    .expectStatus().isUnauthorized
                webTestClient.get().uri("/context-protected")
                    .header("Authorization", "Bearer application-token")
                    .exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.authorities[0]").isEqualTo("APPLICATION_WEBFLUX")
            }
    }

    @Test
    fun `security disablement retains independent shared capabilities`() {
        mvcRunner()
            .withoutIssuer()
            .withPropertyValues("logistics.parent-service.security.enabled=false")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(SecurityFilterChain::class.java)
                assertThat(context).doesNotHaveBean(MVC_METHOD_SECURITY_PRE_POST_SENTINEL)
                assertThat(context).hasSingleBean(PlatformCorrelationContext::class.java)
                assertThat(context).hasSingleBean(PlatformProblemDetailFactory::class.java)
            }

        webFluxRunner()
            .withoutIssuer()
            .withPropertyValues("logistics.parent-service.security.enabled=false")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(SecurityWebFilterChain::class.java)
                assertThat(context).doesNotHaveBean(REACTIVE_METHOD_SECURITY_SENTINEL)
                assertThat(context).hasSingleBean(PlatformCorrelationContext::class.java)
                assertThat(context).hasSingleBean(PlatformProblemDetailFactory::class.java)
            }
    }

    private fun mvcRunner(): WebApplicationContextRunner =
        WebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PlatformAutoConfiguration::class.java,
                    PlatformMvcSecurityAutoConfiguration::class.java,
                    PlatformWebFluxSecurityAutoConfiguration::class.java,
                    *methodSecurityAutoConfigurations(),
                ),
            )
            .withPropertyValues(VALID_ISSUER)

    private fun webFluxRunner(): ReactiveWebApplicationContextRunner =
        ReactiveWebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PlatformAutoConfiguration::class.java,
                    PlatformMvcSecurityAutoConfiguration::class.java,
                    PlatformWebFluxSecurityAutoConfiguration::class.java,
                    *methodSecurityAutoConfigurations(),
                ),
            )
            .withPropertyValues(VALID_ISSUER)

    private fun WebApplicationContextRunner.withoutIssuer(): WebApplicationContextRunner =
        withPropertyValues("logistics.parent-service.security.issuer=")

    private fun ReactiveWebApplicationContextRunner.withoutIssuer(): ReactiveWebApplicationContextRunner =
        withPropertyValues("logistics.parent-service.security.issuer=")

    private fun methodSecurityAutoConfigurations(): Array<Class<*>> = arrayOf(
        Class.forName(MVC_METHOD_SECURITY_AUTO_CONFIGURATION),
        Class.forName(WEBFLUX_METHOD_SECURITY_AUTO_CONFIGURATION),
    )

    private fun assertMvcMethodSecurityBacksOffFor(sentinel: String, manualConfiguration: Class<*>) {
        mvcRunner()
            .withUserConfiguration(MvcApplicationSecurityConfiguration::class.java, manualConfiguration)
            .withoutIssuer()
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasBean(sentinel)
                assertThat(isFullMatch(context, MVC_METHOD_SECURITY_AUTO_CONFIGURATION)).isFalse()
            }
    }

    private fun assertWebFluxMethodSecurityBacksOffFor(sentinel: String, manualConfiguration: Class<*>) {
        webFluxRunner()
            .withUserConfiguration(WebFluxApplicationSecurityConfiguration::class.java, manualConfiguration)
            .withoutIssuer()
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasBean(sentinel)
                assertThat(isFullMatch(context, WEBFLUX_METHOD_SECURITY_AUTO_CONFIGURATION)).isFalse()
            }
    }

    private fun isFullMatch(
        context: org.springframework.context.ConfigurableApplicationContext,
        source: String,
    ): Boolean = ConditionEvaluationReport.get(context.beanFactory)
        .conditionAndOutcomesBySource[source]
        ?.isFullMatch
        ?: false

    @Configuration(proxyBeanMethods = false)
    class MvcApplicationSecurityConfiguration {

        @Bean
        fun applicationSecurityFilterChain(): SecurityFilterChain =
            Proxy.newProxyInstance(
                SecurityFilterChain::class.java.classLoader,
                arrayOf(SecurityFilterChain::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "matches" -> false
                    "getFilters" -> emptyList<Filter>()
                    else -> null
                }
            } as SecurityFilterChain
    }

    @Configuration(proxyBeanMethods = false)
    class WebFluxApplicationSecurityConfiguration {

        @Bean
        fun applicationSecurityWebFilterChain(): SecurityWebFilterChain = object : SecurityWebFilterChain {
            override fun matches(exchange: ServerWebExchange): Mono<Boolean> = Mono.just(false)

            override fun getWebFilters(): Flux<WebFilter> = Flux.empty()
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
    class MvcManualMethodSecurityConfiguration

    @Configuration(proxyBeanMethods = false)
    class MvcPrePostMethodSecuritySentinelConfiguration {

        @Bean(name = [MVC_METHOD_SECURITY_PRE_POST_SENTINEL])
        fun prePostMethodSecurityConfiguration(): Any = Any()
    }

    @Configuration(proxyBeanMethods = false)
    class MvcSecuredMethodSecuritySentinelConfiguration {

        @Bean(name = [MVC_METHOD_SECURITY_SECURED_SENTINEL])
        fun securedMethodSecurityConfiguration(): Any = Any()
    }

    @Configuration(proxyBeanMethods = false)
    class MvcJsr250MethodSecuritySentinelConfiguration {

        @Bean(name = [MVC_METHOD_SECURITY_JSR250_SENTINEL])
        fun jsr250MethodSecurityConfiguration(): Any = Any()
    }

    @Configuration(proxyBeanMethods = false)
    @EnableReactiveMethodSecurity
    class WebFluxManualMethodSecurityConfiguration

    @Configuration(proxyBeanMethods = false)
    class WebFluxAuthorizationManagerMethodSecuritySentinelConfiguration {

        @Bean(name = [REACTIVE_METHOD_SECURITY_SENTINEL])
        fun reactiveMethodSecurityConfiguration(): Any = Any()
    }

    @Configuration(proxyBeanMethods = false)
    class WebFluxLegacyMethodSecurityConfigurationSentinelConfiguration {

        @Bean(name = [LEGACY_REACTIVE_METHOD_SECURITY_CONFIGURATION])
        fun reactiveMethodSecurityConfiguration(): Any = Any()
    }

    @Configuration(proxyBeanMethods = false)
    class WebFluxLegacyMethodSecurityInterceptorSentinelConfiguration {

        @Bean(name = [LEGACY_METHOD_SECURITY_INTERCEPTOR])
        fun methodSecurityInterceptor(): Any = Any()
    }

    @Configuration(proxyBeanMethods = false)
    class MvcDecoderAndConverterConfiguration {

        @Bean
        fun applicationJwtDecoder(): JwtDecoder = JwtDecoder {
            com.hz.logistics.parentservice.autoconfigure.support.PlatformTestFixtures.mockJwt(tokenValue = it)
        }

        @Bean
        fun applicationJwtAuthenticationConverter(): Converter<Jwt, AbstractAuthenticationToken> =
            Converter { jwt ->
                org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                    jwt,
                    listOf(SimpleGrantedAuthority("APPLICATION_MVC")),
                )
            }
    }

    @Configuration(proxyBeanMethods = false)
    class WebFluxDecoderAndConverterConfiguration {

        @Bean
        fun applicationReactiveJwtDecoder(): ReactiveJwtDecoder =
            ReactiveJwtDecoder {
                Mono.just(
                    com.hz.logistics.parentservice.autoconfigure.support.PlatformTestFixtures.mockJwt(
                        tokenValue = it,
                    ),
                )
            }

        @Bean
        fun applicationReactiveJwtAuthenticationConverter(): Converter<Jwt, Mono<AbstractAuthenticationToken>> =
            Converter { jwt ->
                Mono.just(
                    org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                        jwt,
                        listOf(SimpleGrantedAuthority("APPLICATION_WEBFLUX")),
                    ),
                )
            }
    }

    @RestController
    private class MvcSecurityContextController {

        @GetMapping("/context-protected")
        fun protectedEndpoint(authentication: Authentication?): Map<String, List<String>> =
            mapOf("authorities" to authentication?.authorities?.mapNotNull { it.authority }.orEmpty())
    }

    @RestController
    private class WebFluxSecurityContextController {

        @GetMapping("/context-protected")
        fun protectedEndpoint(authentication: Authentication?): Mono<Map<String, List<String>>> =
            Mono.just(mapOf("authorities" to authentication?.authorities?.mapNotNull { it.authority }.orEmpty()))
    }

    private companion object {
        const val VALID_ISSUER =
            "logistics.parent-service.security.issuer=https://identity.example.test/realms/logistics"
        const val MVC_METHOD_SECURITY_AUTO_CONFIGURATION =
            "com.hz.logistics.parentservice.autoconfigure.security.mvc.PlatformMvcMethodSecurityAutoConfiguration"
        const val WEBFLUX_METHOD_SECURITY_AUTO_CONFIGURATION =
            "com.hz.logistics.parentservice.autoconfigure.security.reactive.PlatformWebFluxMethodSecurityAutoConfiguration"
        const val MVC_METHOD_SECURITY_PRE_POST_SENTINEL = "_prePostMethodSecurityConfiguration"
        const val MVC_METHOD_SECURITY_SECURED_SENTINEL = "_securedMethodSecurityConfiguration"
        const val MVC_METHOD_SECURITY_JSR250_SENTINEL = "_jsr250MethodSecurityConfiguration"
        const val REACTIVE_METHOD_SECURITY_SENTINEL = "_reactiveMethodSecurityConfiguration"
        const val LEGACY_REACTIVE_METHOD_SECURITY_CONFIGURATION = "reactiveMethodSecurityConfiguration"
        const val LEGACY_METHOD_SECURITY_INTERCEPTOR = "methodSecurityInterceptor"
    }
}
