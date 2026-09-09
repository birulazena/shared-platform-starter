package com.hz.logistics.parentservice.autoconfigure.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient

class ManagedClientBuildersContractTest {

    @Test
    fun `managed client fixture requires both context-managed builders`() {
        val managedBuilderMethods = PlatformTestFixtures::class.java.declaredMethods
            .filter { it.name == "managedClientBuilders" }

        assertThat(managedBuilderMethods).hasSize(1)
        val method = managedBuilderMethods.single()
        assertThat(method.parameterTypes.toList()).containsExactly(
            RestClient.Builder::class.java,
            WebClient.Builder::class.java,
        )
        assertThat(PlatformTestFixtures::class.java.declaredMethods)
            .noneMatch { it.name == "managedClientBuilders\$default" }
    }
}
