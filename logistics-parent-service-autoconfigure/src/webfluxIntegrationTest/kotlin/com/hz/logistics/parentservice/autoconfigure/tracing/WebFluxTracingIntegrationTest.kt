package com.hz.logistics.parentservice.autoconfigure.tracing

import com.hz.logistics.parentservice.autoconfigure.support.ControlledOtlpCollector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import org.slf4j.LoggerFactory

@SpringBootTest(
    classes = [WebFluxTracingFixtureApplication::class],
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "logistics.parent-service.security.enabled=false",
        "logistics.parent-service.tracing.sampling-probability=1.0",
        "management.otlp.metrics.export.enabled=false",
    ],
)
@ExtendWith(OutputCaptureExtension::class)
class WebFluxTracingIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    private val http: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @Test
    fun `continues inbound W3C context, injects the managed WebClient, and exports asynchronously`() {
        http.get().uri("/traces/outbound")
            .header("traceparent", VALID_TRACE_PARENT)
            .exchange()
            .expectStatus().isOk

        assertThat(outboundCollector.requestCount).isPositive()
        traceParent(outboundCollector.requests.last().headers).hasTraceId(TRACE_ID)
        assertThat(exportCollector.awaitRequest(Duration.ofSeconds(2))).isTrue()
        assertThat(exportCollector.requests.last().method).isEqualTo("POST")
    }

    @Test
    fun `replaces missing or malformed context and retains it across a Reactor scheduler boundary`() {
        http.get().uri("/traces/reactor")
            .header("traceparent", VALID_TRACE_PARENT)
            .exchange()
            .expectStatus().isOk

        traceParent(outboundCollector.requests.last().headers).hasTraceId(TRACE_ID)

        http.get().uri("/traces/outbound")
            .header("traceparent", "not-a-traceparent")
            .exchange()
            .expectStatus().isOk

        traceParent(outboundCollector.requests.last().headers).isValid().hasTraceIdNotEqualTo(TRACE_ID)
    }

    @Test
    fun `creates and propagates a fresh trace when traceparent is absent`() {
        http.get().uri("/traces/reactor")
            .exchange()
            .expectStatus().isOk

        traceParent(outboundCollector.requests.last().headers)
            .isValid()
            .hasTraceIdNotEqualTo(TRACE_ID)
    }

    @Test
    fun `correlates reactive problem responses and JSON logs with a rejecting collector`(output: CapturedOutput) {
        exportCollector.reject()

        val response = http.get().uri("/traces/failure")
            .header("traceparent", VALID_TRACE_PARENT)
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody(String::class.java)
            .returnResult()

        assertThat(response.responseBody).contains("\"traceId\":\"$TRACE_ID\"")
        assertThat(output.out.lines().lastOrNull { it.contains("webflux-trace-failure") })
            .contains("\"traceId\":\"$TRACE_ID\"")
    }

    @Test
    fun `rejecting exporter does not fail a successful managed WebClient request`() {
        exportCollector.reject()

        http.get().uri("/traces/outbound")
            .header("traceparent", VALID_TRACE_PARENT)
            .exchange()
            .expectStatus().isOk

        traceParent(outboundCollector.requests.last().headers).isValid().hasTraceId(TRACE_ID)
    }

    private companion object {
        const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
        const val VALID_TRACE_PARENT = "00-$TRACE_ID-00f067aa0ba902b7-01"

        val outboundCollector = ControlledOtlpCollector().start()
        val exportCollector = ControlledOtlpCollector().start()

        @JvmStatic
        @DynamicPropertySource
        fun tracingProperties(registry: DynamicPropertyRegistry) {
            registry.add("tracing.fixture.outbound-url") { outboundCollector.endpoint.toString() }
            registry.add("logistics.parent-service.tracing.otlp.endpoint") { exportCollector.traceEndpoint.toString() }
            registry.add("logistics.parent-service.tracing.otlp.protocol") { "HTTP_PROTOBUF" }
        }

        @JvmStatic
        @AfterAll
        fun closeCollectors() {
            outboundCollector.close()
            exportCollector.close()
        }

        fun traceParent(headers: Map<String, List<String>>): WebFluxTraceParentAssertion =
            WebFluxTraceParentAssertion(headers.entries.firstOrNull { it.key.equals("traceparent", ignoreCase = true) }
                ?.value
                ?.singleOrNull())
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EnableWebFluxSecurity
@Import(WebFluxTracingFixtureController::class)
class WebFluxTracingFixtureApplication {

    /** Tracing acceptance is independent of the platform security contract. */
    @Bean
    fun applicationSecurityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http.authorizeExchange { it.anyExchange().permitAll() }.build()
}

@RestController
class WebFluxTracingFixtureController(
    private val webClientBuilder: WebClient.Builder,
    @param:Value("\${tracing.fixture.outbound-url}") private val outboundUrl: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping("/traces/outbound")
    fun outbound(): Mono<Map<String, String>> =
        webClientBuilder.build().get().uri(outboundUrl).retrieve().toBodilessEntity()
            .thenReturn(mapOf("status" to "ok"))

    @GetMapping("/traces/reactor")
    fun reactorBoundary(): Mono<Map<String, String>> =
        Mono.defer { outbound() }.publishOn(Schedulers.parallel())

    @GetMapping("/traces/failure")
    fun failure(): Mono<Nothing> {
        logger.error("webflux-trace-failure")
        return Mono.error(IllegalStateException("tracing fixture failure"))
    }
}

private class WebFluxTraceParentAssertion(private val value: String?) {

    fun isValid(): WebFluxTraceParentAssertion {
        assertThat(value).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")
        return this
    }

    fun hasTraceId(traceId: String): WebFluxTraceParentAssertion {
        assertThat(value).startsWith("00-$traceId-")
        return this
    }

    fun hasTraceIdNotEqualTo(traceId: String): WebFluxTraceParentAssertion {
        assertThat(value).doesNotStartWith("00-$traceId-")
        return this
    }
}
