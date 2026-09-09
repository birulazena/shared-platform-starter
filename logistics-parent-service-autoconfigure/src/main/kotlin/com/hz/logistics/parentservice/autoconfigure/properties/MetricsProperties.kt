package com.hz.logistics.parentservice.autoconfigure.properties

import jakarta.validation.constraints.AssertTrue

/** Configuration for the platform's Micrometer policy. */
class MetricsProperties {

    /** Whether platform metrics policy is eligible to activate. */
    var enabled: Boolean = true

    /** Bounded, non-sensitive common tags applied by the platform policy. */
    var commonTags: Map<String, String> = emptyMap()

    @AssertTrue(message = "metrics common-tag names and values must be non-blank")
    fun areCommonTagsValid(): Boolean = commonTags.all { (name, value) ->
        name.isNotBlank() && value.isNotBlank()
    }
}
