package com.hz.logistics.parentservice.autoconfigure.metrics

import com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration
import com.hz.logistics.parentservice.autoconfigure.properties.MetricsProperties
import com.hz.logistics.parentservice.autoconfigure.properties.PlatformProperties
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean

/**
 * Adds the platform's Micrometer policy without selecting a metrics backend.
 *
 * Registries remain application (or Spring Boot) owned.  The only platform
 * contribution is a [MeterRegistryCustomizer], so a service can keep its
 * existing registry and replace just the common-tag policy by declaring a
 * [PlatformMetricsCustomizer].
 */
@AutoConfiguration
@AutoConfigureAfter(PlatformAutoConfiguration::class)
@ConditionalOnProperty(
    prefix = "logistics.parent-service.metrics",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class PlatformMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PlatformMetricsCustomizer::class)
    fun platformMetricsPolicy(properties: PlatformProperties): PlatformMetricsCustomizer =
        PlatformMetricsPolicy(properties.metrics)

    /**
     * Spring Boot invokes this for every selected [MeterRegistry], including
     * registries supplied directly by an application.  It deliberately never
     * creates a registry or configures an OTLP/vendor metrics exporter.
     */
    @Bean
    fun platformMeterRegistryCustomizer(
        customizer: PlatformMetricsCustomizer,
        properties: PlatformProperties,
    ): MeterRegistryCustomizer<MeterRegistry> =
        MeterRegistryCustomizer { registry -> customizer.customize(registry, properties.metrics) }
}
