package com.hz.logistics.parentservice.autoconfigure.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import kotlin.reflect.KClass

class MethodSecurityAutoConfigurationAnnotationTest {

    @Test
    fun `declares the exact MVC auto-configuration contract`() {
        val configuration = load(MVC_CONFIGURATION)

        assertThat(configuration).hasAnnotation(AutoConfiguration::class.java)
        assertThat(configuration.getAnnotation(ConditionalOnWebApplication::class.java).type)
            .isEqualTo(ConditionalOnWebApplication.Type.SERVLET)
        assertSecurityPropertyCondition(configuration)
        assertThat(configuration.getAnnotation(ConditionalOnClass::class.java).name)
            .containsExactly(*MVC_REQUIRED_CLASSES)
        assertAfter(configuration, PlatformAutoConfigurationNames.MVC_SECURITY)
        assertThat(configuration.getAnnotation(ConditionalOnMissingBean::class.java).name)
            .containsExactly(*MVC_METHOD_SECURITY_SENTINELS)

        val enablement = configuration.getAnnotation(EnableMethodSecurity::class.java)
        assertThat(enablement).isNotNull
        assertThat(enablement.securedEnabled).isTrue
        assertThat(enablement.jsr250Enabled).isTrue
    }

    @Test
    fun `declares the exact WebFlux auto-configuration contract`() {
        val configuration = load(WEBFLUX_CONFIGURATION)

        assertThat(configuration).hasAnnotation(AutoConfiguration::class.java)
        assertThat(configuration.getAnnotation(ConditionalOnWebApplication::class.java).type)
            .isEqualTo(ConditionalOnWebApplication.Type.REACTIVE)
        assertSecurityPropertyCondition(configuration)
        assertThat(configuration.getAnnotation(ConditionalOnClass::class.java).name)
            .containsExactly(*WEBFLUX_REQUIRED_CLASSES)
        assertAfter(configuration, PlatformAutoConfigurationNames.WEBFLUX_SECURITY)
        assertThat(configuration.getAnnotation(ConditionalOnMissingBean::class.java).name)
            .containsExactly(*WEBFLUX_METHOD_SECURITY_SENTINELS)

        assertThat(configuration.getAnnotation(EnableReactiveMethodSecurity::class.java)).isNotNull
    }

    private fun assertSecurityPropertyCondition(configuration: Class<*>) {
        val condition = configuration.getAnnotation(ConditionalOnProperty::class.java)

        assertThat(condition.prefix).isEqualTo("logistics.parent-service.security")
        assertThat(condition.name).containsExactly("enabled")
        assertThat(condition.havingValue).isEqualTo("true")
        assertThat(condition.matchIfMissing).isTrue
    }

    private fun assertAfter(configuration: Class<*>, matchingWebSecurityConfiguration: String) {
        val after = configuration.getAnnotation(AutoConfigureAfter::class.java)

        assertThat(after.value.map(KClass<*>::javaName))
            .containsExactly(
                PlatformAutoConfigurationNames.PLATFORM,
                matchingWebSecurityConfiguration,
            )
        assertThat(after.name).isEmpty()
    }

    private fun load(name: String): Class<*> =
        runCatching { Class.forName(name) }
            .getOrElse { throw AssertionError("Expected production auto-configuration $name", it) }

    private object PlatformAutoConfigurationNames {
        const val PLATFORM = "com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration"
        const val MVC_SECURITY =
            "com.hz.logistics.parentservice.autoconfigure.security.mvc.PlatformMvcSecurityAutoConfiguration"
        const val WEBFLUX_SECURITY =
            "com.hz.logistics.parentservice.autoconfigure.security.reactive.PlatformWebFluxSecurityAutoConfiguration"
    }

    private companion object {
        const val MVC_CONFIGURATION =
            "com.hz.logistics.parentservice.autoconfigure.security.mvc.PlatformMvcMethodSecurityAutoConfiguration"
        const val WEBFLUX_CONFIGURATION =
            "com.hz.logistics.parentservice.autoconfigure.security.reactive.PlatformWebFluxMethodSecurityAutoConfiguration"

        val MVC_REQUIRED_CLASSES = arrayOf(
            "org.springframework.web.servlet.DispatcherServlet",
            "org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity",
            "org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor",
            "org.springframework.security.authorization.method.AuthorizationManagerAfterMethodInterceptor",
            "org.springframework.security.authorization.method.PreFilterAuthorizationMethodInterceptor",
            "org.springframework.security.authorization.method.PostFilterAuthorizationMethodInterceptor",
            "jakarta.annotation.security.RolesAllowed",
        )

        val WEBFLUX_REQUIRED_CLASSES = arrayOf(
            "org.springframework.web.reactive.DispatcherHandler",
            "org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity",
            "org.springframework.security.authorization.method.AuthorizationManagerBeforeReactiveMethodInterceptor",
            "org.springframework.security.authorization.method.AuthorizationManagerAfterReactiveMethodInterceptor",
            "org.springframework.security.authorization.method.PreFilterAuthorizationReactiveMethodInterceptor",
            "org.springframework.security.authorization.method.PostFilterAuthorizationReactiveMethodInterceptor",
            "reactor.core.publisher.Mono",
        )

        val MVC_METHOD_SECURITY_SENTINELS = arrayOf(
            "_prePostMethodSecurityConfiguration",
            "_securedMethodSecurityConfiguration",
            "_jsr250MethodSecurityConfiguration",
        )

        val WEBFLUX_METHOD_SECURITY_SENTINELS = arrayOf(
            "_reactiveMethodSecurityConfiguration",
            "reactiveMethodSecurityConfiguration",
            "methodSecurityInterceptor",
        )
    }
}

private val <T : Any> KClass<T>.javaName: String
    get() = java.name
