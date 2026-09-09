package com.hz.logistics.parentservice.autoconfigure.security

import com.hz.logistics.parentservice.autoconfigure.properties.SecurityProperties
import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Converts an explicitly configured nested JWT claim to role authorities.
 *
 * This converter deliberately has no vendor-specific fallback claim path. A
 * malformed value yields no role authority rather than coercing data that may
 * accidentally grant access.
 */
class RoleClaimsAuthorityMapper(
    private val properties: SecurityProperties,
) : Converter<Jwt, Collection<GrantedAuthority>> {

    override fun convert(source: Jwt): Collection<GrantedAuthority> = map(source)

    /** Maps configured roles in encounter order, with duplicates removed after trimming. */
    fun map(jwt: Jwt): List<SimpleGrantedAuthority> {
        val roles = extractRoles(jwt.claims) ?: return emptyList()
        val uniqueRoles = LinkedHashSet<String>()
        roles.forEach { role ->
            role.trim().takeIf(String::isNotEmpty)?.let(uniqueRoles::add)
        }
        return uniqueRoles.map { role -> SimpleGrantedAuthority(properties.rolePrefix + role) }
    }

    private fun extractRoles(claims: Map<String, Any>): List<String>? {
        val path = properties.roleClaimsPath?.takeIf(String::isNotBlank) ?: return emptyList()
        val segments = path.split('.')
        if (segments.any(String::isBlank)) return emptyList()

        var value: Any? = claims
        segments.forEach { segment ->
            val nestedClaims = value as? Map<*, *> ?: return emptyList()
            value = nestedClaims[segment] ?: return emptyList()
        }

        return when (value) {
            is String -> listOf(value)
            is Collection<*> -> {
                if (value.all { it is String }) {
                    @Suppress("UNCHECKED_CAST")
                    (value as Collection<String>).toList()
                } else {
                    null
                }
            }
            else -> null
        }
    }
}
