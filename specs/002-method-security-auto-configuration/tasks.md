---

description: "Actionable task list for automatic MVC and WebFlux method-security configuration"
---

# Tasks: Automatic Method Security Configuration

**Input**: Design documents from `specs/002-method-security-auto-configuration/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/security.md`, and `quickstart.md`

**Tests**: Tests are required by the feature specification. Create and observe the failing tests before adding the production auto-configurations.

**Organization**: Test work is deliberately scheduled before every production change. The implementation tasks remain traceable to the user stories they enable.

## Phase 1: Setup

**Purpose**: Confirm the existing module and source-set boundary before work starts.

- [X] T001 Verify the existing `test`, `mvcIntegrationTest`, and `webfluxIntegrationTest` source sets and retain the three-module build without adding dependencies or a Gradle module in `logistics-parent-service-autoconfigure/build.gradle.kts`.

---

## Phase 2: Foundational Auto-Configuration Tests

**Purpose**: Pin stack selection, conditions, and compatibility behavior before production configuration exists.

- [X] T002 [P] Write failing reflection-level unit tests in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/security/MethodSecurityAutoConfigurationAnnotationTest.kt` for both new configuration classes: `@AutoConfiguration`, selected `@ConditionalOnWebApplication`, the exact `logistics.parent-service.security.enabled` property condition, exact MVC/WebFlux `@ConditionalOnClass` names, `@AutoConfigureAfter`, and the exact `@ConditionalOnMissingBean(name = [...])` sentinel arrays. In `AutoConfigurationSelectionTest.kt`, also write failing import-order, non-web, selected-stack-only, dual-API explicit-type, and missing-method-security-class condition tests.
- [X] T003 Write the failing MVC method-security context test for the `_prePostMethodSecurityConfiguration` marker, absent reactive markers, and continued activation when an application owns `SecurityFilterChain` in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/security/SecurityAutoConfigurationContextTest.kt`.
- [X] T004 Write the failing WebFlux method-security context test for the `_reactiveMethodSecurityConfiguration` marker, absent MVC markers, and continued activation when an application owns `SecurityWebFilterChain` in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/security/SecurityAutoConfigurationContextTest.kt`.
- [X] T005 Write failing disabled-security and matching manual-enablement back-off context tests, proving `security.enabled=false` removes the selected platform method infrastructure and application-owned method security avoids duplicate infrastructure. Cover MVC sentinels `_prePostMethodSecurityConfiguration`, `_securedMethodSecurityConfiguration`, `_jsr250MethodSecurityConfiguration`; reactive authorization-manager sentinel `_reactiveMethodSecurityConfiguration`; and legacy reactive sentinels `reactiveMethodSecurityConfiguration` and `methodSecurityInterceptor`. Also prove that an application-owned web chain alone does not trigger method-security back-off in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/security/SecurityAutoConfigurationContextTest.kt`.
- [X] T006 [P] Extend the selected Servlet disabled-security capability test to assert the MVC method-security marker is absent while tracing, metrics, errors, logging, and correlation stay active in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/CapabilityBackOffTest.kt`.

---

## Phase 3: User Story 1 — Authorize MVC Methods Automatically (Priority: P1) 🎯 MVP

**Goal**: An MVC consumer receives all required method-authorization semantics without declaring `@EnableMethodSecurity`.

**Independent Test**: With the starter and platform security enabled, an MVC fixture lacking a consumer enablement annotation authorizes and denies each required MVC annotation family according to its standard semantics.

### Tests for User Story 1

- [X] T007 [US1] Write failing MVC integration tests using fixture service/controller types with no explicit `@EnableMethodSecurity` for `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, `@PostFilter`, `@Secured`, `@RolesAllowed`, `@PermitAll`, and `@DenyAll` in `logistics-parent-service-autoconfigure/src/mvcIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/security/MvcSecurityIntegrationTest.kt`. Include collection filtering with partial matches and the no-match case: the service receives an empty pre-filtered collection, the response is an empty post-filtered collection, and no unauthorized element reaches or leaves the service.
- [X] T008 [P] [US1] Remove the fixture `@EnableMethodSecurity` and retain failing automatic-method-security ProblemDetail denial coverage in `logistics-parent-service-autoconfigure/src/mvcIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/errors/MvcProblemDetailIntegrationTest.kt`. Assert `403`, `application/problem+json`, nonblank `type`/`title`/`detail`, matching `status`/`instance`, and a 32-character hexadecimal `traceId`.

---

## Phase 4: User Story 2 — Authorize Reactive Methods Automatically (Priority: P1)

**Goal**: A WebFlux consumer receives publisher-aware method authorization without declaring `@EnableReactiveMethodSecurity`.

