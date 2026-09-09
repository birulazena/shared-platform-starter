# Quickstart: Validate Automatic Method Security

## Prerequisites

- JDK 21 available to Gradle;
- repository dependencies resolved;
- no Keycloak/Testcontainers setup is required: the suites use controlled mock JWT decoders.

Run commands from the repository root.

## 1. Fast auto-configuration matrix

```bash
./gradlew :logistics-parent-service-autoconfigure:test \
  --tests '*MethodSecurityAutoConfigurationAnnotationTest' \
  --tests '*AutoConfigurationSelectionTest' \
  --tests '*SecurityAutoConfigurationContextTest' \
  --tests '*CapabilityBackOffTest'
```

Expected evidence:

- the imports registry contains both method auto-configurations immediately after their matching MVC/WebFlux web-security entries;
- a non-web context contributes neither method mechanism;
- an explicitly selected Servlet context contains MVC method infrastructure only, and an explicitly selected Reactive context contains reactive method infrastructure only, including when both APIs are visible to the test;
- the annotation unit tests prove the exact classpath conditions, property condition, ordering, and manual-owner sentinel names;
- absent required method-security classes safely back off;
- `security.enabled=false` contributes neither platform chain nor method infrastructure while non-security capabilities remain;
- a custom web chain does not remove the selected method mechanism; matching manually enabled method security prevents duplicate platform infrastructure for MVC sentinels `_prePostMethodSecurityConfiguration`, `_securedMethodSecurityConfiguration`, `_jsr250MethodSecurityConfiguration` and reactive sentinels `_reactiveMethodSecurityConfiguration`, `reactiveMethodSecurityConfiguration`, `methodSecurityInterceptor`.

## 2. MVC automatic method-security contract

```bash
./gradlew :logistics-parent-service-autoconfigure:mvcIntegrationTest \
  --tests '*MvcSecurityIntegrationTest' \
  --tests '*MvcSecurityCustomPrefixIntegrationTest' \
  --tests '*MvcSecurityActuatorOptOutIntegrationTest' \
  --tests '*MvcApplicationOwnedSecurityIntegrationTest' \
  --tests '*MvcSecurityDisabledMethodIntegrationTest' \
  --tests '*MvcProblemDetailIntegrationTest'
```

Expected evidence without any fixture `@EnableMethodSecurity` annotation:

- all eight MVC annotation families have their standard behavior;
- `@PreFilter` and `@PostFilter` produce empty collections when no element is authorized, without leaking an unauthorized element;
- nested configured roles, default/custom prefixes, `scope`, and `scp` satisfy the corresponding `ROLE_`/`SCOPE_` expressions;
- required authority is `200`, missing authority is `403`, and no token is `401` before protected method invocation;
- method-denial responses preserve `application/problem+json`, RFC 7807 fields, and a 32-character hexadecimal `traceId`;
- a bearer-authenticating application `SecurityFilterChain` is the only chain bean yet method authorization remains active;
- a `security.enabled=false` fixture with independently owned permit-all web security executes an annotated deny method, proving no platform advisor was registered.

## 3. WebFlux automatic method-security contract

```bash
./gradlew :logistics-parent-service-autoconfigure:webfluxIntegrationTest \
  --tests '*WebFluxSecurityIntegrationTest' \
  --tests '*WebFluxSecurityCustomPrefixIntegrationTest' \
  --tests '*WebFluxSecurityActuatorOptOutIntegrationTest' \
  --tests '*WebFluxApplicationOwnedSecurityIntegrationTest' \
  --tests '*WebFluxSecurityDisabledMethodIntegrationTest' \
  --tests '*WebFluxProblemDetailIntegrationTest'
```

Expected evidence without any fixture `@EnableReactiveMethodSecurity` annotation:

- publisher-returning `@PreAuthorize` and `@PostAuthorize` methods authorize existing role and scope authorities;
- delayed, empty, and scheduled publishers retain the reactive security context and do not admit an unrelated or absent context, with explicit value/completion/rejection assertions;
- the 200/403/401 matrix matches MVC;
- method-denial responses preserve `application/problem+json`, RFC 7807 fields, and a 32-character hexadecimal `traceId`;
- a bearer-authenticating application `SecurityWebFilterChain` replaces only the platform chain, not reactive method authorization;
- disabled platform security leaves an annotated method unenforced by the platform while independent application security continues to own requests.

## 4. Full regression gate

```bash
./gradlew check
```

Expected result: all existing unit, MVC, WebFlux, logging, BOM, and thin-starter checks pass. Confirm that no Gradle module or dependency declaration was added and that the starter remains web-stack-neutral.
