package com.hz.logistics.parentservice.autoconfigure.properties

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.net.URI

/** Configuration for the platform's selected-stack resource-server security. */
class SecurityProperties {

    /** Whether platform security defaults are eligible to activate. */
    var enabled: Boolean = true

    /** Absolute HTTP(S) issuer used by the default JWT decoder. */
    var issuer: URI? = null

    /** Permit-only application path patterns. */
    var publicEndpoints: List<String> = emptyList()

    /** Whether present and exposed health/info actuator endpoints are public. */
    var publicActuatorEndpoints: Boolean = true

    /** Optional dot-separated nested JWT claim path used for role extraction. */
    @field:Pattern(
        regexp = "[^.\\s]+(?:\\.[^.\\s]+)*",
        message = "role-claims-path must contain non-blank dot-separated claim keys"
    )
    var roleClaimsPath: String? = null

    /** Prefix applied to extracted role authorities; an empty prefix is valid. */
    @field:Size(max = 64, message = "role-prefix must contain at most 64 characters")
    var rolePrefix: String = "ROLE_"

}
