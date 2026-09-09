package com.hz.logistics.parentservice.autoconfigure.properties

import com.hz.logistics.parentservice.autoconfigure.PlatformAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.math.BigDecimal
import java.time.Duration

class PlatformPropertiesBindingTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                PlatformAutoConfiguration::class.java,
            )
        )

    @Test
    fun `binds canonical namespaces and documented defaults`() {
        contextRunner
            .withPropertyValues("hz.logistics.parent-service.security.enabled=false")
            .run { context ->
                assertThat(context).hasNotFailed()
                val properties = context.getBean(PlatformProperties::class.java)

                assertThat(properties.security.enabled).isTrue()
                assertThat(properties.security.issuer).isNull()
                assertThat(properties.security.publicEndpoints).isEmpty()
                assertThat(properties.security.publicActuatorEndpoints).isTrue()
                assertThat(properties.security.roleClaimsPath).isNull()
                assertThat(properties.security.rolePrefix).isEqualTo("ROLE_")
                assertThat(properties.tracing.enabled).isTrue()
                assertThat(properties.tracing.samplingProbability).isEqualByComparingTo("0.1")
                assertThat(properties.tracing.otlp.endpoint).isNull()
                assertThat(properties.tracing.otlp.protocol).isEqualTo(OtlpProtocol.HTTP_PROTOBUF)
                assertThat(properties.tracing.otlp.headers).isEmpty()
                assertThat(properties.tracing.otlp.timeout).isEqualTo(Duration.ofSeconds(10))
                assertThat(properties.tracing.otlp.compression).isEqualTo(OtlpCompression.GZIP)
                assertThat(properties.metrics.enabled).isTrue()
                assertThat(properties.metrics.commonTags).isEmpty()
                assertThat(properties.errors.enabled).isTrue()
                assertThat(properties.errors.detailPolicy).isEqualTo(ErrorDetailPolicy.GENERIC)
                assertThat(properties.errors.includeInstance).isTrue()
                assertThat(properties.logging.enabled).isTrue()
                assertThat(properties.logging.consoleEnabled).isTrue()
                assertThat(properties.logging.otelEnabled).isTrue()
                assertThat(properties.logging.redactionMask).isEqualTo("[REDACTED]")
                assertThat(properties.logging.additionalSensitiveFields).isEmpty()
                assertThat(properties.logging.additionalSensitivePaths).isEmpty()
            }
    }

    @Test
    fun `binds all supported value shapes`() {
        contextRunner
            .withPropertyValues(
                "logistics.parent-service.security.enabled=false",
                "logistics.parent-service.security.issuer=https://identity.example.test/realms/logistics",
                "logistics.parent-service.security.public-endpoints[0]=/public/**",
                "logistics.parent-service.security.public-actuator-endpoints=false",
                "logistics.parent-service.security.role-claims-path=realm_access.roles",
                "logistics.parent-service.security.role-prefix=PERM_",
                "logistics.parent-service.tracing.sampling-probability=1.0",
                "logistics.parent-service.tracing.otlp.endpoint=https://otel.example.test:4318/v1/traces",
                "logistics.parent-service.tracing.otlp.protocol=HTTP_PROTOBUF",
                "logistics.parent-service.tracing.otlp.headers.Authorization=Bearer secret",
                "logistics.parent-service.tracing.otlp.timeout=5s",
                "logistics.parent-service.tracing.otlp.compression=NONE",
                "logistics.parent-service.metrics.common-tags.environment=test",
                "logistics.parent-service.errors.detail-policy=SAFE",
                "logistics.parent-service.errors.include-instance=false",
                "logistics.parent-service.logging.console-enabled=false",
                "logistics.parent-service.logging.otel-enabled=false",
                "logistics.parent-service.logging.redaction-mask=MASKED",
                "logistics.parent-service.logging.additional-sensitive-fields[0]=customerEmail",
                "logistics.parent-service.logging.additional-sensitive-paths[0]=shipment.recipient.phone",
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                val properties = context.getBean(PlatformProperties::class.java)

                assertThat(properties.security.issuer.toString())
                    .isEqualTo("https://identity.example.test/realms/logistics")
                assertThat(properties.security.publicEndpoints).containsExactly("/public/**")
                assertThat(properties.security.roleClaimsPath).isEqualTo("realm_access.roles")
                assertThat(properties.security.rolePrefix).isEqualTo("PERM_")
                assertThat(properties.tracing.samplingProbability).isEqualByComparingTo(BigDecimal.ONE)
                assertThat(properties.tracing.otlp.headers).containsEntry("Authorization", "Bearer secret")
                assertThat(properties.tracing.otlp.timeout).isEqualTo(Duration.ofSeconds(5))
                assertThat(properties.metrics.commonTags).containsEntry("environment", "test")
                assertThat(properties.errors.detailPolicy).isEqualTo(ErrorDetailPolicy.SAFE)
                assertThat(properties.logging.additionalSensitiveFields).containsExactly("customerEmail")
                assertThat(properties.logging.additionalSensitivePaths).containsExactly("shipment.recipient.phone")
            }
    }

    @Test
    fun `rejects values outside the documented validation ranges`() {
        contextRunner
            .withPropertyValues("logistics.parent-service.tracing.sampling-probability=1.01")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("sampling-probability")
            }

        contextRunner
            .withPropertyValues("logistics.parent-service.logging.redaction-mask= ")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("redaction-mask")
            }

        contextRunner
            .withPropertyValues("logistics.parent-service.tracing.otlp.timeout=61s")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("OTLP timeout")
            }

    }

    @Test
    fun `issuer requirement remains conditional for the shared non-web configuration`() {
        contextRunner
            .withPropertyValues("logistics.parent-service.security.enabled=false")
            .run { context -> assertThat(context).hasNotFailed() }

        contextRunner
            .withPropertyValues("logistics.parent-service.security.enabled=true")
            .run { context -> assertThat(context).hasNotFailed() }
    }

    @Test
    fun `does not bind an alternate configuration root`() {
        contextRunner
            .withPropertyValues("hz.logistics.parent-service.security.enabled=false")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBean(PlatformProperties::class.java).security.enabled).isTrue()
            }
    }
}