**Independent Test**: With the starter and platform security enabled, a WebFlux fixture lacking either consumer method-security annotation permits matching reactive `@PreAuthorize`/`@PostAuthorize` calls and rejects missing authorities across delayed, empty, and scheduled publishers.

### Tests for User Story 2

- [X] T009 [US2] Write failing WebFlux integration tests using fixture service/controller types with no explicit `@EnableReactiveMethodSecurity` for publisher-returning `@PreAuthorize` and `@PostAuthorize` in `logistics-parent-service-autoconfigure/src/webfluxIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/security/WebFluxSecurityIntegrationTest.kt`. Include delayed, empty, and scheduled Reactor-context cases with explicit assertions for authorized values, empty completion, retained context, and missing-authority rejection.
- [X] T010 [P] [US2] Remove the incorrect fixture `@EnableMethodSecurity` and retain failing reactive automatic-method-security ProblemDetail denial coverage in `logistics-parent-service-autoconfigure/src/webfluxIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/errors/WebFluxProblemDetailIntegrationTest.kt`. Assert `403`, `application/problem+json`, nonblank `type`/`title`/`detail`, matching `status`/`instance`, and a 32-character hexadecimal `traceId`.

---

## Phase 5: User Story 3 — Preserve Layered Web and Method Authorization (Priority: P1)

**Goal**: HTTP-chain ownership and automatic method authorization remain independent policy layers on both web stacks.

**Independent Test**: Each selected stack returns `200` for an authorized token, `403` for a valid token without the method authority, and `401` before method invocation for no token; a bearer-authenticating application-owned chain remains the only HTTP chain without disabling method authorization.

### Tests for User Story 3

- [X] T011 [US3] Write failing MVC integration tests for the `200`/`403`/`401` authorization matrix, a bearer-authenticating application-owned `SecurityFilterChain` that backs off only the platform chain, and an annotated method that executes with `security.enabled=false` under application-owned permit-all security in `logistics-parent-service-autoconfigure/src/mvcIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/security/MvcSecurityIntegrationTest.kt`.
- [X] T012 [US3] Write failing WebFlux integration tests for the `200`/`403`/`401` authorization matrix, a bearer-authenticating application-owned `SecurityWebFilterChain` that backs off only the platform chain, and an annotated method that executes with `security.enabled=false` under application-owned permit-all security in `logistics-parent-service-autoconfigure/src/webfluxIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/security/WebFluxSecurityIntegrationTest.kt`.

---

## Phase 6: User Story 4 — Reuse Existing JWT Authorities in Method Expressions (Priority: P1)

**Goal**: Method expressions consume the established mapped role and scope authorities unchanged.

**Independent Test**: Controlled JWTs carrying nested roles or `scope`/`scp` claims produce matching `ROLE_`/configured-prefix and `SCOPE_` decisions in each selected-stack method fixture, while missing authorities are rejected.

### Tests for User Story 4

- [X] T013 [US4] Write failing MVC role-based and scope/permission-based method-expression integration tests for configured nested roles, the default `ROLE_` prefix, the existing custom `APP_` prefix, and both `scope` and `scp` claims, including missing-authority rejection, in `logistics-parent-service-autoconfigure/src/mvcIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/security/MvcSecurityIntegrationTest.kt`.
- [X] T014 [US4] Write failing WebFlux role-based and scope/permission-based method-expression integration tests for configured nested roles, the default `ROLE_` prefix, the existing custom `APP_` prefix, and both `scope` and `scp` claims, including missing-authority rejection, in `logistics-parent-service-autoconfigure/src/webfluxIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/security/WebFluxSecurityIntegrationTest.kt`.

---

## Phase 7: Shared Implementation

**Purpose**: Add the minimum stack-isolated production configuration only after tasks T002–T014 are present and failing.

