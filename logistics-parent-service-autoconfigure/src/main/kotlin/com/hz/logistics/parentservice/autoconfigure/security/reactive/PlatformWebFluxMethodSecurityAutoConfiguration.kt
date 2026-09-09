package com.hz.logistics.parentservice.autoconfigure.security.reactive

import com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity

/**
 * Enables Spring Security's publisher-aware WebFlux method-authorization
 * infrastructure independently from the HTTP security filter chain.
 */
@AutoConfiguration
@AutoConfigureAfter(
    PlatformAutoConfiguration::class,
    PlatformWebFluxSecurityAutoConfiguration::class,
)
@ConditionalOnClass(
    name = [
        "org.springframework.web.reactive.DispatcherHandler",
        "org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity",
        "org.springframework.security.authorization.method.AuthorizationManagerBeforeReactiveMethodInterceptor",
        "org.springframework.security.authorization.method.AuthorizationManagerAfterReactiveMethodInterceptor",
        "org.springframework.security.authorization.method.PreFilterAuthorizationReactiveMethodInterceptor",
        "org.springframework.security.authorization.method.PostFilterAuthorizationReactiveMethodInterceptor",
        "reactor.core.publisher.Mono",
    ],
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(
    prefix = "logistics.parent-service.security",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@ConditionalOnMissingBean(
    name = [
        "_reactiveMethodSecurityConfiguration",
        "reactiveMethodSecurityConfiguration",
        "methodSecurityInterceptor",
    ],
)
@EnableReactiveMethodSecurity
class PlatformWebFluxMethodSecurityAutoConfiguration
