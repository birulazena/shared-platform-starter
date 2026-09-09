package com.hz.logistics.parentservice.autoconfigure

import com.hz.logistics.parentservice.autoconfigure.errors.PlatformProblemDetailFactory
import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import com.hz.logistics.parentservice.autoconfigure.properties.PlatformProperties
import com.hz.logistics.parentservice.autoconfigure.security.mvc.PlatformMvcMethodSecurityAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.security.reactive.PlatformWebFluxMethodSecurityAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.context.annotation.Configuration

class AutoConfigurationSelectionTest {

    @Test
    fun `registers the shared candidates once in foundation before capability order`() {
        val imports = requireNotNull(javaClass.classLoader.getResourceAsStream(AUTO_CONFIGURATION_IMPORTS))
            .bufferedReader()
            .useLines { lines ->
                lines.map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toList()
            }

        assertThat(imports).containsExactly(
            "com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration",
            "com.hz.logistics.parentservice.autoconfigure.tracing.PlatformTracingAutoConfiguration",
            "com.hz.logistics.parentservice.autoconfigure.metrics.PlatformMetricsAutoConfiguration",
            MVC_SECURITY_AUTO_CONFIGURATION,
            MVC_METHOD_SECURITY_AUTO_CONFIGURATION,
            WEBFLUX_SECURITY_AUTO_CONFIGURATION,
            WEBFLUX_METHOD_SECURITY_AUTO_CONFIGURATION,
            MVC_ERROR_AUTO_CONFIGURATION,
            WEBFLUX_ERROR_AUTO_CONFIGURATION,
            LOGGING_AUTO_CONFIGURATION,
        )
    }

