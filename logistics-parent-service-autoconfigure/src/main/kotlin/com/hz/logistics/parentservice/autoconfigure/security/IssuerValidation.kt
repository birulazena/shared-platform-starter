package com.hz.logistics.parentservice.autoconfigure.security

import com.hz.logistics.parentservice.autoconfigure.properties.SecurityProperties
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder
import org.springframework.security.oauth2.jwt.SupplierReactiveJwtDecoder
import java.net.URI

/**
 * Issuer validation and selected-stack decoder selection.
 *
 * Call this only from an active platform security branch. Keeping it out of
 * configuration-property validation preserves the supported application-chain
 * back-off: an application that owns the complete chain does not need platform
 * issuer settings.
 */
object IssuerValidation {

    /** Returns the configured issuer or fails startup with an actionable secure-default message. */
    @JvmStatic
    fun requireValidIssuer(properties: SecurityProperties): URI = requireValidIssuer(properties.issuer)

    /** Validates the URI shape required by issuer discovery and issuer equality validation. */
    @JvmStatic
    fun requireValidIssuer(issuer: URI?): URI {
        require(issuer != null && isValid(issuer)) {
            "logistics.parent-service.security.issuer must be an absolute HTTP(S) URI without user-info, query, or fragment"
        }
        return issuer
    }

    @JvmStatic
    fun isValid(issuer: URI?): Boolean = issuer != null &&
        issuer.isAbsolute &&
        !issuer.isOpaque &&
        !issuer.host.isNullOrBlank() &&
        (issuer.scheme.equals("http", ignoreCase = true) || issuer.scheme.equals("https", ignoreCase = true)) &&
        issuer.userInfo == null &&
        issuer.query == null &&
        issuer.fragment == null

    /**
     * Reuses an application decoder when supplied; otherwise lazily discovers
     * the configured issuer. URI validation always runs for an active default
     * security branch, even when the application supplies the decoder.
     */
    @JvmStatic
    fun selectedJwtDecoder(properties: SecurityProperties, applicationDecoder: JwtDecoder? = null): JwtDecoder {
        val issuer = requireValidIssuer(properties).toASCIIString()
        return applicationDecoder ?: SupplierJwtDecoder { JwtDecoders.fromIssuerLocation(issuer) }
    }

    /** Reactive counterpart of [selectedJwtDecoder] with lazy, nonblocking discovery. */
    @JvmStatic
    fun selectedReactiveJwtDecoder(
        properties: SecurityProperties,
        applicationDecoder: ReactiveJwtDecoder? = null,
    ): ReactiveJwtDecoder {
        val issuer = requireValidIssuer(properties).toASCIIString()
        return applicationDecoder ?: SupplierReactiveJwtDecoder { ReactiveJwtDecoders.fromIssuerLocation(issuer) }
    }
}
