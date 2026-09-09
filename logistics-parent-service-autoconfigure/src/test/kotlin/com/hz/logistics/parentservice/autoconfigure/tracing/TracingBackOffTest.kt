package com.hz.logistics.parentservice.autoconfigure.tracing

import com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.errors.PlatformProblemDetailFactory
import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import com.hz.logistics.parentservice.autoconfigure.logging.PlatformLogSanitizer
import com.hz.logistics.parentservice.autoconfigure.metrics.PlatformMetricsCustomizer
import com.hz.logistics.parentservice.autoconfigure.logging.PlatformLoggingAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.metrics.PlatformMetricsAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.security.mvc.PlatformMvcSecurityAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.security.reactive.PlatformWebFluxSecurityAutoConfiguration
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.trace.SdkTracerProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.web.SecurityFilterChain

class TracingBackOffTest {

    @Test
    fun `tracing disablement leaves the shared correlation and error contracts eligible`() {
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PlatformAutoConfiguration::class.java,
                    PlatformTracingAutoConfiguration::class.java,
                    PlatformMetricsAutoConfiguration::class.java,
                    PlatformLoggingAutoConfiguration::class.java,
                ),
            )
            .withPropertyValues("logistics.parent-service.tracing.enabled=false")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(PlatformCorrelationContext::class.java)
                assertThat(context).hasSingleBean(PlatformProblemDetailFactory::class.java)
                assertThat(context).hasSingleBean(PlatformMetricsCustomizer::class.java)
                assertThat(context).hasSingleBean(PlatformLogSanitizer::class.java)
                assertThat(tracingConfigurationMatched(context.beanFactory)).isFalse()
            }
    }

    @Test
    fun `tracing disablement leaves MVC security eligible`() {
        WebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PlatformAutoConfiguration::class.java,
                    PlatformTracingAutoConfiguration::class.java,
                    PlatformMetricsAutoConfiguration::class.java,
                    PlatformLoggingAutoConfiguration::class.java,
                    PlatformMvcSecurityAutoConfiguration::class.java,
                    PlatformWebFluxSecurityAutoConfiguration::class.java,
                ),
            )
            .withPropertyValues(
                VALID_ISSUER,
                "logistics.parent-service.tracing.enabled=false",
                "logistics.parent-service.metrics.enabled=true",
                "logistics.parent-service.errors.enabled=true",
                "logistics.parent-service.logging.enabled=true",
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(SecurityFilterChain::class.java)
                assertThat(context).hasSingleBean(PlatformProblemDetailFactory::class.java)
                assertThat(context).hasSingleBean(PlatformMetricsCustomizer::class.java)
                assertThat(context).hasSingleBean(PlatformLogSanitizer::class.java)
                assertThat(tracingConfigurationMatched(context.beanFactory)).isFalse()
            }
    }

    @Test
    fun `an application owned OpenTelemetry instance backs off only platform tracing`() {
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PlatformAutoConfiguration::class.java,
                    PlatformTracingAutoConfiguration::class.java,
                    PlatformMetricsAutoConfiguration::class.java,
                    PlatformLoggingAutoConfiguration::class.java,
                ),
            )
            .withUserConfiguration(ApplicationOwnedOpenTelemetry::class.java)
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(OpenTelemetry::class.java)
                assertThat(context).hasSingleBean(PlatformProblemDetailFactory::class.java)
                assertThat(context).hasSingleBean(PlatformMetricsCustomizer::class.java)
                assertThat(context).hasSingleBean(PlatformLogSanitizer::class.java)
                assertThat(context).doesNotHaveBean("platformW3cContextPropagators")
            }
    }

    @Test
    fun `an application owned tracer provider also backs off only platform tracing`() {
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PlatformAutoConfiguration::class.java,
                    PlatformTracingAutoConfiguration::class.java,
                    PlatformMetricsAutoConfiguration::class.java,
                    PlatformLoggingAutoConfiguration::class.java,
                ),
            )
            .withUserConfiguration(ApplicationOwnedTracerProvider::class.java)
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(SdkTracerProvider::class.java)
                assertThat(context).hasSingleBean(PlatformProblemDetailFactory::class.java)
                assertThat(context).doesNotHaveBean("platformW3cContextPropagators")
            }
    }

    private fun tracingConfigurationMatched(beanFactory: ConfigurableListableBeanFactory): Boolean =
        ConditionEvaluationReport.get(beanFactory)
            .conditionAndOutcomesBySource[TRACING_AUTO_CONFIGURATION]
            ?.isFullMatch
            ?: false

    @Configuration(proxyBeanMethods = false)
    class ApplicationOwnedOpenTelemetry {

        @Bean
        fun applicationOpenTelemetry(): OpenTelemetry = OpenTelemetry.noop()
    }

    @Configuration(proxyBeanMethods = false)
    class ApplicationOwnedTracerProvider {

        @Bean
        fun applicationTracerProvider(): SdkTracerProvider = SdkTracerProvider.builder().build()
    }

    private companion object {
        const val VALID_ISSUER =
            "logistics.parent-service.security.issuer=https://identity.example.test/realms/logistics"
        const val TRACING_AUTO_CONFIGURATION =
            "com.hz.logistics.parentservice.autoconfigure.tracing.PlatformTracingAutoConfiguration"
    }
}
