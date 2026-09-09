package com.hz.logistics.parentservice.autoconfigure.security

import com.hz.logistics.parentservice.autoconfigure.support.mockJwt
import com.hz.logistics.parentservice.autoconfigure.properties.PlatformProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.security.access.annotation.Secured
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.access.prepost.PreFilter
import jakarta.annotation.security.DenyAll
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.stereotype.Service

@SpringBootTest(
    classes = [MvcSecurityFixtureApplication::class],
    webEnvironment = WebEnvironment.MOCK,
    properties = [
        "logistics.parent-service.security.issuer=https://identity.example.test/realms/logistics",
        "logistics.parent-service.security.public-endpoints[0]=/public/**",
        "logistics.parent-service.security.role-claims-path=realm_access.roles",
        "management.endpoints.web.base-path=/manage",
        "management.endpoints.web.exposure.include=health,info",
    ],
)
@AutoConfigureMockMvc
class MvcSecurityIntegrationTest(
) {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `denies protected requests by default with a safe problem response`() {
        mockMvc.perform(get("/protected"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.traceId").isNotEmpty())
    }

    @Test
    fun `permits configured public paths and default health and info endpoints`() {
        mockMvc.perform(get("/public/ping")).andExpect(status().isOk)
        mockMvc.perform(get("/manage/health")).andExpect(status().isOk)
        mockMvc.perform(get("/manage/info")).andExpect(status().isOk)
    }

    @Test
    fun `accepts a mock jwt and applies nested role authorities`() {
        mockMvc.perform(
            get("/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authorities[0]").value("ROLE_dispatcher"))
            .andExpect(jsonPath("$.authorities[1]").value("ROLE_planner"))
    }

    @Test
    fun `accepts a nested string role with the default prefix`() {
        mockMvc.perform(get("/protected").header(HttpHeaders.AUTHORIZATION, "Bearer nested-string"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authorities[0]").value("ROLE_driver"))
    }

    @Test
    fun `automatically authorizes every required MVC method annotation family`() {
        mockMvc.perform(get("/methods/pre-authorize").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
            .andExpect(status().isOk)
            .andExpect(content().string("pre-authorized"))
        mockMvc.perform(get("/methods/post-authorize").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
            .andExpect(status().isOk)
            .andExpect(content().string("post-authorized"))
        mockMvc.perform(get("/methods/post-authorize").header(HttpHeaders.AUTHORIZATION, "Bearer missing-role"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/methods/secured").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/methods/secured").header(HttpHeaders.AUTHORIZATION, "Bearer missing-role"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/methods/roles-allowed").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/methods/roles-allowed").header(HttpHeaders.AUTHORIZATION, "Bearer missing-role"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/methods/permit-all").header(HttpHeaders.AUTHORIZATION, "Bearer scope-only"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/methods/deny-all").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `filters MVC method arguments and return values without leaking unauthorized elements`() {
        mockMvc.perform(
            get("/methods/pre-filter")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                .queryParam("value", "allowed-one", "blocked-one", "allowed-two"),
        )
            .andExpect(status().isOk)
            .andExpect(content().json("[\"allowed-one\",\"allowed-two\"]"))

        mockMvc.perform(
            get("/methods/pre-filter")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                .queryParam("value", "blocked-one", "blocked-two"),
        )
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
        assertThat(methodSecurityFixture.lastPreFilterInput).isEmpty()

        mockMvc.perform(get("/methods/post-filter").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
            .andExpect(status().isOk)
            .andExpect(content().json("[\"allowed-one\",\"allowed-two\"]"))

        mockMvc.perform(get("/methods/post-filter-no-match").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
    }

    @Test
    fun `keeps web authentication layered before MVC method authorization`() {
        mockMvc.perform(get("/methods/default-role").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/methods/default-role").header(HttpHeaders.AUTHORIZATION, "Bearer missing-role"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/methods/default-role"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `reuses mapped MVC role and scope authorities in method expressions`() {
        mockMvc.perform(get("/methods/default-role").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/methods/default-role").header(HttpHeaders.AUTHORIZATION, "Bearer missing-role"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/methods/scope").header(HttpHeaders.AUTHORIZATION, "Bearer scope"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/methods/scope").header(HttpHeaders.AUTHORIZATION, "Bearer scp"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/methods/scope").header(HttpHeaders.AUTHORIZATION, "Bearer missing-permission"))
            .andExpect(status().isForbidden)
    }

    @Autowired
    private lateinit var methodSecurityFixture: MvcMethodSecurityFixture

    @Test
    fun `rejects an invalid signature through the common problem contract`() {
        mockMvc.perform(get("/protected").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-signature"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401))
    }

    @Test
    fun `rejects an expired token through the common problem contract`() {
        mockMvc.perform(get("/protected").header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401))
    }

    @Test
    fun `rejects an issuer mismatched token through the common problem contract`() {
        mockMvc.perform(get("/protected").header(HttpHeaders.AUTHORIZATION, "Bearer issuer-mismatch"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401))
    }
}

@SpringBootTest(
    classes = [MvcSecurityFixtureApplication::class],
    webEnvironment = WebEnvironment.MOCK,
    properties = [
        "logistics.parent-service.security.issuer=https://identity.example.test/realms/logistics",
        "logistics.parent-service.security.role-claims-path=realm_access.roles",
        "logistics.parent-service.security.role-prefix=APP_",
    ],
)
@AutoConfigureMockMvc
class MvcSecurityCustomPrefixIntegrationTest(
) {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `applies a custom role prefix in the active MVC bearer flow`() {
        mockMvc.perform(get("/protected").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authorities[0]").value("APP_dispatcher"))
            .andExpect(jsonPath("$.authorities[1]").value("APP_planner"))

        mockMvc.perform(get("/methods/custom-role").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/methods/custom-role").header(HttpHeaders.AUTHORIZATION, "Bearer missing-role"))
            .andExpect(status().isForbidden)
    }
}

@SpringBootTest(
    classes = [MvcSecurityFixtureApplication::class],
    webEnvironment = WebEnvironment.MOCK,
    properties = [
        "logistics.parent-service.security.issuer=https://identity.example.test/realms/logistics",
        "logistics.parent-service.security.public-actuator-endpoints=false",
        "management.endpoints.web.base-path=/manage",
        "management.endpoints.web.exposure.include=health,info",
    ],
)
@AutoConfigureMockMvc
class MvcSecurityActuatorOptOutIntegrationTest(
) {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `protects actuator endpoints when their public default is disabled`() {
        mockMvc.perform(get("/manage/health")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/manage/info")).andExpect(status().isUnauthorized)
    }
}

@SpringBootTest(
    classes = [MvcApplicationOwnedSecurityFixtureApplication::class],
    webEnvironment = WebEnvironment.MOCK,
    properties = ["logistics.parent-service.security.role-claims-path=realm_access.roles"],
)
@AutoConfigureMockMvc
class MvcApplicationOwnedSecurityIntegrationTest(
) {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var securityFilterChains: Map<String, SecurityFilterChain>

    @Test
    fun `backs off platform MVC security when the application supplies its own chain`() {
        assertThat(securityFilterChains).containsOnlyKeys("applicationSecurityFilterChain")
        mockMvc.perform(get("/methods/default-role").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/methods/default-role").header(HttpHeaders.AUTHORIZATION, "Bearer missing-role"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/methods/default-role")).andExpect(status().isUnauthorized)
    }
}

@SpringBootTest(
    classes = [MvcSecurityDisabledFixtureApplication::class],
    webEnvironment = WebEnvironment.MOCK,
    properties = ["logistics.parent-service.security.enabled=false"],
)
@AutoConfigureMockMvc
class MvcSecurityDisabledMethodIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `does not enforce annotated MVC methods when platform security is disabled`() {
        mockMvc.perform(get("/methods/default-role")).andExpect(status().isOk)
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(MvcSecurityFixtureController::class, MvcMethodSecurityFixtureController::class, MvcMethodSecurityFixture::class)
class MvcSecurityFixtureApplication {

    @Bean
    fun jwtDecoder(): JwtDecoder = JwtDecoder { token ->
        when (token) {
            "trusted", "valid" -> mockJwt(
                tokenValue = token,
                claims = mapOf("realm_access" to mapOf("roles" to listOf("dispatcher", "planner"))),
            )
            "nested-string" -> mockJwt(
                tokenValue = token,
                claims = mapOf("realm_access" to mapOf("roles" to "driver")),
            )
            "missing-role" -> mockJwt(
                tokenValue = token,
                claims = mapOf("realm_access" to mapOf("roles" to listOf("planner"))),
            )
            "scope-only" -> mockJwt(
                tokenValue = token,
                claims = mapOf("scope" to "shipments.read"),
            )
            "scope" -> mockJwt(
                tokenValue = token,
                claims = mapOf("scope" to "shipments.read"),
            )
            "scp" -> mockJwt(
                tokenValue = token,
                claims = mapOf("scp" to listOf("shipments.read")),
            )
            "missing-permission" -> mockJwt(
                tokenValue = token,
                claims = mapOf("scope" to "shipments.write"),
            )
            "invalid-signature" -> throw BadJwtException("invalid signature")
            "expired" -> throw BadJwtException("token expired")
            "issuer-mismatch" -> throw BadJwtException("issuer mismatch")
            else -> throw BadJwtException("unknown test token")
        }
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(MvcSecurityFixtureController::class, MvcMethodSecurityFixtureController::class, MvcMethodSecurityFixture::class)
class MvcApplicationOwnedSecurityFixtureApplication {

    @Bean
    fun jwtDecoder(): JwtDecoder = MvcSecurityFixtureApplication().jwtDecoder()

    @Bean
    fun applicationSecurityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
        properties: PlatformProperties,
    ): SecurityFilterChain {
        val authenticationConverter = PlatformJwtAuthenticationConverter(RoleClaimsAuthorityMapper(properties.security))

        return http
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt ->
                    jwt.decoder(jwtDecoder).jwtAuthenticationConverter(authenticationConverter)
                }
            }
            .build()
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(MvcMethodSecurityFixtureController::class, MvcMethodSecurityFixture::class)
class MvcSecurityDisabledFixtureApplication {

    @Bean
    fun applicationSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http.authorizeHttpRequests { it.anyRequest().permitAll() }.build()
}

@RestController
class MvcSecurityFixtureController {

    @GetMapping("/public/ping")
    fun publicEndpoint(): Map<String, String> = mapOf("status" to "ok")

    @GetMapping("/protected")
    fun protectedEndpoint(authentication: Authentication?): Map<String, List<String>> =
        mapOf("authorities" to authentication?.authorities?.mapNotNull { it.authority }.orEmpty())
}

@RestController
class MvcMethodSecurityFixtureController(
    private val methodSecurityFixture: MvcMethodSecurityFixture,
) {

    @GetMapping("/methods/pre-authorize")
    fun preAuthorize(): String = methodSecurityFixture.preAuthorize()

    @GetMapping("/methods/post-authorize")
    fun postAuthorize(): String = methodSecurityFixture.postAuthorize()

    @GetMapping("/methods/pre-filter")
    fun preFilter(@RequestParam("value") value: List<String>): List<String> =
        methodSecurityFixture.preFilter(value)

    @GetMapping("/methods/post-filter")
    fun postFilter(): List<String> = methodSecurityFixture.postFilter()

    @GetMapping("/methods/post-filter-no-match")
    fun postFilterNoMatch(): List<String> = methodSecurityFixture.postFilterNoMatch()

    @GetMapping("/methods/secured")
    fun secured(): String = methodSecurityFixture.secured()

    @GetMapping("/methods/roles-allowed")
    fun rolesAllowed(): String = methodSecurityFixture.rolesAllowed()

    @GetMapping("/methods/permit-all")
    fun permitAll(): String = methodSecurityFixture.permitAll()

    @GetMapping("/methods/deny-all")
    fun denyAll(): String = methodSecurityFixture.denyAll()

    @GetMapping("/methods/default-role")
    fun defaultRole(): String = methodSecurityFixture.defaultRole()

    @GetMapping("/methods/custom-role")
    fun customRole(): String = methodSecurityFixture.customRole()

    @GetMapping("/methods/scope")
    fun scope(): String = methodSecurityFixture.scope()
}

@Service
class MvcMethodSecurityFixture {

    var lastPreFilterInput: List<String> = emptyList()

    @PreAuthorize("hasAuthority('ROLE_dispatcher')")
    fun preAuthorize(): String = "pre-authorized"

    @PostAuthorize("hasAuthority('ROLE_dispatcher') and returnObject == 'post-authorized'")
    fun postAuthorize(): String = "post-authorized"

    @PreFilter("filterObject.startsWith('allowed')")
    fun preFilter(value: List<String>): List<String> {
        lastPreFilterInput = value
        return value
    }

    @PostFilter("filterObject.startsWith('allowed')")
    fun postFilter(): List<String> = listOf("allowed-one", "blocked-one", "allowed-two")

    @PostFilter("filterObject.startsWith('allowed')")
    fun postFilterNoMatch(): List<String> = listOf("blocked-one", "blocked-two")

    @Secured("ROLE_dispatcher")
    fun secured(): String = "secured"

    @RolesAllowed("dispatcher")
    fun rolesAllowed(): String = "roles-allowed"

    @PermitAll
    fun permitAll(): String = "permit-all"

    @DenyAll
    fun denyAll(): String = "deny-all"

    @PreAuthorize("hasAuthority('ROLE_dispatcher')")
    fun defaultRole(): String = "default-role"

    @PreAuthorize("hasAuthority('APP_dispatcher')")
    fun customRole(): String = "custom-role"

    @PreAuthorize("hasAuthority('SCOPE_shipments.read')")
    fun scope(): String = "scope"
}
