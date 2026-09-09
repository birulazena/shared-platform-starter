# Implementation Plan: Automatic Method Security Configuration

**Branch**: `002-method-security-auto-configuration` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-method-security-auto-configuration/spec.md`

## Summary

Add two stack-isolated Spring Boot auto-configurations to the existing auto-configure module. The Servlet branch will enable Spring Security MVC method authorization with `@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)`; the reactive branch will enable its publisher-aware counterpart with `@EnableReactiveMethodSecurity`. Both branches use the existing `logistics.parent-service.security.enabled` switch, selected web application type, and string-based classpath conditions. They do not create a web chain, JWT converter, role mapper, property type, or module.

The new configurations are ordered immediately after their matching platform web-security configurations. This keeps request authentication and method authorization as separate layers: an application-owned `SecurityFilterChain` or `SecurityWebFilterChain` still replaces only the platform web chain, while the selected platform method-security configuration remains eligible.

## Technical Context

**Language/Version**: Kotlin 2.3.21; Java 21 bytecode/toolchain

**Primary Dependencies**: Spring Boot 4.1.0 auto-configuration and test support; Spring Security configuration/resource server (Boot-managed); Spring MVC or WebFlux selected by the consumer; Reactor for the reactive branch

**Storage**: N/A — this feature creates no persistent data or domain model

**Testing**: JUnit 6, AssertJ, reflection-level auto-configuration unit tests, Spring Boot context runners, `MockMvc`, `WebTestClient`, and controlled mock JWT decoders

**Target Platform**: JVM services built with Gradle Kotlin DSL on Java 21

**Project Type**: Reusable three-module Kotlin/Spring Boot platform library

**Performance Goals**: Preserve Spring Security's standard proxy-based method authorization; no feature-specific throughput or latency target

**Constraints**:

- Keep exactly the existing BOM, `autoconfigure`, and thin `starter` modules; do not add a module or a web-specific starter.
- Do not make a consumer `@EnableMethodSecurity` or `@EnableReactiveMethodSecurity` annotation mandatory.
- Create no infrastructure for the non-selected web stack, even when both API families are on a broad test classpath.
- Gate both method branches on `security.enabled`, web application type, and their own relevant Spring Security and web-stack classes.
- Preserve `PlatformProperties`, `SecurityProperties`, `RoleClaimsAuthorityMapper`, and `PlatformJwtAuthenticationConverter` unchanged so method expressions see the existing `ROLE_` and `SCOPE_` authorities.

**Scale/Scope**: Two new auto-configuration classes, one imports-registry change, targeted unit/context/adoption tests in the existing suites, removal of two test-only manual-enablement annotations, and documentation/compatibility updates. No consumer configuration property is added. Unit coverage includes reflection assertions for both new configuration classes and their conditions.

## Constitution Check

### Pre-research gate — PASS

| Constitution concern | Evidence and plan response |
|---|---|
| Three-module platform and thin starter | Both classes belong in the existing `logistics-parent-service-autoconfigure` module. Its existing `spring-security-config` compile-only dependency is sufficient; the starter stays source-free and web-neutral. |
| Secure-by-default and JWT compatibility | Method authorization consumes the authenticated authorities already created by the selected resource-server chain. No issuer validation, default endpoint rule, role claim path, role prefix, or scope mapping changes. |
| Web-stack neutrality and safe auto-configuration | Separate Servlet and Reactive classes repeat the current classpath, selected-web-type, and property gating pattern. They do not condition on a web-chain bean. |
| Stable error/API behavior and quality | Existing MVC/WebFlux ProblemDetail handlers already map method authorization failures. Context and full selected-stack integration coverage will prove conditions, back-off, and 200/401/403 behavior. |
| Documentation and compatibility discipline | The plan creates feature design artifacts and schedules updates to the repository README plus the stable security, quickstart, and compatibility documents. |

No constitutional exception, new dependency, module, public property, or breaking contract is required.

### Post-design gate — PASS

The research and design below retain all five constitutional principles. The only new behavior is the additive, automatically enabled method-authorization layer for already security-enabled web applications. Tests explicitly cover both stacks, class/application-type isolation, application-owned web chains, and security disablement.

## Project Structure

### Documentation (this feature)

```text
specs/002-method-security-auto-configuration/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/
│   └── security.md
├── quickstart.md
└── compatibility-review.md
```

`tasks.md` is intentionally not created by this planning workflow.

### Source Code (repository root)

```text
logistics-parent-service-autoconfigure/
├── build.gradle.kts                              # no change expected
├── src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/
│   ├── PlatformAutoConfiguration.kt              # existing shared foundation
│   └── security/
│       ├── PlatformJwtAuthenticationConverter.kt # retained unchanged
│       ├── RoleClaimsAuthorityMapper.kt          # retained unchanged
│       ├── mvc/
│       │   ├── PlatformMvcSecurityAutoConfiguration.kt
│       │   └── PlatformMvcMethodSecurityAutoConfiguration.kt        # new
│       └── reactive/
│           ├── PlatformWebFluxSecurityAutoConfiguration.kt
│           └── PlatformWebFluxMethodSecurityAutoConfiguration.kt    # new
├── src/main/resources/META-INF/spring/
│   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
├── src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/
│   ├── AutoConfigurationSelectionTest.kt
│   ├── CapabilityBackOffTest.kt
│   └── security/SecurityAutoConfigurationContextTest.kt
├── src/mvcIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/
│   ├── security/MvcSecurityIntegrationTest.kt
│   └── errors/MvcProblemDetailIntegrationTest.kt
└── src/webfluxIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/
    ├── security/WebFluxSecurityIntegrationTest.kt
    └── errors/WebFluxProblemDetailIntegrationTest.kt