- [X] T015 Run the newly added unit, auto-configuration/context, MVC, and WebFlux tests to confirm they fail before production implementation, using the commands in `specs/002-method-security-auto-configuration/quickstart.md`.
- [X] T016 [US1] Implement MVC automatic method security with `@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)`, `SERVLET`/property conditions, exact classpath names from `plan.md`, and missing-bean guards that back off if any of `_prePostMethodSecurityConfiguration`, `_securedMethodSecurityConfiguration`, or `_jsr250MethodSecurityConfiguration` is present. Do not condition on `SecurityFilterChain`; order after MVC web security in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/security/mvc/PlatformMvcMethodSecurityAutoConfiguration.kt`.
- [X] T017 [US2] Implement reactive automatic method security with `@EnableReactiveMethodSecurity`, `REACTIVE`/property conditions, exact classpath names from `plan.md`, and missing-bean guards that back off if any of `_reactiveMethodSecurityConfiguration`, `reactiveMethodSecurityConfiguration`, or `methodSecurityInterceptor` is present. Do not condition on `SecurityWebFilterChain`; order after WebFlux web security in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/security/reactive/PlatformWebFluxMethodSecurityAutoConfiguration.kt`.
- [X] T018 Register the MVC and WebFlux method-security auto-configurations immediately after their matching web-security entries in `logistics-parent-service-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

---

## Phase 8: Documentation and Compatibility

**Purpose**: Publish the automatic-enablement, authority, back-off, disablement, and compatibility contract after the implementation is in place.

- [X] T019 [P] Document automatic MVC/WebFlux method security, supported annotation families, `security.enabled=false`, and custom HTTP-chain independence in `README.md`.
- [X] T020 [P] Update the stable method-security contract with stack conditions, existing role/scope authority reuse, manual-enablement back-off, custom-chain independence, disabled behavior, and the `200`/`401`/`403` matrix in `specs/001-shared-platform-starter/contracts/security.md`.
- [X] T021 [P] Add runnable automatic method-security validation scenarios for both stacks, role/scope claims, selected-stack isolation, custom chains, and disabled security in `specs/001-shared-platform-starter/quickstart.md`.
- [X] T022 [P] Create the feature compatibility baseline and classify the change as additive MINOR with no module/property migration, then update the stable release review in `specs/002-method-security-auto-configuration/compatibility-review.md` and `specs/001-shared-platform-starter/compatibility-review.md`.

---

## Phase 9: Validation

**Purpose**: Prove the focused acceptance matrix after implementation. These validations may run in parallel with documentation; the complete repository regression gate waits for both.

- [X] T023 [P] Run the focused unit and auto-configuration/context Gradle tests for exact annotation metadata, stack selection, method-security markers, disabled security, matching manual-enablement back-off, web-chain independence, and missing-class conditions using `specs/002-method-security-auto-configuration/quickstart.md`.
- [X] T024 [P] Run the focused MVC Gradle integration tests for automatic annotation coverage including no-match filtering, role/scope decisions, matching manual enablement, ProblemDetail fields/traceId, layered `200`/`401`/`403` behavior, custom-chain ownership, and disabled security using `specs/002-method-security-auto-configuration/quickstart.md`.
- [X] T025 [P] Run the focused WebFlux Gradle integration tests for reactive delayed/empty/scheduled context propagation, role/scope decisions, matching manual enablement, ProblemDetail fields/traceId, layered `200`/`401`/`403` behavior, custom-chain ownership, and disabled security using `specs/002-method-security-auto-configuration/quickstart.md`.
- [ ] T026 Run the full `./gradlew check` regression gate after documentation and focused validations pass, using the root build definition in `build.gradle.kts`.

---

## Dependencies & Execution Order

### Stage dependencies

```text
T001 (setup)
  └── T002–T014 (all failing tests)
        └── T015 (red-test confirmation)
              ├── T016 (MVC implementation) ─┐
              └── T017 (WebFlux implementation) ─┼── T018 (imports registry)
                                                   ├── T019–T022 (documentation) ───────┐
                                                   └── T023–T025 (focused Gradle validation) ─┼── T026 (full Gradle check)
