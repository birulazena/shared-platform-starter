package com.hz.logistics.parentservice.autoconfigure.properties

import jakarta.validation.constraints.NotNull

/** Configuration for the shared RFC 7807-compatible error contract. */
class ErrorProperties {

    /** Whether platform error handlers are eligible to activate. */
    var enabled: Boolean = true

    /** Detail disclosure policy; GENERIC is the safe default. */
    @field:NotNull
    var detailPolicy: DetailPolicy = DetailPolicy.GENERIC

    /** Whether a safe request path is included as ProblemDetail.instance. */
    var includeInstance: Boolean = true

    /** Supported ProblemDetail detail disclosure policies. */
    enum class DetailPolicy {
        GENERIC,
        SAFE,
    }
}

typealias ErrorDetailPolicy = ErrorProperties.DetailPolicy
