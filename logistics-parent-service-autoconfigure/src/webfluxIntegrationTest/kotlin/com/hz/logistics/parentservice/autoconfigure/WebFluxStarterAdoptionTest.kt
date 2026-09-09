package com.hz.logistics.parentservice.autoconfigure

import com.hz.logistics.parentservice.autoconfigure.properties.PlatformProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.web.server.reactive.context.ReactiveWebServerApplicationContext
import org.springframework.context.ApplicationContext
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.test.context.ActiveProfiles
import org.springframework.util.ClassUtils

@SpringBootTest(
    classes = [WebFluxAdoptionFixtureApplication::class],
    webEnvironment = WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles("webflux")
class WebFluxStarterAdoptionTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var platformProperties: PlatformProperties

    @Test
    fun `starts a WebFlux consumer through the public starter without MVC or Servlet infrastructure`() {
        assertThat(applicationContext).isInstanceOf(ReactiveWebServerApplicationContext::class.java)
        assertThat(applicationContext.getBeansOfType(SecurityWebFilterChain::class.java)).isNotEmpty
        assertThat(
            ClassUtils.isPresent(
                "org.springframework.web.servlet.DispatcherServlet",
                applicationContext.classLoader,
            ),
        ).isFalse()
        assertThat(
            ClassUtils.isPresent("jakarta.servlet.Servlet", applicationContext.classLoader),
        ).isFalse()
        assertThat(applicationContext.getBeansOfType(WebFluxAdoptionFixtureController::class.java)).hasSize(1)
        assertThat(platformProperties.metrics.commonTags).containsEntry("environment", "adoption-test")
    }
}