```

### Task dependencies

- T002 and T006 can start after T001; T003–T005 are sequential changes to the same context-test file.
- T007–T014 start after T002–T006 define the shared selection and context expectations; they are intentionally complete before T015 so no production task masks a missing test.
- T016 depends on T002, T003, T005, T007, T008, T011, T013, and T015; T017 depends on T002, T004, T005, T009, T010, T012, T014, and T015.
- T018 depends on T002, T016, and T017.
- T019–T022 depend on T018 and define the documentation criteria that the release review must publish.
- T023–T025 depend only on T018 and are independent focused validation commands; T026 depends on T019–T025.

### User-story dependencies

- **US1 (P1)**: Depends on the foundational test phase and T016; it is independently testable with the MVC fixture.
- **US2 (P1)**: Depends on the foundational test phase and T017; it is independently testable with the WebFlux fixture.
- **US3 (P1)**: Reuses US1/US2 method mechanisms, but its MVC and WebFlux HTTP-chain assertions are independently runnable after T018.
- **US4 (P1)**: Reuses the existing JWT converter/role mapper without production changes; its method-expression assertions run independently after T018.

## Parallel Opportunities

- T002 and T006 can run concurrently after setup because they modify different test files.
- T008 and T010 can run concurrently with their main-stack test work because they modify independent ProblemDetail suites.
- T016 and T017 can run concurrently after T015 because they create isolated MVC and WebFlux source files.
- T019–T022 can run concurrently once T018 is complete because they target separate documentation files.
- T023–T025 can run concurrently after T018 and in parallel with T019–T022; each invokes a distinct focused Gradle test target.

## Implementation Strategy

### MVP first

1. Complete T001–T015 to establish the full red test suite before any auto-configuration is added.
2. Complete T016–T018 as the atomic stack-isolated platform installation, then use T023–T025 to validate the implementation independently of documentation.
3. Complete the documentation and both-stack regression work through T026 before release.

### Incremental delivery

1. Establish selected-stack and disabled/back-off behavior with T001–T006.
2. Add all MVC and WebFlux acceptance tests (T007–T014) and prove the red state (T015).
3. Add the two isolated configurations and registry entry (T016–T018).
4. Publish the stable contract, complete focused validation, and run the repository gate (T019–T026).

## Phase 10: Convergence

- [X] T027 Add filtered-classloader condition cases for every MVC and WebFlux class listed in the plan's `@ConditionalOnClass` contract, asserting that the corresponding method-security auto-configuration safely backs off when each individual required class is absent per plan: classpath conditions (partial)
- [X] T028 Add isolated matching manual-enablement context cases for each MVC sentinel and for the reactive authorization-manager and legacy sentinel variants, asserting that the selected platform method-security auto-configuration backs off without duplicate infrastructure while a web-chain bean alone remains a full match per FR-015 (partial)

## Phase 11: Convergence

- [X] T029 Implement the MVC automatic method-security auto-configuration with the selected Servlet/property conditions, required classpath guards, matching manual-enablement sentinels, and enabled annotation families so the phase 3 MVC authorization, phase 5 layered-chain, and phase 6 mapped-authority scenarios execute per US1/AC1–4 and FR-002 (missing)
- [X] T030 Implement the WebFlux automatic method-security auto-configuration with the selected Reactive/property conditions, required classpath guards, matching manual-enablement sentinels, and publisher-aware authorization so the phase 4 reactive, phase 5 layered-chain, and phase 6 mapped-authority scenarios execute per US2/AC1–3 and FR-003 (missing)
- [X] T031 Register the MVC and WebFlux method-security auto-configurations immediately after their matching web-security entries in the imports registry so selected-stack activation and isolation are discoverable per the plan ordering decision and FR-001/FR-004 (missing)
- [X] T032 Add a WebFlux integration assertion that a token missing the required authority is rejected on a publisher-returning `@PostAuthorize` method, complementing the existing successful path per US2/AC1 (partial)

## Phase 12: Convergence

- [X] T033 Replace the incompatible `@EnableReactiveMethodSecurity(useAuthorizationManager = false)` legacy fixture with executable named-sentinel context fixtures, and assert back-off for both `reactiveMethodSecurityConfiguration` and `methodSecurityInterceptor` without the missing `MethodSecurityMetadataSource` startup failure per FR-015/T005/T028 (partial)
- [X] T034 Configure the MVC application-owned bearer `SecurityFilterChain` fixture with the existing JWT role/scope authority converter so a valid mapped role reaches the protected method with `200` while a missing role remains `403` per US3/AC2/T011 (partial)
- [X] T035 Configure the WebFlux application-owned bearer `SecurityWebFilterChain` fixture with the existing reactive JWT role/scope authority converter so a valid mapped role reaches the protected method with `200` while a missing role remains `403` per US3/AC3/T012 (partial)
- [X] T036 Preserve the required reactive method-denial ProblemDetail contract by returning `403` rather than `401` for the public-route `@PreAuthorize` denial, including the existing RFC 7807 fields and traceId assertion per T010/plan: reactive ProblemDetail coverage (partial)

## Phase 13: Convergence

- [X] T037 Replace the incompatible `@EnableReactiveMethodSecurity(useAuthorizationManager = false)` legacy fixture with executable named-sentinel fixtures and make the reactive manual-enablement back-off context pass without the missing `MethodSecurityMetadataSource` startup failure per FR-015/SC-010 (partial)
- [X] T038 Configure the MVC application-owned bearer `SecurityFilterChain` fixture to reuse the existing JWT authority converter and role mapper so nested `ROLE_dispatcher` authorization returns `200`, missing-role authorization returns `403`, and unauthenticated requests return `401` per US3/AC2/SC-007 (partial)
- [X] T039 Configure the WebFlux application-owned bearer `SecurityWebFilterChain` fixture to reuse the existing reactive JWT authority converter and role mapper so nested `ROLE_dispatcher` authorization returns `200`, missing-role authorization returns `403`, and unauthenticated requests return `401` per US3/AC3/SC-007 (partial)
- [X] T040 Correct reactive public-route method-denial handling so `@PreAuthorize("isAuthenticated()")` produces the common safe `403` ProblemDetail while protected routes retain `401` before method invocation per plan: reactive ProblemDetail coverage/SC-003 (partial)
- [X] T041 Update the README and both method-security quickstarts so focused MVC/WebFlux commands execute all required integration classes, including application-owned-chain, disabled-security, custom-prefix, actuator, and ProblemDetail scenarios, then rerun the complete focused validation per FR-014/SC-009 (partial)
