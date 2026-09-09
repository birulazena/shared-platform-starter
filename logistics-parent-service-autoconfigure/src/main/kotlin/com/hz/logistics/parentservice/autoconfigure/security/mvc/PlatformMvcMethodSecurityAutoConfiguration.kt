package com.hz.logistics.parentservice.autoconfigure.security.mvc

import com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

/**
 * Enables Spring Security's standard MVC method-authorization infrastructure
 * independently from the HTTP security filter chain.
 */
@AutoConfiguration
@AutoConfigureAfter(
    PlatformAutoConfiguration::class,
    PlatformMvcSecurityAutoConfiguration::class,
)
@ConditionalOnClass(
    name = [
        "org.springframework.web.servlet.DispatcherServlet",
        "org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity",
        "org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor",
        "org.springframework.security.authorization.method.AuthorizationManagerAfterMethodInterceptor",
        "org.springframework.security.authorization.method.PreFilterAuthorizationMethodInterceptor",
        "org.springframework.security.authorization.method.PostFilterAuthorizationMethodInterceptor",
        "jakarta.annotation.security.RolesAllowed",
    ],
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
    prefix = "logistics.parent-service.security",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@ConditionalOnMissingBean(
    name = [
        "_prePostMethodSecurityConfiguration",
        "_securedMethodSecurityConfiguration",
        "_jsr250MethodSecurityConfiguration",
    ],
)
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
class PlatformMvcMethodSecurityAutoConfiguration
