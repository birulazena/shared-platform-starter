package com.hz.logistics.parentservice.autoconfigure.metrics

import com.hz.logistics.parentservice.autoconfigure.properties.MetricsProperties
import io.micrometer.core.instrument.MeterRegistry

/**
 * Application-owned replacement point for the platform's metrics policy.
 *
 * Providing this bean backs off only the platform common-tag/policy
 * contribution. The [MeterRegistry] remains application-owned and all other
 * platform capabilities continue to be eligible independently. An
 * implementation must keep application metrics on Micrometer APIs and should
 * apply only bounded, non-sensitive tags.
 */
fun interface PlatformMetricsCustomizer {

    /** Apply an application metrics policy to the selected registry. */
    fun customize(registry: MeterRegistry, properties: MetricsProperties)

    companion object {
        /** A convenient no-op implementation for conditional test fixtures. */
        @JvmField
        val NO_OP: PlatformMetricsCustomizer = PlatformMetricsCustomizer { _, _ -> }
    }
}
