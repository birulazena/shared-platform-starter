package com.hz.logistics.parentservice.autoconfigure.security.reactive

import com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.errors.PlatformProblemDetailFactory
import com.hz.logistics.parentservice.autoconfigure.properties.PlatformProperties
import com.hz.logistics.parentservice.autoconfigure.security.IssuerValidation
import com.hz.logistics.parentservice.autoconfigure.security.PlatformJwtAuthenticationConverter
import com.hz.logistics.parentservice.autoconfigure.security.PublicEndpointPattern
import com.hz.logistics.parentservice.autoconfigure.security.RoleClaimsAuthorityMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.security.autoconfigure.actuate.web.reactive.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.core.convert.converter.Converter
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Reactive/WebFlux selection boundary for the platform security capability.
 *
 * Web-specific security types remain strings in the condition so an
 * application that did not select WebFlux never links reactive infrastructure.
 * The default reactive chain is added by the security implementation phase.
 */
@AutoConfiguration
@AutoConfigureAfter(PlatformAutoConfiguration::class)
@AutoConfigureBefore(
    name = ["org.springframework.boot.security.autoconfigure.web.reactive.ReactiveWebSecurityAutoConfiguration"],
)
@EnableWebFluxSecurity
@ConditionalOnClass(
    name = [
        "org.springframework.security.config.web.server.ServerHttpSecurity",
        "org.springframework.security.oauth2.jwt.ReactiveJwtDecoder",
        "org.springframework.security.web.server.SecurityWebFilterChain",
        "org.springframework.web.reactive.DispatcherHandler",
    ],
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(
    prefix = "logistics.parent-service.security",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class PlatformWebFluxSecurityAutoConfiguration {

    /** The Reactive counterpart of the secure MVC default, without session state. */
    @Bean
    @ConditionalOnMissingBean(SecurityWebFilterChain::class)
    fun platformWebFluxSecurityFilterChain(
        http: ServerHttpSecurity,
        properties: PlatformProperties,
        problemDetailFactory: PlatformProblemDetailFactory,
        jwtDecoderProvider: ObjectProvider<ReactiveJwtDecoder>,
        reactiveJwtAuthenticationConverterProvider: ObjectProvider<Converter<Jwt, Mono<AbstractAuthenticationToken>>>,
    ): SecurityWebFilterChain {
        val securityProperties = properties.security
        val publicEndpoints = PublicEndpointPattern.compileAll(securityProperties.publicEndpoints)
        val failureHandlers = PlatformWebFluxSecurityFailureHandlers(problemDetailFactory)
        val decoder = IssuerValidation.selectedReactiveJwtDecoder(securityProperties, jwtDecoderProvider.getIfAvailable())
        val platformConverter = PlatformJwtAuthenticationConverter(RoleClaimsAuthorityMapper(securityProperties))
        val applicationConverter = reactiveJwtAuthenticationConverterProvider.getIfAvailable()

        http
            .csrf { csrf -> csrf.disable() }
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .addFilterAfter(
                PlatformWebFluxPublicMethodSecurityDeniedFilter(publicEndpoints, failureHandlers.accessDeniedHandler),
                SecurityWebFiltersOrder.EXCEPTION_TRANSLATION,
            )
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(failureHandlers.authenticationEntryPoint)
                    .accessDeniedHandler(failureHandlers.accessDeniedHandler)
            }
            .authorizeExchange { exchanges ->
                exchanges.matchers(publicEndpointMatcher(publicEndpoints)).permitAll()
                if (securityProperties.publicActuatorEndpoints) {
                    exchanges.matchers(EndpointRequest.to("health", "info")).permitAll()
                }
                exchanges.anyExchange().authenticated()
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer
                    .authenticationEntryPoint(failureHandlers.authenticationEntryPoint)
                    .accessDeniedHandler(failureHandlers.accessDeniedHandler)
                    .jwt { jwt ->
                        jwt.jwtDecoder(decoder)
                        if (applicationConverter != null) {
                            jwt.jwtAuthenticationConverter(applicationConverter)
                        } else {
                            jwt.jwtAuthenticationConverter(ReactiveJwtAuthenticationConverterAdapter(platformConverter))
                        }
                    }
            }

        return http.build()
    }

    private fun publicEndpointMatcher(patterns: PublicEndpointPattern): ServerWebExchangeMatcher =
        ServerWebExchangeMatcher { exchange ->
            val applicationPath = exchange.request.path.pathWithinApplication().value()
            if (patterns.matches(applicationPath)) {
                ServerWebExchangeMatcher.MatchResult.match()
            } else {
                ServerWebExchangeMatcher.MatchResult.notMatch()
            }
        }
}

/**
 * Lets method-security denials on an explicitly public route reach the common
 * forbidden handler before the reactive exception-translation filter turns an
 * anonymous request into an authentication challenge. Protected routes still
 * propagate their denial to the normal 401/403 request-security handling.
 */
private class PlatformWebFluxPublicMethodSecurityDeniedFilter(
    private val publicEndpoints: PublicEndpointPattern,
    private val accessDeniedHandler: ServerAccessDeniedHandler,
) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> =
        chain.filter(exchange).onErrorResume(AccessDeniedException::class.java) { exception ->
            val path = exchange.request.path.pathWithinApplication().value()
            if (publicEndpoints.matches(path)) {
                accessDeniedHandler.handle(exchange, exception)
            } else {
                Mono.error(exception)
            }
        }
}
