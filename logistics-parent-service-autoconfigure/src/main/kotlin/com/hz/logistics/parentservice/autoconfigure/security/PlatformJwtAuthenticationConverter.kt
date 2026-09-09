package com.hz.logistics.parentservice.autoconfigure.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter

/**
 * Authentication adapter used by both stack-specific resource-server branches.
 *
 * Standard scope authorities remain available while configured nested roles are
 * added deterministically. Claim traversal is delegated solely to
 * [RoleClaimsAuthorityMapper], so this class never assumes a Keycloak or other
 * vendor-specific claim structure.
 */
class PlatformJwtAuthenticationConverter(
    private val roleMapper: RoleClaimsAuthorityMapper,
    private val standardAuthorities: Converter<Jwt, Collection<GrantedAuthority>> = JwtGrantedAuthoritiesConverter(),
) : Converter<Jwt, AbstractAuthenticationToken> {

    override fun convert(source: Jwt): AbstractAuthenticationToken {
        val authorities = LinkedHashSet<GrantedAuthority>()
        standardAuthorities.convert(source).forEach(authorities::add)
        roleMapper.map(source).forEach(authorities::add)
        return JwtAuthenticationToken(source, authorities, source.subject.orEmpty())
    }
}
