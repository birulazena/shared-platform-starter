package com.hz.logistics.parentservice.autoconfigure

import com.hz.logistics.parentservice.autoconfigure.properties.PlatformProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.context.ApplicationContext
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.context.ActiveProfiles
import org.springframework.util.ClassUtils

@SpringBootTest(
    classes = [MvcAdoptionFixtureApplication::class],
    webEnvironment = WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles("mvc")
class MvcStarterAdoptionTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var platformProperties: PlatformProperties

    @Test
    fun `starts an MVC consumer through the public starter without Reactive web infrastructure`() {
        assertThat(applicationContext).isInstanceOf(ServletWebServerApplicationContext::class.java)
        assertThat(applicationContext.getBeansOfType(SecurityFilterChain::class.java)).isNotEmpty
        assertThat(
            ClassUtils.isPresent(
                "org.springframework.web.reactive.DispatcherHandler",
                applicationContext.classLoader,
            ),
        ).isFalse()
        assertThat(
            ClassUtils.isPresent(
                "org.springframework.web.reactive.function.client.WebClient",
                applicationContext.classLoader,
            ),
        ).isFalse()
        assertThat(applicationContext.getBeansOfType(MvcAdoptionFixtureController::class.java)).hasSize(1)
        assertThat(platformProperties.metrics.commonTags).containsEntry("environment", "adoption-test")
    }
}
