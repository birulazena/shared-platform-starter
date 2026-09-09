package com.hz.logistics.parentservice.autoconfigure

import com.hz.logistics.parentservice.autoconfigure.errors.PlatformProblemDetailFactory
import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import com.hz.logistics.parentservice.autoconfigure.properties.PlatformProperties
import io.micrometer.tracing.Tracer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered

/**
 * Non-web foundation for the shared platform.
 *
 * This configuration deliberately references no Servlet, MVC, WebFlux, or
 * reactive type. Stack-specific auto-configurations can therefore be selected
 * later by Spring Boot without linking an unselected web model. The capability
 * `enabled` flags live on the independently bound property groups and are
 * consumed by each capability's own conditional configuration.
 */
@AutoConfiguration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@AutoConfigureBefore(
    name = [
        "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.web.reactive.ReactiveWebSecurityAutoConfiguration",
    ],
)
@EnableConfigurationProperties(PlatformProperties::class)
class PlatformAutoConfiguration {

    /**
     * Provides correlation to non-web consumers as well as future web error
     * adapters. A consumer-owned tracer is reused when available.
     */
    @Bean
    @ConditionalOnMissingBean
    fun platformCorrelationContext(tracerProvider: ObjectProvider<Tracer>): PlatformCorrelationContext =
        PlatformCorrelationContext(tracerProvider)

    /**
     * The shared factory is eligible outside web applications. Stack-specific
     * handlers added later write its representation and may back off when the
     * application supplies its own compatible factory.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "logistics.parent-service.errors",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    @ConditionalOnMissingBean
    fun platformProblemDetailFactory(
        correlationContext: PlatformCorrelationContext,
        properties: PlatformProperties,
    ): PlatformProblemDetailFactory = PlatformProblemDetailFactory(correlationContext, properties.errors)
}
