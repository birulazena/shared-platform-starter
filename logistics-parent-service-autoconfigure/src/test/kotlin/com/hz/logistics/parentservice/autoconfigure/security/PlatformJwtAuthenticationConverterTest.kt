package com.hz.logistics.parentservice.autoconfigure.security

import com.hz.logistics.parentservice.autoconfigure.properties.SecurityProperties
import com.hz.logistics.parentservice.autoconfigure.support.mockJwt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformJwtAuthenticationConverterTest {

    @Test
    fun `retains standard scope authorities and adds configured nested roles`() {
        val properties = SecurityProperties().apply {
            roleClaimsPath = "custom.roles"
        }
        val converter = PlatformJwtAuthenticationConverter(RoleClaimsAuthorityMapper(properties))
        val jwt = mockJwt(
            claims = mapOf(
                "scope" to "shipments.read",
                "custom" to mapOf("roles" to listOf("dispatcher", "planner")),
            ),
        )

        assertThat(converter.convert(jwt).authorities.map { it.authority })
            .containsExactlyInAnyOrder("SCOPE_shipments.read", "ROLE_dispatcher", "ROLE_planner")
    }

    @Test
    fun `does not infer roles when no nested claim path is configured`() {
        val converter = PlatformJwtAuthenticationConverter(RoleClaimsAuthorityMapper(SecurityProperties()))

        assertThat(
            converter.convert(
                mockJwt(claims = mapOf("realm_access" to mapOf("roles" to listOf("dispatcher")))),
            ).authorities,
        ).isEmpty()
    }
}