README.md
specs/001-shared-platform-starter/
├── contracts/security.md
├── quickstart.md
└── compatibility-review.md
```

**Structure Decision**: Extend the current stack-specific packages and the existing selected-stack test source sets. Do not create a Gradle module, a separate starter, a cross-stack configuration class, or business code.

## Detailed Design

### Auto-configuration classes and conditions

1. Create `security.mvc.PlatformMvcMethodSecurityAutoConfiguration` with:

   - `@AutoConfiguration` and `@AutoConfigureAfter` for `PlatformAutoConfiguration` and `PlatformMvcSecurityAutoConfiguration`;
   - `@ConditionalOnWebApplication(type = SERVLET)`;
   - the same `@ConditionalOnProperty(prefix = "logistics.parent-service.security", name = ["enabled"], havingValue = "true", matchIfMissing = true)` used by the MVC web security configuration;
   - string-name `@ConditionalOnClass` checks for `org.springframework.web.servlet.DispatcherServlet`, `org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity`, `org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor`, `org.springframework.security.authorization.method.AuthorizationManagerAfterMethodInterceptor`, `org.springframework.security.authorization.method.PreFilterAuthorizationMethodInterceptor`, `org.springframework.security.authorization.method.PostFilterAuthorizationMethodInterceptor`, and `jakarta.annotation.security.RolesAllowed`;
   - `@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)`. Pre/post security remains enabled by Spring Security's default. The two explicit flags are required for `@Secured`, `@RolesAllowed`, `@PermitAll`, and `@DenyAll`.

2. Create `security.reactive.PlatformWebFluxMethodSecurityAutoConfiguration` with:

   - `@AutoConfiguration` and `@AutoConfigureAfter` for `PlatformAutoConfiguration` and `PlatformWebFluxSecurityAutoConfiguration`;
   - `@ConditionalOnWebApplication(type = REACTIVE)`;
   - the identical security-enabled property condition;
   - string-name `@ConditionalOnClass` checks for `org.springframework.web.reactive.DispatcherHandler`, `org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity`, `org.springframework.security.authorization.method.AuthorizationManagerBeforeReactiveMethodInterceptor`, `org.springframework.security.authorization.method.AuthorizationManagerAfterReactiveMethodInterceptor`, `org.springframework.security.authorization.method.PreFilterAuthorizationReactiveMethodInterceptor`, `org.springframework.security.authorization.method.PostFilterAuthorizationReactiveMethodInterceptor`, and `reactor.core.publisher.Mono`;
   - `@EnableReactiveMethodSecurity`, using Spring Security's standard reactive authorization-manager implementation for publisher-returning methods.

Neither class declares a `SecurityFilterChain`, `SecurityWebFilterChain`, JWT decoder, converter, mapper, property bean, error handler, or a reference to the opposite web stack. `PlatformProperties.security` continues to provide only the existing enablement input; all authority creation remains in `PlatformJwtAuthenticationConverter` and `RoleClaimsAuthorityMapper`.

### Auto-configuration ordering

Keep the existing foundation/capability ordering and insert each method branch immediately after its matching web branch in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

1. `PlatformAutoConfiguration`
2. `PlatformTracingAutoConfiguration`
3. `PlatformMetricsAutoConfiguration`
4. `PlatformMvcSecurityAutoConfiguration`
5. `PlatformMvcMethodSecurityAutoConfiguration` **(new)**
6. `PlatformWebFluxSecurityAutoConfiguration`
7. `PlatformWebFluxMethodSecurityAutoConfiguration` **(new)**
8. `PlatformMvcErrorAutoConfiguration`
9. `PlatformWebFluxErrorAutoConfiguration`
10. `PlatformLoggingAutoConfiguration`

The method configurations are deliberately not nested in the filter-chain bean methods and have no `@ConditionalOnMissingBean(SecurityFilterChain)` or `@ConditionalOnMissingBean(SecurityWebFilterChain)`. Therefore a complete application-owned HTTP chain backs off only the platform's HTTP chain—the existing contract—rather than disabling method authorization.

### Duplicate-enablement compatibility decision

Spring Security's enablement annotations import named infrastructure, including the MVC `_prePostMethodSecurityConfiguration`, `_securedMethodSecurityConfiguration`, and `_jsr250MethodSecurityConfiguration` beans, and the reactive `_reactiveMethodSecurityConfiguration` bean. Legacy reactive enablement with `useAuthorizationManager = false` registers the generated `reactiveMethodSecurityConfiguration` configuration bean and its `methodSecurityInterceptor` advisor. Enabling both mechanisms in one selected-stack context would therefore risk duplicate named beans and duplicate advisors.

The platform auto-configurations will use one `@ConditionalOnMissingBean(name = [...])` declaration per branch. Spring Boot evaluates the named set as an “any present” check for this condition: the MVC branch must back off when any of `_prePostMethodSecurityConfiguration`, `_securedMethodSecurityConfiguration`, or `_jsr250MethodSecurityConfiguration` is present; the reactive branch must back off when any of `_reactiveMethodSecurityConfiguration`, `reactiveMethodSecurityConfiguration`, or `methodSecurityInterceptor` is present. The annotation values and this observable “back off if any selected-stack sentinel exists” behavior are pinned by unit and context tests.

The guards must not inspect `SecurityFilterChain` or `SecurityWebFilterChain`, and they must not treat an opposite-stack method-security sentinel as ownership of the selected branch. This has three effects:

- a consumer needs no enablement annotation in the normal path;
- a consumer that still deliberately declares the matching enablement annotation is treated as the owner of method-security customization, and the platform branch backs off without duplicate infrastructure; and
- an application-owned web chain alone cannot trigger this guard.

Pin this decision with context tests. Remove the two existing test-fixture manual annotations—especially the MVC annotation currently used in the WebFlux ProblemDetail fixture—so platform tests prove the automatic path rather than masking it with consumer configuration.

## Implementation Sequence (TDD)

All test changes below are written and observed failing before the matching production changes. Do not add a new Gradle module or dependency declaration.

1. **Write failing unit and auto-configuration selection tests.** Add `MethodSecurityAutoConfigurationAnnotationTest` under `src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/security/` with reflection assertions for both new classes: `@AutoConfiguration`, selected `@ConditionalOnWebApplication`, the exact security property condition, exact `@ConditionalOnClass` names, `@AutoConfigureAfter`, and the stack-specific missing-bean sentinel names/semantics. Update `AutoConfigurationSelectionTest` so its exact imports assertion includes both new classes in the documented order. Add condition-report assertions for non-web, Servlet, Reactive, and dual-API/explicit-type contexts. Assert that only the selected method auto-configuration is a full match. Use a filtered test class loader for each listed method-security class to prove the relevant classpath condition backs off safely.

2. **Write failing fast security-context tests.** Extend `SecurityAutoConfigurationContextTest` to pass both new auto-configurations to the Servlet and Reactive runners. Assert the selected infrastructure marker (`_prePostMethodSecurityConfiguration` for MVC or `_reactiveMethodSecurityConfiguration` for WebFlux) exists and the opposite marker does not. Add cases proving:

   - an application `SecurityFilterChain` or `SecurityWebFilterChain` removes only the platform chain and leaves the selected method infrastructure;
   - `security.enabled=false` removes both platform chain and selected method infrastructure while shared correlation and ProblemDetail beans remain;
   - an application-owned matching manual method-security configuration causes the new platform branch to back off cleanly for each named sentinel, documenting the duplicate-infrastructure compatibility rule; a web-chain bean alone does not cause method-security back-off.

   Extend `CapabilityBackOffTest` with the same disabled-security assertion in the all-capabilities context so the security flag remains scoped to security only.

3. **Write failing MVC adoption tests first.** In `MvcSecurityIntegrationTest`, introduce test-only `MvcMethodSecurityFixtureService` and `MvcMethodSecurityFixtureController` (or equivalent focused fixture types) with no `@EnableMethodSecurity` on the automatic-path fixture. Cover all eight required annotation families:

   - `@PreAuthorize` and `@PostAuthorize` for an existing `ROLE_` authority;
   - `@PreFilter` and `@PostFilter` with caller-owned collection elements, including the no-match case where the service receives and returns an empty collection, and an assertion that unauthorized elements do not reach/leave the service;
   - `@Secured` and `@RolesAllowed` with the existing role vocabulary;
   - `@PermitAll` and `@DenyAll`, while still proving the HTTP layer returns `401` before an unauthenticated request reaches the service.

   Add a matching consumer `@EnableMethodSecurity` fixture with pre/post, secured, and JSR-250 options to prove that the platform backs off on each corresponding sentinel without duplicate advisors or interceptors. Assert the method-denial ProblemDetail response has `application/problem+json`, `type`, `title`, `status`, `detail`, `instance`, and a 32-character `traceId`.

   Extend the controlled decoder tokens with nested roles plus `scope` and `scp` claims; assert `ROLE_<role>` and `SCOPE_<permission>` expressions both allow matching callers and reject missing authorities with `403`. Add a disabled-security fixture with an application-owned permit-all web chain and an annotated deny method: it must execute, proving no platform method advisor was contributed. Upgrade the current `MvcApplicationOwnedSecurityFixtureApplication` to a bearer-authenticating application chain using the existing decoder/converter/role-mapper behavior; assert exactly its chain is present, an authorized token returns `200`, a token lacking the method authority returns `403`, and no token follows the application's `401` policy.

4. **Write failing WebFlux adoption tests first.** Mirror the MVC proof in `WebFluxSecurityIntegrationTest` with `WebFluxMethodSecurityFixtureService` and `WebFluxMethodSecurityFixtureController`, both free of `@EnableReactiveMethodSecurity` on the automatic-path fixture. Cover publisher-returning `@PreAuthorize` and `@PostAuthorize` methods for role and `scope`/`scp` authorities. Include delayed, empty, and scheduled publisher cases with explicit assertions for authorized values, empty completion, retained Reactor context, and missing-authority rejection. Add a matching consumer `@EnableReactiveMethodSecurity` fixture for both authorization-manager and legacy mode sentinels, proving the platform backs off without duplicate advisors/interceptors. Assert `200`/`403`/`401` outcomes, a platform-disabled annotated-deny fixture that executes under application-owned permit-all security, a bearer-authenticating application-owned `SecurityWebFilterChain` that backs off only the platform chain, and the method-denial ProblemDetail contract (`application/problem+json`, `type`, `title`, `status`, `detail`, `instance`, and 32-character `traceId`).

5. **Make the minimal production change.** Add the two classes described in the auto-configuration design and insert their import entries in the exact order above. Do not modify `PlatformMvcSecurityAutoConfiguration`, `PlatformWebFluxSecurityAutoConfiguration`, `PlatformProperties`, `SecurityProperties`, `RoleClaimsAuthorityMapper`, or `PlatformJwtAuthenticationConverter` unless a red test proves a genuine compatibility defect outside this design.

6. **Remove legacy manual fixture enablement.** Delete `@EnableMethodSecurity` from `MvcProblemDetailFixtureApplication` in `MvcProblemDetailIntegrationTest` and the incorrect MVC `@EnableMethodSecurity` from `WebFluxProblemDetailFixtureApplication` in `WebFluxProblemDetailIntegrationTest`. Their existing method-denial ProblemDetail assertions then become regression coverage for automatic MVC and reactive selection, without creating duplicate configuration.

7. **Run focused tests, then the module/repository regression gate.** The unit/context and selected-stack focused tests may run immediately after T018 and in parallel with documentation. The expected command order is documented in [quickstart.md](./quickstart.md). Keep test fixture names and test source sets unchanged outside the focused additions so `check` continues to execute every suite.

## Documentation and Compatibility Updates

The implementation must update these stable repository documents in addition to the feature artifacts generated here:

- `README.md`: state that security-enabled MVC/WebFlux services receive matching method security automatically, document the annotation families, explain that `security.enabled=false` disables the platform's web *and* method contribution, and refine the web-chain back-off table so a custom chain does not imply method-security back-off.
- `specs/001-shared-platform-starter/contracts/security.md`: add the stack-specific method-security contract, exact enablement conditions, existing JWT authority reuse, custom-chain independence, disabled behavior, manual-enablement compatibility/back-off, and 200/401/403 matrix.
- `specs/001-shared-platform-starter/quickstart.md`: add runnable MVC and WebFlux automatic-method-security validation, including roles/scopes, selected-stack isolation, custom chains, and `security.enabled=false`.
- `specs/001-shared-platform-starter/compatibility-review.md`: classify this as an additive MINOR capability, state that no property/module migration is required, explain the manual-enablement back-off rule, and list regression evidence for both stacks.

The new feature-local [contracts/security.md](./contracts/security.md), [quickstart.md](./quickstart.md), and [compatibility-review.md](./compatibility-review.md) provide the implementation baseline for those updates without changing a published contract during the planning-only workflow.

## Risks and Mitigations

| Risk | Mitigation and proof |
|---|---|
| MVC and reactive method configuration both register similarly named Spring Security advisors/interceptors. | Separate classes have opposite `@ConditionalOnWebApplication` types and opposite-stack-free imports. Condition-report and infrastructure-marker tests run with both API families available. |
| A consumer's manual enablement annotation collides with the starter's automatic one. | Guard each platform class on the appropriate existing framework infrastructure sentinels; test startup and platform back-off for a matching consumer-owned enablement configuration. |
| A custom HTTP chain accidentally disables method security. | Do not condition a method configuration on `SecurityFilterChain` or `SecurityWebFilterChain`; use authenticated custom-chain integration cases that still produce method-level `200`/`403`. |
| `@Secured` and JSR-250 annotations silently remain inactive. | Set `securedEnabled = true` and `jsr250Enabled = true` explicitly and test `@Secured`, `@RolesAllowed`, `@PermitAll`, and `@DenyAll`. |
| Method expressions diverge from endpoint authorities. | Make no mapper/converter changes; test nested roles, default/custom role prefixes, and both `scope` and `scp` as `SCOPE_` authorities in both stacks. |
| `security.enabled=false` is confused with Boot or application-owned security. | Assert absence of the platform method and chain markers; use a distinct application-owned permit-all fixture to prove annotations are not enforced by the platform while shared capabilities remain. |
| Existing WebFlux ProblemDetail test masks the reactive behavior with MVC method security. | Remove its manual MVC enablement annotation and retain its 403 ProblemDetail regression tests under the new reactive auto-configuration. |

## Complexity Tracking

No constitution violation requires justification.
