package com.hz.logistics.parentservice.autoconfigure.metrics

import com.hz.logistics.parentservice.autoconfigure.errors.PlatformProblemDetailFactory
import com.hz.logistics.parentservice.autoconfigure.observability.PlatformCorrelationContext
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(
    classes = [MvcMetricsFixtureApplication::class],
    properties = [
        "logistics.parent-service.security.enabled=false",
        "logistics.parent-service.metrics.common-tags.service=shipment-api",
        "logistics.parent-service.metrics.common-tags.environment=integration",
        "management.otlp.metrics.export.enabled=false",
    ],
)
@AutoConfigureMockMvc
class MvcMetricsIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var registry: MeterRegistry

    @Test
    fun recordsCounterTimerAndGaugeWithSafeCommonTags() {
        mockMvc.perform(get("/metrics/record")).andExpect(status().isOk)

        assertThat(registry.find("shipments.processed").counter()).isNotNull
        assertThat(registry.find("shipments.processing").timer()).isNotNull
        assertThat(registry.find("shipments.in-flight").gauge()).isNotNull

        listOf("shipments.processed", "shipments.processing", "shipments.in-flight").forEach { name ->
            val tags = registry.find(name).meters()
                .single()
                .id
                .tags
                .associate { tag -> tag.key to tag.value }
            assertThat(tags)
                .containsEntry("service", "shipment-api")
                .containsEntry("environment", "integration")
                .doesNotContainKeys("authorization", "token", "password", "customerEmail")
        }
    }

    @Test
    fun reusesTheApplicationSelectedRegistryInsteadOfCreatingAnotherRegistry() {
        mockMvc.perform(get("/metrics/record")).andExpect(status().isOk)

        assertThat(registry).isInstanceOf(SimpleMeterRegistry::class.java)
        assertThat(registry.find("shipments.processed").counter()?.count()).isEqualTo(1.0)
    }
}

@SpringBootTest(
    classes = [MvcMetricsCustomizerFixtureApplication::class],
    properties = [
        "logistics.parent-service.security.enabled=false",
        "logistics.parent-service.metrics.common-tags.service=platform-default",
        "management.otlp.metrics.export.enabled=false",
    ],
)
@AutoConfigureMockMvc
class MvcMetricsCustomizerBackOffIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var registry: MeterRegistry

    @Autowired
    private lateinit var customizer: PlatformMetricsCustomizer

    @Autowired
    private lateinit var correlationContext: PlatformCorrelationContext

    @Autowired
    private lateinit var problemDetailFactory: PlatformProblemDetailFactory

    @Test
    fun applicationMetricsPolicyBacksOffOnlyMetricsContribution() {
        mockMvc.perform(get("/metrics/record")).andExpect(status().isOk)

        assertThat(customizer).isSameAs(MvcMetricsCustomizerFixtureApplication.APPLICATION_POLICY)
        assertThat(registry.find("shipments.processed").counter()).isNotNull
        assertThat(registry.find("shipments.processed").counter()?.id?.tags)
            .noneMatch { it.key == "service" && it.value == "platform-default" }
        assertThat(correlationContext.traceIdOrCreate()).matches("[0-9a-f]{32}")
        assertThat(problemDetailFactory.internalError().properties.orEmpty())
            .containsKey(PlatformProblemDetailFactory.TRACE_ID_PROPERTY)
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(MvcMetricsFixtureController::class)
class MvcMetricsFixtureApplication {

    @Bean
    fun applicationMeterRegistry(): SimpleMeterRegistry = SimpleMeterRegistry()

    @Bean
    fun applicationSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http.authorizeHttpRequests { it.anyRequest().permitAll() }.build()
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(MvcMetricsFixtureController::class)
class MvcMetricsCustomizerFixtureApplication {

    @Bean
    fun applicationMeterRegistry(): SimpleMeterRegistry = SimpleMeterRegistry()

    @Bean
    fun applicationSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http.authorizeHttpRequests { it.anyRequest().permitAll() }.build()

    @Bean
    fun applicationMetricsCustomizer(): PlatformMetricsCustomizer = APPLICATION_POLICY

    companion object {
        val APPLICATION_POLICY = PlatformMetricsCustomizer { _, _ -> }
    }
}

@RestController
class MvcMetricsFixtureController(
    private val registry: MeterRegistry,
) {

    private val inFlight = AtomicInteger(3)

    @GetMapping("/metrics/record")
    fun record(): Map<String, Number> {
        Counter.builder("shipments.processed").register(registry).increment()
        Timer.builder("shipments.processing").register(registry).record(25, java.util.concurrent.TimeUnit.MILLISECONDS)
        registry.gauge("shipments.in-flight", inFlight)
        return mapOf("inFlight" to inFlight.get())
    }
}
