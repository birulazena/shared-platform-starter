package com.hz.logistics.parentservice.autoconfigure.security.mvc

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
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * Servlet/MVC selection boundary for the platform security capability.
 *
 * Web-specific security types remain strings in the condition so an
 * application that did not select MVC never links Servlet infrastructure.
 * The default MVC chain is added by the security implementation phase.
 */
@AutoConfiguration
@AutoConfigureAfter(PlatformAutoConfiguration::class)
@AutoConfigureBefore(
    name = ["org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration"],
)
@EnableWebSecurity
@ConditionalOnClass(
    name = [
        "org.springframework.security.config.annotation.web.builders.HttpSecurity",
        "org.springframework.security.oauth2.jwt.JwtDecoder",
        "org.springframework.security.web.SecurityFilterChain",
        "org.springframework.web.servlet.DispatcherServlet",
    ],
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
    prefix = "logistics.parent-service.security",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class PlatformMvcSecurityAutoConfiguration {

    /**
     * Secures every MVC request unless it is explicitly public. The condition is
     * deliberately on the chain rather than this configuration class: when an
     * application owns a complete chain, no issuer or platform infrastructure is
     * required for this branch.
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain::class)
    fun platformMvcSecurityFilterChain(
        http: HttpSecurity,
        properties: PlatformProperties,
        problemDetailFactory: PlatformProblemDetailFactory,
        jwtDecoderProvider: ObjectProvider<JwtDecoder>,
        jwtAuthenticationConverterProvider: ObjectProvider<Converter<Jwt, AbstractAuthenticationToken>>,
    ): SecurityFilterChain {
        val securityProperties = properties.security
        val publicEndpoints = PublicEndpointPattern.compileAll(securityProperties.publicEndpoints)
        val failureHandlers = PlatformMvcSecurityFailureHandlers(problemDetailFactory)
        val decoder = IssuerValidation.selectedJwtDecoder(securityProperties, jwtDecoderProvider.getIfAvailable())
        val authenticationConverter = jwtAuthenticationConverterProvider.getIfAvailable()
            ?: PlatformJwtAuthenticationConverter(RoleClaimsAuthorityMapper(securityProperties))

        http
            .csrf { csrf -> csrf.disable() }
            .sessionManagement { sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(failureHandlers.authenticationEntryPoint)
                    .accessDeniedHandler(failureHandlers.accessDeniedHandler)
            }
            .authorizeHttpRequests { requests ->
                requests.requestMatchers(publicEndpointMatcher(publicEndpoints)).permitAll()
                if (securityProperties.publicActuatorEndpoints) {
                    requests.requestMatchers(EndpointRequest.to("health", "info")).permitAll()
                }
                requests.anyRequest().authenticated()
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer
                    .authenticationEntryPoint(failureHandlers.authenticationEntryPoint)
                    .accessDeniedHandler(failureHandlers.accessDeniedHandler)
                    .jwt { jwt ->
                        jwt.decoder(decoder).jwtAuthenticationConverter(authenticationConverter)
                    }
            }

        return http.build()
    }

    private fun publicEndpointMatcher(patterns: PublicEndpointPattern): RequestMatcher = RequestMatcher { request ->
        val requestUri = request.requestURI.orEmpty()
        val applicationPath = requestUri.removePrefix(request.contextPath.orEmpty()).ifEmpty { "/" }
        patterns.matches(applicationPath)
    }
}
