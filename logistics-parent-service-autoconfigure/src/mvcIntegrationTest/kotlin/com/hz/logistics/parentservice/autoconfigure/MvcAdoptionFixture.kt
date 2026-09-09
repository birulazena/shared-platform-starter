package com.hz.logistics.parentservice.autoconfigure

import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.SpringBootConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Minimal Servlet consumer used by the public-starter adoption test. */
@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(MvcAdoptionFixtureController::class)
class MvcAdoptionFixtureApplication {

    @Bean
    fun jwtDecoder(): JwtDecoder = JwtDecoder {
        throw JwtException("A token is not needed for the startup fixture")
    }
}

/** A representative MVC endpoint; the application, not the starter, chose MVC. */
@RestController
class MvcAdoptionFixtureController {

    @GetMapping("/fixtures/mvc")
    fun response(): Map<String, String> = mapOf("stack" to "mvc")
}
