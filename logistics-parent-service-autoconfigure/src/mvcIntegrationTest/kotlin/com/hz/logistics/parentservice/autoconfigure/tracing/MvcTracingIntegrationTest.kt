package com.hz.logistics.parentservice.autoconfigure.tracing

import com.hz.logistics.parentservice.autoconfigure.support.ControlledOtlpCollector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.slf4j.LoggerFactory

@SpringBootTest(
    classes = [MvcTracingFixtureApplication::class],
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "logistics.parent-service.security.enabled=false",
        "logistics.parent-service.tracing.sampling-probability=1.0",
        "management.otlp.metrics.export.enabled=false",
    ],
)
@ExtendWith(OutputCaptureExtension::class)
class MvcTracingIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    private val http: HttpClient = HttpClient.newHttpClient()

    @Test
    fun `continues inbound W3C context, injects the managed RestClient, and exports asynchronously`() {
        val response = request("/traces/outbound", VALID_TRACE_PARENT)

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(outboundCollector.requestCount).isPositive()
        traceParent(outboundCollector.requests.last().headers).hasTraceId(TRACE_ID)
        assertThat(exportCollector.awaitRequest(Duration.ofSeconds(2))).isTrue()
        assertThat(exportCollector.requests.last().method).isEqualTo("POST")
    }

    @Test
    fun `creates and propagates a fresh trace when traceparent is absent`() {
        val missingContext = request("/traces/outbound")

        assertThat(missingContext.statusCode()).isEqualTo(200)
        traceParent(outboundCollector.requests.last().headers).isValid().hasTraceIdNotEqualTo(TRACE_ID)
    }

    @Test
    fun `replaces malformed inbound context without failing the MVC request`() {
        val malformedContext = request("/traces/outbound", "not-a-traceparent")

        assertThat(malformedContext.statusCode()).isEqualTo(200)
        traceParent(outboundCollector.requests.last().headers).isValid().hasTraceIdNotEqualTo(TRACE_ID)
    }

    @Test
    fun `correlates a platform problem response and JSON log with the inbound trace`(output: CapturedOutput) {
        exportCollector.reject()

        val response = request("/traces/failure", VALID_TRACE_PARENT)

        assertThat(response.statusCode()).isEqualTo(500)
        assertThat(response.body()).contains("\"traceId\":\"$TRACE_ID\"")
        assertThat(output.out.lines().lastOrNull { it.contains("mvc-trace-failure") })
            .contains("\"traceId\":\"$TRACE_ID\"")
    }

    @Test
    fun `rejecting exporter does not fail a successful managed outbound request`() {
        exportCollector.reject()

        val response = request("/traces/outbound", VALID_TRACE_PARENT)

        assertThat(response.statusCode()).isEqualTo(200)
        traceParent(outboundCollector.requests.last().headers).hasTraceId(TRACE_ID)
    }

    private fun request(path: String, traceParent: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .GET()
            .apply { traceParent?.let { header("traceparent", it) } }
            .build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
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

        fun traceParent(headers: Map<String, List<String>>): TraceParentAssertion =
            TraceParentAssertion(headers.entries.firstOrNull { it.key.equals("traceparent", ignoreCase = true) }
                ?.value
                ?.singleOrNull())
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EnableWebSecurity
@Import(MvcTracingFixtureController::class)
class MvcTracingFixtureApplication {

    /** Tracing acceptance is independent of the platform security contract. */
    @Bean
    fun applicationSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http.authorizeHttpRequests { it.anyRequest().permitAll() }.build()
}

@RestController
class MvcTracingFixtureController(
    private val restClientBuilder: RestClient.Builder,
    @param:Value("\${tracing.fixture.outbound-url}") private val outboundUrl: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping("/traces/outbound")
    fun outbound(): Map<String, String> {
        restClientBuilder.build().get().uri(outboundUrl).retrieve().toBodilessEntity()
        return mapOf("status" to "ok")
    }

    @GetMapping("/traces/failure")
    fun failure(): Nothing {
        logger.error("mvc-trace-failure")
        throw IllegalStateException("tracing fixture failure")
    }
}

private class TraceParentAssertion(private val value: String?) {

    fun isValid(): TraceParentAssertion {
        assertThat(value).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")
        return this
    }

    fun hasTraceId(traceId: String): TraceParentAssertion {
        assertThat(value).startsWith("00-$traceId-")
        return this
    }

    fun hasTraceIdNotEqualTo(traceId: String): TraceParentAssertion {
        assertThat(value).doesNotStartWith("00-$traceId-")
        return this
    }
}