    @Test
    fun `keeps shared non-web defaults eligible without a web application`() {
        ApplicationContextRunner()
            .withUserConfiguration(AutoConfigurationTestApplication::class.java)
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(PlatformProperties::class.java)
                assertThat(context).hasSingleBean(PlatformCorrelationContext::class.java)
                assertThat(context).hasSingleBean(PlatformProblemDetailFactory::class.java)
                assertThat(isFullMatch(context, MVC_SECURITY_AUTO_CONFIGURATION)).isFalse()
                assertThat(isFullMatch(context, WEBFLUX_SECURITY_AUTO_CONFIGURATION)).isFalse()
                assertThat(isFullMatch(context, MVC_METHOD_SECURITY_AUTO_CONFIGURATION)).isFalse()
                assertThat(isFullMatch(context, WEBFLUX_METHOD_SECURITY_AUTO_CONFIGURATION)).isFalse()
            }
    }

    @Test
    fun `activates only the Servlet branch for an explicit Servlet application`() {
        WebApplicationContextRunner()
            .withUserConfiguration(AutoConfigurationTestApplication::class.java)
            .withPropertyValues(*platformProperties(), "spring.main.web-application-type=servlet")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(isFullMatch(context, MVC_SECURITY_AUTO_CONFIGURATION)).isTrue()
                assertThat(isFullMatch(context, WEBFLUX_SECURITY_AUTO_CONFIGURATION)).isFalse()
                assertThat(isFullMatch(context, MVC_METHOD_SECURITY_AUTO_CONFIGURATION)).isTrue()
                assertThat(isFullMatch(context, WEBFLUX_METHOD_SECURITY_AUTO_CONFIGURATION)).isFalse()
            }
    }

    @Test
    fun `activates only the Reactive branch for an explicit Reactive application`() {
        ReactiveWebApplicationContextRunner()
            .withUserConfiguration(AutoConfigurationTestApplication::class.java)
            .withPropertyValues(*platformProperties(), "spring.main.web-application-type=reactive")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(isFullMatch(context, MVC_SECURITY_AUTO_CONFIGURATION)).isFalse()
                assertThat(isFullMatch(context, WEBFLUX_SECURITY_AUTO_CONFIGURATION)).isTrue()
                assertThat(isFullMatch(context, MVC_METHOD_SECURITY_AUTO_CONFIGURATION)).isFalse()
                assertThat(isFullMatch(context, WEBFLUX_METHOD_SECURITY_AUTO_CONFIGURATION)).isTrue()
            }
    }

    @Test
    fun `uses the explicitly selected branch when both web APIs are available`() {
        WebApplicationContextRunner()
            .withUserConfiguration(AutoConfigurationTestApplication::class.java)
            .withPropertyValues(*platformProperties(), "spring.main.web-application-type=servlet")
            .run { servletContext ->
                assertThat(servletContext).hasNotFailed()
                assertThat(isFullMatch(servletContext, MVC_SECURITY_AUTO_CONFIGURATION)).isTrue()
                assertThat(isFullMatch(servletContext, WEBFLUX_SECURITY_AUTO_CONFIGURATION)).isFalse()
                assertThat(isFullMatch(servletContext, MVC_METHOD_SECURITY_AUTO_CONFIGURATION)).isTrue()
                assertThat(isFullMatch(servletContext, WEBFLUX_METHOD_SECURITY_AUTO_CONFIGURATION)).isFalse()
            }

        ReactiveWebApplicationContextRunner()
            .withUserConfiguration(AutoConfigurationTestApplication::class.java)
            .withPropertyValues(*platformProperties(), "spring.main.web-application-type=reactive")
            .run { reactiveContext ->
                assertThat(reactiveContext).hasNotFailed()
                assertThat(isFullMatch(reactiveContext, MVC_SECURITY_AUTO_CONFIGURATION)).isFalse()
                assertThat(isFullMatch(reactiveContext, WEBFLUX_SECURITY_AUTO_CONFIGURATION)).isTrue()
                assertThat(isFullMatch(reactiveContext, MVC_METHOD_SECURITY_AUTO_CONFIGURATION)).isFalse()
                assertThat(isFullMatch(reactiveContext, WEBFLUX_METHOD_SECURITY_AUTO_CONFIGURATION)).isTrue()
            }
    }

    @Test
    fun `backs off the selected MVC method branch when any required class is absent`() {
        MVC_METHOD_SECURITY_REQUIRED_CLASSES.forEach { requiredClass ->
            mvcMethodSecurityRunner()
                .withClassLoader(FilteredClassLoader(requiredClass))
                .run { context ->
                    assertThat(context).hasNotFailed()
                    assertThat(ConditionEvaluationReport.get(context.beanFactory).conditionAndOutcomesBySource)
                        .containsKey(MVC_METHOD_SECURITY_AUTO_CONFIGURATION)
                    assertThat(isFullMatch(context, MVC_METHOD_SECURITY_AUTO_CONFIGURATION))
                        .describedAs("MVC method security must back off without %s", requiredClass)
                        .isFalse()
                }
        }
    }

    @Test
    fun `backs off the selected WebFlux method branch when any required class is absent`() {
        WEBFLUX_METHOD_SECURITY_REQUIRED_CLASSES.forEach { requiredClass ->
            webFluxMethodSecurityRunner()
                .withClassLoader(FilteredClassLoader(requiredClass))
                .run { context ->
                    assertThat(context).hasNotFailed()
                    assertThat(ConditionEvaluationReport.get(context.beanFactory).conditionAndOutcomesBySource)
                        .containsKey(WEBFLUX_METHOD_SECURITY_AUTO_CONFIGURATION)
                    assertThat(isFullMatch(context, WEBFLUX_METHOD_SECURITY_AUTO_CONFIGURATION))
                        .describedAs("WebFlux method security must back off without %s", requiredClass)
                        .isFalse()
                }
        }
    }

    private fun isFullMatch(context: org.springframework.context.ConfigurableApplicationContext, source: String): Boolean =
        ConditionEvaluationReport.get(context.beanFactory)
            .conditionAndOutcomesBySource[source]
            ?.isFullMatch
            ?: false

    private fun mvcMethodSecurityRunner(): WebApplicationContextRunner =
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformMvcMethodSecurityAutoConfiguration::class.java))
            .withPropertyValues("logistics.parent-service.security.enabled=true")

    private fun webFluxMethodSecurityRunner(): ReactiveWebApplicationContextRunner =
        ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformWebFluxMethodSecurityAutoConfiguration::class.java))
            .withPropertyValues("logistics.parent-service.security.enabled=true")

    private fun platformProperties(): Array<String> = arrayOf(
        "logistics.parent-service.security.issuer=https://identity.example.test/realms/logistics",
        "logistics.parent-service.tracing.sampling-probability=1.0",
        "logistics.parent-service.metrics.common-tags.environment=selection-test",
    )

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    class AutoConfigurationTestApplication

    private companion object {
        const val AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        const val MVC_SECURITY_AUTO_CONFIGURATION =
            "com.hz.logistics.parentservice.autoconfigure.security.mvc.PlatformMvcSecurityAutoConfiguration"
        const val MVC_METHOD_SECURITY_AUTO_CONFIGURATION =
            "com.hz.logistics.parentservice.autoconfigure.security.mvc.PlatformMvcMethodSecurityAutoConfiguration"
        const val WEBFLUX_SECURITY_AUTO_CONFIGURATION =
            "com.hz.logistics.parentservice.autoconfigure.security.reactive.PlatformWebFluxSecurityAutoConfiguration"
        const val WEBFLUX_METHOD_SECURITY_AUTO_CONFIGURATION =
            "com.hz.logistics.parentservice.autoconfigure.security.reactive.PlatformWebFluxMethodSecurityAutoConfiguration"
        val MVC_METHOD_SECURITY_REQUIRED_CLASSES = listOf(
            "org.springframework.web.servlet.DispatcherServlet",
            "org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity",
            "org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor",
            "org.springframework.security.authorization.method.AuthorizationManagerAfterMethodInterceptor",
            "org.springframework.security.authorization.method.PreFilterAuthorizationMethodInterceptor",
            "org.springframework.security.authorization.method.PostFilterAuthorizationMethodInterceptor",
            "jakarta.annotation.security.RolesAllowed",
        )
        val WEBFLUX_METHOD_SECURITY_REQUIRED_CLASSES = listOf(
            "org.springframework.web.reactive.DispatcherHandler",
            "org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity",
            "org.springframework.security.authorization.method.AuthorizationManagerBeforeReactiveMethodInterceptor",
            "org.springframework.security.authorization.method.AuthorizationManagerAfterReactiveMethodInterceptor",
            "org.springframework.security.authorization.method.PreFilterAuthorizationReactiveMethodInterceptor",
            "org.springframework.security.authorization.method.PostFilterAuthorizationReactiveMethodInterceptor",
            "reactor.core.publisher.Mono",
        )
        const val MVC_ERROR_AUTO_CONFIGURATION =
            "com.hz.logistics.parentservice.autoconfigure.errors.mvc.PlatformMvcErrorAutoConfiguration"
        const val WEBFLUX_ERROR_AUTO_CONFIGURATION =
            "com.hz.logistics.parentservice.autoconfigure.errors.reactive.PlatformWebFluxErrorAutoConfiguration"
        const val LOGGING_AUTO_CONFIGURATION =
            "com.hz.logistics.parentservice.autoconfigure.logging.PlatformLoggingAutoConfiguration"
    }
}
