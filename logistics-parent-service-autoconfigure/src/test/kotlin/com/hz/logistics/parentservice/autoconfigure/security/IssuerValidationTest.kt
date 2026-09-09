package com.hz.logistics.parentservice.autoconfigure.security

import com.hz.logistics.parentservice.autoconfigure.properties.SecurityProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder
import org.springframework.security.oauth2.jwt.SupplierReactiveJwtDecoder
import reactor.core.publisher.Mono
import java.net.URI

class IssuerValidationTest {

    @Test
    fun `requires an absolute HTTP issuer without credential or URI suffix components`() {
        listOf(
            null,
            URI("issuer.example.test"),
            URI("ftp://issuer.example.test"),
            URI("https://user@issuer.example.test"),
            URI("https://issuer.example.test?tenant=logistics"),
            URI("https://issuer.example.test#fragment"),
        ).forEach { issuer ->
            assertThatIllegalArgumentException().isThrownBy {
                IssuerValidation.requireValidIssuer(issuer)
            }
        }

        val issuer = URI("https://issuer.example.test/realms/logistics")
        assertThat(IssuerValidation.requireValidIssuer(issuer)).isEqualTo(issuer)
    }

    @Test
    fun `reuses supplied decoders and defers issuer discovery when no decoder is supplied`() {
        val properties = SecurityProperties().apply {
            issuer = URI("https://issuer.example.test/realms/logistics")
        }
        val jwtDecoder = JwtDecoder { throw JwtException("fixture") }
        val reactiveJwtDecoder = ReactiveJwtDecoder { Mono.error(JwtException("fixture")) }

        assertThat(IssuerValidation.selectedJwtDecoder(properties, jwtDecoder)).isSameAs(jwtDecoder)
        assertThat(IssuerValidation.selectedReactiveJwtDecoder(properties, reactiveJwtDecoder))
            .isSameAs(reactiveJwtDecoder)
        assertThat(IssuerValidation.selectedJwtDecoder(properties)).isInstanceOf(SupplierJwtDecoder::class.java)
        assertThat(IssuerValidation.selectedReactiveJwtDecoder(properties))
            .isInstanceOf(SupplierReactiveJwtDecoder::class.java)
    }
}
