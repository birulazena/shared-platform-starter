package com.hz.logistics.parentservice.autoconfigure.properties

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConfigurationMetadataTest {

    private val mapper = ObjectMapper()

    @Test
    fun `metadata advertises every canonical group with its exact type and description`() {
        val generated = readMetadata("META-INF/spring-configuration-metadata.json", "groups")
        val additional = readMetadata("META-INF/additional-spring-configuration-metadata.json", "groups")
        val groups = (generated + additional).associateBy { it["name"].asText() }
        val expected = mapOf(
            "logistics.parent-service" to ExpectedGroup(
                "com.hz.logistics.parentservice.autoconfigure.properties.PlatformProperties",
                "Shared platform configuration.",
            ),
            "logistics.parent-service.security" to ExpectedGroup(
                "com.hz.logistics.parentservice.autoconfigure.properties.SecurityProperties",
                "JWT resource-server security defaults.",
            ),
            "logistics.parent-service.tracing" to ExpectedGroup(
                "com.hz.logistics.parentservice.autoconfigure.properties.TracingProperties",
                "W3C trace propagation and OTLP settings.",
            ),
            "logistics.parent-service.tracing.otlp" to ExpectedGroup(
                "com.hz.logistics.parentservice.autoconfigure.properties.TracingProperties\$OtlpProperties",
                "Optional OTLP exporter settings.",
            ),
            "logistics.parent-service.metrics" to ExpectedGroup(
                "com.hz.logistics.parentservice.autoconfigure.properties.MetricsProperties",
                "Micrometer policy settings.",
            ),
            "logistics.parent-service.errors" to ExpectedGroup(
                "com.hz.logistics.parentservice.autoconfigure.properties.ErrorProperties",
                "ProblemDetail error settings.",
            ),
            "logistics.parent-service.logging" to ExpectedGroup(
                "com.hz.logistics.parentservice.autoconfigure.properties.LoggingProperties",
                "Structured logging and redaction settings.",
            ),
        )

        assertThat(generated.map { it["name"].asText() })
            .containsAll(expected.keys)
        assertThat(groups.keys)
            .filteredOn { it.startsWith("logistics.parent-service") }
            .containsExactlyInAnyOrderElementsOf(expected.keys)
        expected.forEach { (name, value) ->
            val metadata = requireNotNull(groups[name]) { "Missing metadata group for $name" }
            assertThat(metadata["type"].asText()).isEqualTo(value.type)
            assertThat(metadata["description"].asText()).isEqualTo(value.description)
        }
    }

    @Test
    fun `metadata advertises every canonical property with exact type description and default semantics`() {
        val generated = readMetadata("META-INF/spring-configuration-metadata.json", "properties")
        val additional = readMetadata("META-INF/additional-spring-configuration-metadata.json", "properties")
        val generatedProperties = generated.associateBy { it["name"].asText() }
        val properties = (generated + additional).associateBy { it["name"].asText() }
        val expected = expectedProperties()

        assertThat(generatedProperties.keys).containsAll(expected.keys)
        assertThat(properties.keys)
            .filteredOn { it.startsWith("logistics.parent-service.") }
            .containsExactlyInAnyOrderElementsOf(expected.keys)

        expected.forEach { (name, value) ->
            val metadata = requireNotNull(properties[name]) { "Missing metadata for $name" }
            assertThat(metadata["type"].asText()).isEqualTo(value.type)
            assertThat(metadata["description"].asText()).isEqualTo(value.description)
            if (value.defaultValueJson == null) {
                assertThat(metadata.has("defaultValue"))
                    .withFailMessage("Nullable property $name must not advertise a non-null default")
                    .isFalse()
            } else {
                assertThat(metadata["defaultValue"])
                    .withFailMessage("Unexpected default semantics for $name")
                    .isEqualTo(mapper.readTree(value.defaultValueJson))
            }
        }
    }

    @Test
    fun `metadata contains no alternate root`() {
        val generated = readMetadata("META-INF/spring-configuration-metadata.json", "properties")
        val additional = readMetadata("META-INF/additional-spring-configuration-metadata.json", "properties")
        val names = (generated + additional).map { it["name"].asText() }

        assertThat(names).noneMatch { it.startsWith("hz.logistics.") }
    }

    private fun expectedProperties(): Map<String, ExpectedProperty> = mapOf(
        "logistics.parent-service.security.enabled" to property(
            "java.lang.Boolean", "Whether platform security defaults are eligible to activate.", "true",
        ),
        "logistics.parent-service.security.issuer" to property(
            "java.net.URI", "Absolute HTTP(S) issuer used by the default JWT decoder.",
        ),
        "logistics.parent-service.security.public-endpoints" to property(
            "java.util.List<java.lang.String>", "Permit-only application path patterns.", "[]",
        ),
        "logistics.parent-service.security.public-actuator-endpoints" to property(
            "java.lang.Boolean", "Whether present and exposed health/info actuator endpoints are public.", "true",
        ),
        "logistics.parent-service.security.role-claims-path" to property(
            "java.lang.String", "Optional dot-separated nested JWT claim path used for role extraction.",
        ),
        "logistics.parent-service.security.role-prefix" to property(
            "java.lang.String", "Prefix applied to extracted role authorities; an empty prefix is valid.", "\"ROLE_\"",
        ),
        "logistics.parent-service.tracing.enabled" to property(
            "java.lang.Boolean", "Whether platform tracing and correlation integration are eligible to activate.", "true",
        ),
        "logistics.parent-service.tracing.sampling-probability" to property(
            "java.math.BigDecimal", "Inclusive probability used for recording/export sampling.", "0.1",
        ),
        "logistics.parent-service.tracing.otlp.endpoint" to property(
            "java.net.URI", "Absolute exporter endpoint, or null to keep export disabled.",
        ),
        "logistics.parent-service.tracing.otlp.protocol" to property(
            "com.hz.logistics.parentservice.autoconfigure.properties.TracingProperties\$OtlpProtocol",
            "OTLP transport protocol.", "\"HTTP_PROTOBUF\"",
        ),
        "logistics.parent-service.tracing.otlp.headers" to property(
            "java.util.Map<java.lang.String,java.lang.String>",
            "Export headers; values are secrets and must never be logged.", "{}",
        ),
        "logistics.parent-service.tracing.otlp.timeout" to property(
            "java.time.Duration", "Maximum time allotted to one exporter operation.", "\"10s\"",
        ),
        "logistics.parent-service.tracing.otlp.compression" to property(
            "com.hz.logistics.parentservice.autoconfigure.properties.TracingProperties\$OtlpCompression",
            "OTLP payload compression.", "\"GZIP\"",
        ),
        "logistics.parent-service.metrics.enabled" to property(
            "java.lang.Boolean", "Whether platform metrics policy is eligible to activate.", "true",
        ),
        "logistics.parent-service.metrics.common-tags" to property(
            "java.util.Map<java.lang.String,java.lang.String>",
            "Bounded, non-sensitive common tags applied by the platform policy.", "{}",
        ),
        "logistics.parent-service.errors.enabled" to property(
            "java.lang.Boolean", "Whether platform error handlers are eligible to activate.", "true",
        ),
        "logistics.parent-service.errors.detail-policy" to property(
            "com.hz.logistics.parentservice.autoconfigure.properties.ErrorProperties\$DetailPolicy",
            "Detail disclosure policy; GENERIC is the safe default.", "\"GENERIC\"",
        ),
        "logistics.parent-service.errors.include-instance" to property(
            "java.lang.Boolean", "Whether a safe request path is included as ProblemDetail.instance.", "true",
        ),
        "logistics.parent-service.logging.enabled" to property(
            "java.lang.Boolean", "Whether the platform logging contribution is eligible to activate.", "true",
        ),
        "logistics.parent-service.logging.console-enabled" to property(
            "java.lang.Boolean", "Whether the default structured console sink is enabled.", "true",
        ),
        "logistics.parent-service.logging.otel-enabled" to property(
            "java.lang.Boolean", "Whether sanitized events are forwarded to an available OTel log pipeline.", "true",
        ),
        "logistics.parent-service.logging.redaction-mask" to property(
            "java.lang.String", "Replacement value used by the baseline and configured redaction rules.", "\"[REDACTED]\"",
        ),
        "logistics.parent-service.logging.additional-sensitive-fields" to property(
            "java.util.Set<java.lang.String>",
            "Additional case-insensitive exact fields, headers, or query parameter names.", "[]",
        ),
        "logistics.parent-service.logging.additional-sensitive-paths" to property(
            "java.util.Set<java.lang.String>",
            "Additional dot-separated structured paths to redact.", "[]",
        ),
    )

    private fun property(type: String, description: String, defaultValueJson: String? = null): ExpectedProperty =
        ExpectedProperty(type, description, defaultValueJson)

    private fun readMetadata(resource: String, section: String): List<JsonNode> {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream(resource)) {
            "Missing metadata resource $resource"
        }
        stream.use { input ->
            return mapper.readTree(input)[section].toList()
        }
    }

    private data class ExpectedGroup(val type: String, val description: String)

    private data class ExpectedProperty(
        val type: String,
        val description: String,
        val defaultValueJson: String?,
    )
}
