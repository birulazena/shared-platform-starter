package com.hz.logistics.parentservice.autoconfigure.properties

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import java.math.BigDecimal
import java.net.URI
import java.time.Duration

/** Configuration for W3C trace propagation and optional OTLP export. */
class TracingProperties {

    /** Whether platform tracing and correlation integration are eligible to activate. */
    var enabled: Boolean = true

    /** Inclusive probability used for recording/export sampling. */
    @field:DecimalMin(value = "0.0", message = "sampling-probability must be at least 0.0")
    @field:DecimalMax(value = "1.0", message = "sampling-probability must be at most 1.0")
    var samplingProbability: BigDecimal = BigDecimal("0.1")

    /** Canonical OTLP settings; no endpoint means no exporter is selected. */
    @field:Valid
    var otlp: OtlpProperties = OtlpProperties()

    /** OTLP transport protocol supported by the platform contract. */
    enum class OtlpProtocol {
        HTTP_PROTOBUF,
        GRPC,
    }

    /** Compression supported by the OTLP exporter. */
    enum class OtlpCompression {
        NONE,
        GZIP,
    }

    /** Nested OTLP exporter configuration. */
    class OtlpProperties {

        /** Absolute exporter endpoint, or null to keep export disabled. */
        var endpoint: URI? = null

        /** OTLP transport protocol. */
        var protocol: OtlpProtocol = OtlpProtocol.HTTP_PROTOBUF

        /** Export headers; values are secrets and must never be logged. */
        var headers: Map<String, String> = emptyMap()

        /** Maximum time allotted to one exporter operation. */
        var timeout: Duration = Duration.ofSeconds(10)

        /** OTLP payload compression. */
        var compression: OtlpCompression = OtlpCompression.GZIP

        @AssertTrue(message = "OTLP timeout must be positive and no longer than 60 seconds")
        fun isTimeoutValid(): Boolean =
            !timeout.isNegative && !timeout.isZero && timeout <= Duration.ofSeconds(60)

        @AssertTrue(message = "OTLP endpoint must be absolute and use HTTP(S), or a valid gRPC target")
        fun isEndpointValid(): Boolean {
            val value = endpoint ?: return true
            if (!value.isAbsolute || value.userInfo != null || value.query != null || value.fragment != null) {
                return false
            }
            return protocol == OtlpProtocol.GRPC || value.scheme.equals("http", ignoreCase = true) ||
                value.scheme.equals("https", ignoreCase = true)
        }

        @AssertTrue(message = "OTLP header names and values must be non-blank")
        fun areHeadersValid(): Boolean = headers.all { (name, value) ->
            name.isNotBlank() && value.isNotBlank()
        }
    }
}

typealias OtlpProtocol = TracingProperties.OtlpProtocol
typealias OtlpCompression = TracingProperties.OtlpCompression
typealias OtlpProperties = TracingProperties.OtlpProperties
