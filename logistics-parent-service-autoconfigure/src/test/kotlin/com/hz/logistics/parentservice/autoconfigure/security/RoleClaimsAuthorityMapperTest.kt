package com.hz.logistics.parentservice.autoconfigure.security

import com.hz.logistics.parentservice.autoconfigure.properties.SecurityProperties
import com.hz.logistics.parentservice.autoconfigure.support.mockJwt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RoleClaimsAuthorityMapperTest {

    @Test
    fun `returns no platform roles for an absent path or missing null and malformed claims`() {
        assertThat(authorities(path = null, claims = roles("dispatcher"))).isEmpty()
        assertThat(authorities(path = "realm_access.roles", claims = emptyMap())).isEmpty()
        assertThat(authorities(path = "realm_access.roles", claims = mapOf("realm_access" to null))).isEmpty()
        assertThat(authorities(path = "realm_access.roles", claims = mapOf("realm_access" to "not-an-object"))).isEmpty()
        assertThat(authorities(path = "realm_access.roles", claims = mapOf("realm_access" to mapOf("roles" to 42)))).isEmpty()
    }

    @Test
    fun `maps a nested string role using the default prefix`() {
        assertThat(authorities(claims = roles("dispatcher"))).containsExactly("ROLE_dispatcher")
    }

    @Test
    fun `trims discards blanks and de-duplicates nested list roles in encounter order`() {
        assertThat(
            authorities(
                claims = roles(listOf(" dispatcher ", "", "dispatcher", " planner ", "   ")),
            ),
        ).containsExactly("ROLE_dispatcher", "ROLE_planner")
    }

    @Test
    fun `rejects mixed type role collections rather than coercing values`() {
        assertThat(authorities(claims = roles(listOf("dispatcher", 42, true)))).isEmpty()
    }

    @Test
    fun `applies custom and explicitly empty role prefixes`() {
        assertThat(authorities(claims = roles("dispatcher"), prefix = "HZ_"))
            .containsExactly("HZ_dispatcher")
        assertThat(authorities(claims = roles("dispatcher"), prefix = ""))
            .containsExactly("dispatcher")
    }

    private fun authorities(
        path: String? = "realm_access.roles",
        prefix: String = "ROLE_",
        claims: Map<String, Any?>,
    ): List<String> {
        val properties = SecurityProperties().apply {
            roleClaimsPath = path
            rolePrefix = prefix
        }
        return RoleClaimsAuthorityMapper(properties)
            .map(mockJwt(claims = claims))
            .map { it.authority }
    }

    private fun roles(value: Any?): Map<String, Any?> =
        mapOf("realm_access" to mapOf("roles" to value))
}
