---

description: "Actionable implementation tasks for the shared platform starter"
---

# Tasks: Shared Platform Starter

**Input**: Design documents from `/specs/001-shared-platform-starter/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`, and `.specify/memory/constitution.md`

**Tests**: Test tasks are included because the feature specification and constitution require reusable-infrastructure, context, MVC, WebFlux, tracing, ProblemDetail, and structured logging/redaction coverage.

**Organization**: Tasks are grouped by user story. The four story phases are independently testable after the foundational phase; tasks within a phase are ordered so tests and shared contracts precede their implementations.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish the non-published Gradle aggregator and exactly three platform subprojects with the pinned Kotlin, Java, Spring Boot, and Gradle baselines.

- [X] T001 Create the non-published root aggregator and include exactly `logistics-parent-service-bom`, `logistics-parent-service-autoconfigure`, and `logistics-parent-service-starter` in `settings.gradle.kts`
- [X] T002 Configure shared repositories, Kotlin/JVM compilation, Java 21 toolchain and bytecode target, test conventions, group, and non-published root behavior in `build.gradle.kts`
- [X] T003 Pin project coordinates and shared baseline properties in `gradle.properties` and pin the Gradle 9.3.0 distribution in `gradle/wrapper/gradle-wrapper.properties`
- [X] T004 [P] Configure the `java-platform` BOM, Spring Boot 4.1.0 BOM import, platform version alignment, and dependency constraints in `logistics-parent-service-bom/build.gradle.kts`
- [X] T005 [P] Configure the Kotlin auto-configuration library, BOM consumption, Boot OpenTelemetry starter, compile-only MVC/WebFlux APIs, test source sets, and generated configuration metadata in `logistics-parent-service-autoconfigure/build.gradle.kts`
- [X] T006 [P] Configure the thin starter's API dependency on auto-configuration, Boot OpenTelemetry starter, and non-web prerequisites without MVC or WebFlux starter dependencies in `logistics-parent-service-starter/build.gradle.kts`
- [X] T007 Create the planned Kotlin, resource, test-source-set, integration-source-set, logging-test, and redaction-corpus directories under `logistics-parent-service-autoconfigure/src/` and the source roots under `logistics-parent-service-starter/src/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Implement shared contracts and build wiring that every user story relies on. No user-story work is complete until these prerequisites compile and the common configuration model is validated.

**⚠️ CRITICAL**: This phase blocks all user-story phases.

- [X] T008 Define the root `PlatformProperties` configuration model plus the five capability property groups (`SecurityProperties`, `TracingProperties`, `MetricsProperties`, `ErrorProperties`, and `LoggingProperties`) with their defaults, types, canonical namespaces, and validation rules in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/properties/PlatformProperties.kt`, `SecurityProperties.kt`, `TracingProperties.kt`, `MetricsProperties.kt`, `ErrorProperties.kt`, and `LoggingProperties.kt`
- [X] T009 [P] Add property-binding tests for canonical namespaces, defaults, valid values, invalid values, conditional issuer validation, and rejection of alternate roots in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/properties/PlatformPropertiesBindingTest.kt`
- [X] T010 [P] Add generated-configuration-metadata assertions for every public property, default, description, and canonical namespace in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/properties/ConfigurationMetadataTest.kt`
- [X] T011 Implement the non-web shared auto-configuration, property binding, ordered configuration imports, and capability enablement conditions in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/PlatformAutoConfiguration.kt` and `logistics-parent-service-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- [X] T012 [P] Implement the shared trace/correlation access abstraction used by errors and logging in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/observability/PlatformCorrelationContext.kt`
- [X] T013 [P] Implement the shared ProblemDetail factory with stable type URNs, safe default details, request-path instance handling, mandatory trace IDs, and `application/problem+json` support in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/errors/PlatformProblemDetailFactory.kt`
- [X] T014 [P] Verify ProblemDetail field construction, status equality, instance sanitization, trace-ID fallback, and detail-policy redaction in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/errors/PlatformProblemDetailFactoryTest.kt`
- [X] T015 [P] Define the documented application override SPI types for independent capability back-off in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/metrics/PlatformMetricsCustomizer.kt` and `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/logging/PlatformLogSanitizer.kt`
- [X] T016 [P] Create shared context-runner, mock-JWT, managed-client, test-registry, and controlled-collector fixtures in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/support/PlatformTestFixtures.kt` and `ControlledOtlpCollector.kt`
- [X] T017 Wire the custom `mvcIntegrationTest`, `webfluxIntegrationTest`, and `loggingTest` source sets into lifecycle verification and make root `check` depend on all required suites in `logistics-parent-service-autoconfigure/build.gradle.kts` and `build.gradle.kts`

**Checkpoint**: The three-module build, shared property model, common contracts, test fixtures, and lifecycle tasks are ready; user stories can now proceed independently.

---

## Phase 3: User Story 1 - Adopt One Shared Platform Dependency (Priority: P1) 🎯 MVP

**Goal**: Let a Kotlin/Java 21 MVC, WebFlux, or non-web consumer use one BOM plus one thin starter while retaining its selected application model and receiving one aligned dependency set.

**Independent Test**: Resolve the BOM and starter, start representative MVC and WebFlux fixtures with equivalent configuration, and verify that only the selected web branch is present and no web starter is forced transitively.

### Tests for User Story 1

- [X] T018 [P] [US1] Add a Gradle dependency-resolution test proving `spring-boot-starter-opentelemetry` supplies the Boot-managed Micrometer/OpenTelemetry tracing and OTLP graph at Boot 4.1.0, including Kotlin 2.3.21, Spring Framework 7.0.8, Spring Security 7.1.0, Micrometer 1.17.0, Micrometer Tracing 1.7.0, OpenTelemetry 1.62.0, Logback 1.5.34, and the separately managed `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.28.0-alpha`, through `logistics-parent-service-bom/src/test/kotlin/com/hz/logistics/parentservice/bom/BomAlignmentTest.kt`
- [X] T019 [P] [US1] Add a starter dependency contract test proving the starter exposes auto-configuration and non-web prerequisites but neither `spring-boot-starter-web` nor `spring-boot-starter-webflux` in `logistics-parent-service-starter/src/test/kotlin/com/hz/logistics/parentservice/starter/StarterDependencyContractTest.kt`
- [X] T020 [P] [US1] Add context tests for no-web, explicit Servlet, explicit Reactive, and both-classpaths-selected-branch behavior in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/AutoConfigurationSelectionTest.kt`
- [X] T021 [P] [US1] Add the minimal MVC adoption fixture and startup assertions using the public starter, MVC dependencies, and equivalent platform properties in `logistics-parent-service-autoconfigure/src/mvcIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/MvcStarterAdoptionTest.kt` and `src/mvcIntegrationTest/resources/application-mvc.yml`
- [X] T022 [P] [US1] Add the minimal WebFlux adoption fixture and startup assertions using the same public starter, WebFlux dependencies, and equivalent platform properties in `logistics-parent-service-autoconfigure/src/webfluxIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/WebFluxStarterAdoptionTest.kt` and `src/webfluxIntegrationTest/resources/application-webflux.yml`

### Implementation for User Story 1

- [X] T023 [US1] Complete the BOM's transitive dependency constraints and versionless consumer declarations for the Boot OpenTelemetry starter, approved Logback appender, platform, logging, and test coordinates in `logistics-parent-service-bom/build.gradle.kts`
- [X] T024 [US1] Complete the thin starter dependency graph and verify its consumer-facing API configuration contains no implementation source or selected web-stack dependency in `logistics-parent-service-starter/build.gradle.kts`
- [X] T025 [US1] Complete the shared non-web auto-configuration registration and condition ordering so non-web capabilities load without either web stack in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/PlatformAutoConfiguration.kt`
- [X] T026 [US1] Add the representative MVC and WebFlux fixture controllers, application classes, and test-only dependency declarations needed by the adoption tests in `logistics-parent-service-autoconfigure/src/mvcIntegrationTest/` and `logistics-parent-service-autoconfigure/src/webfluxIntegrationTest/`

**Checkpoint**: A consumer can adopt one BOM and one starter, select MVC or WebFlux itself, and start without the nonselected web infrastructure.

---

## Phase 4: User Story 2 - Secure an API by Default (Priority: P1)

**Goal**: Provide equivalent MVC and WebFlux OAuth2 resource-server defaults that deny by default, validate issuer-backed JWTs, allow only explicit public paths, map configured nested roles, and back off only for compatible application security ownership.

**Independent Test**: In both fixtures, exercise unauthenticated, invalid, expired, issuer-mismatched, valid, public-path, nested-role, and application-override cases and compare status, authorities, and problem responses.

### Tests for User Story 2

- [X] T027 [P] [US2] Add common public-endpoint grammar tests for exact literals, `?` within a segment, segment `*` excluding `/`, terminal `/**`, path-only matching, overlap, and order independence; assert that relative paths, empty/double segments, `..`, encoded-slash tricks, inline regex, URI-template variables, and non-terminal `**` are rejected with startup validation when security is active in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/security/PublicEndpointPatternTest.kt`
- [X] T028 [P] [US2] Add nested role extraction tests for absent/null/malformed/mixed claims, strings, lists, trimming, de-duplication, default `ROLE_`, custom prefixes, and empty prefixes in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/security/RoleClaimsAuthorityMapperTest.kt`
- [X] T029 [P] [US2] Add application-context tests for selected-stack conditions, invalid public-pattern startup failure, missing/invalid issuer failure, MVC `SecurityFilterChain` and WebFlux `SecurityWebFilterChain` back-off, `JwtDecoder`/`ReactiveJwtDecoder` reuse, documented authority-converter reuse without disabling default denial, `security.enabled=false` security-only disablement, and independent capability back-off in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/security/SecurityAutoConfigurationContextTest.kt`
- [X] T030 [P] [US2] Add MVC MockMvc security acceptance tests covering default denial, public patterns, health/info defaults and opt-out, mock-JWT validation, nested roles, failure ProblemDetails, and application `SecurityFilterChain` back-off in `logistics-parent-service-autoconfigure/src/mvcIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/security/MvcSecurityIntegrationTest.kt`
- [X] T031 [P] [US2] Add WebTestClient security acceptance tests covering the same cases with reactive decoders/converters and `SecurityWebFilterChain` back-off in `logistics-parent-service-autoconfigure/src/webfluxIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/security/WebFluxSecurityIntegrationTest.kt`

### Implementation for User Story 2

- [X] T032 [US2] Implement the shared parsed `PathPattern` subset, segment-aware matcher, duplicate elimination, and startup validation in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/security/PublicEndpointPattern.kt`
- [X] T033 [US2] Implement deterministic nested JWT role traversal, string/list validation, trimming, de-duplication, and configurable prefix mapping in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/security/RoleClaimsAuthorityMapper.kt`
- [X] T034 [US2] Implement absolute HTTP(S) issuer validation and selected-branch decoder creation/reuse in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/security/IssuerValidation.kt`
- [X] T035 [US2] Implement the shared JWT authentication-converter adapter that applies configured nested authorities without hardcoded vendor claim paths in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/security/PlatformJwtAuthenticationConverter.kt`
- [X] T036 [US2] Implement the conditional MVC default security chain with stateless bearer authentication, secure default denial, common public matchers, health/info matchers, `JwtDecoder` and documented authority-converter reuse, and back-off only when an application `SecurityFilterChain` is present in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/security/mvc/PlatformMvcSecurityAutoConfiguration.kt`
- [X] T037 [US2] Implement MVC authentication-entry-point and access-denied handlers that write the shared safe ProblemDetail contract and required bearer headers in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/security/mvc/PlatformMvcSecurityFailureHandlers.kt`
- [X] T038 [US2] Implement the conditional WebFlux default security chain with equivalent bearer, matcher, `ReactiveJwtDecoder` and documented authority-converter reuse, denial, and back-off only when an application `SecurityWebFilterChain` is present in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/security/reactive/PlatformWebFluxSecurityAutoConfiguration.kt`
- [X] T039 [US2] Implement non-blocking WebFlux authentication-entry-point and access-denied handlers using the shared ProblemDetail factory in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/security/reactive/PlatformWebFluxSecurityFailureHandlers.kt`
- [X] T040 [US2] Register the MVC and WebFlux security auto-configurations with explicit ordering and classpath/web-application conditions in `logistics-parent-service-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Checkpoint**: Both supported web stacks reject unauthenticated protected requests, permit only configured public paths, validate issuer-backed JWTs, map roles identically, and preserve application-owned security behavior.

---

## Phase 5: User Story 3 - Follow a Request Across Services (Priority: P1)

**Goal**: Preserve valid W3C trace context across inbound/outbound calls and reactive boundaries, provide local correlation without an exporter, and make OTLP export asynchronous and non-fatal.

**Independent Test**: Send valid, missing, and malformed `traceparent` values through MVC and WebFlux fixtures, inspect managed outbound clients, compare trace IDs in errors/logs, and exercise absent, recording, and unavailable OTLP collectors.

### Tests for User Story 3

- [X] T041 [P] [US3] Add W3C propagation unit/context tests for valid continuation, invalid/missing header replacement, valid `tracestate`, sampling bounds, and trace/span correlation in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/tracing/W3cPropagationTest.kt`
- [X] T042 [P] [US3] Add OTLP configuration tests for no endpoint/no exporter, HTTP and gRPC settings, headers/timeout/compression validation, sampling `1.0`, asynchronous export, and application observability back-off in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/tracing/OtlpConfigurationTest.kt`
- [X] T043 [P] [US3] Add MVC trace integration tests for inbound continuation, managed RestClient `traceparent` injection, no/invalid context, problem/log correlation, recording collector export, and rejecting collector resilience in `logistics-parent-service-autoconfigure/src/mvcIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/tracing/MvcTracingIntegrationTest.kt`
- [X] T044 [P] [US3] Add WebFlux trace integration tests for inbound continuation, managed WebClient injection, missing/invalid context, Reactor scheduler preservation, problem/log correlation, collector export, and exporter resilience in `logistics-parent-service-autoconfigure/src/webfluxIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/tracing/WebFluxTracingIntegrationTest.kt`
- [X] T045 [P] [US3] Add context tests proving tracing can be disabled or independently overridden while security, metrics, errors, and logging remain eligible in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/tracing/TracingBackOffTest.kt`

### Implementation for User Story 3

- [X] T046 [US3] Integrate Spring Boot's `spring-boot-starter-opentelemetry` auto-configuration with the platform tracing properties, W3C propagation, configured sampling, managed-client instrumentation, and tracing-only conditions in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/tracing/PlatformTracingAutoConfiguration.kt`
- [X] T047 [US3] Map canonical OTLP endpoint/protocol/header/timeout/compression settings to Spring Boot's supported OTLP configuration/customizers, preserving batch/asynchronous export and diagnostic-only exporter failure handling in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/tracing/OtlpTracingCustomizer.kt`
- [X] T048 [US3] Implement W3C propagator registration and invalid-carrier handling so malformed inbound context starts a new safe trace without failing request processing in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/tracing/W3cPropagationConfigurer.kt`
- [X] T049 [US3] Implement trace/span MDC correlation and error-time fallback correlation using the shared context abstraction in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/tracing/TraceCorrelationConfigurer.kt`
- [X] T050 [US3] Implement supported reactive context propagation across Reactor scheduling boundaries without attaching stale thread-local context in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/tracing/ReactiveTraceContextBridge.kt`
- [X] T051 [US3] Register tracing auto-configuration after shared properties and before logging/error adapters in `logistics-parent-service-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Checkpoint**: Valid W3C context is continued and injected into managed clients in both stacks; absent or invalid context is safely replaced; OTLP is optional, asynchronous, and non-blocking.

---

## Phase 6: User Story 4 - Diagnose Errors and Activity Consistently (Priority: P2)

**Goal**: Expose Micrometer metrics, equivalent safe ProblemDetail responses, and structured JSON logs with pre-sink baseline and application-configured redaction across MVC and WebFlux.

**Independent Test**: Trigger success, authentication, authorization, validation, and unhandled-error paths in both stacks; inspect counter/timer/gauge registry output, exact problem fields/media type/trace ID, and sanitized console/OTel JSON events.

### Tests for User Story 4

- [X] T052 [P] [US4] Add MVC Micrometer tests for application counter, timer, gauge, safe common tags, application registry reuse, and metrics-only back-off in `logistics-parent-service-autoconfigure/src/mvcIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/metrics/MvcMetricsIntegrationTest.kt`
- [X] T053 [P] [US4] Add WebFlux Micrometer tests for the same counter, timer, gauge, tags, registry, and independent back-off behavior in `logistics-parent-service-autoconfigure/src/webfluxIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/metrics/WebFluxMetricsIntegrationTest.kt`
- [X] T054 [P] [US4] Add MVC ProblemDetail contract tests for 401, 403, validation/client, unhandled 500, unsupported `Accept`, committed responses, safe details, media type, instance, trace ID, error-handler back-off, and absence of the complete sensitive-data corpus (stack traces, exception class names, JWTs, bearer/authorization values, passwords, secrets, request bodies, OTLP headers, and configured personal data) in `logistics-parent-service-autoconfigure/src/mvcIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/errors/MvcProblemDetailIntegrationTest.kt`
- [X] T055 [P] [US4] Add WebFlux ProblemDetail contract tests for equivalent statuses, fields, media type, trace correlation, non-blocking writing, safe details, committed responses, handler back-off, and absence of the complete sensitive-data corpus (stack traces, exception class names, JWTs, bearer/authorization values, passwords, secrets, request bodies, OTLP headers, and configured personal data) in `logistics-parent-service-autoconfigure/src/webfluxIntegrationTest/kotlin/com/hz/logistics/parentservice/autoconfigure/errors/WebFluxProblemDetailIntegrationTest.kt`
- [X] T056 [P] [US4] Add a redaction corpus unit suite for messages, arguments, MDC/key-values, headers, query parameters, nested maps/lists, causes, suppressed exceptions, baseline categories, configured fields, configured paths, and near-matches in `logistics-parent-service-autoconfigure/src/loggingTest/kotlin/com/hz/logistics/parentservice/autoconfigure/logging/LoggingRedactionTest.kt` and `src/test/resources/redaction-corpus/redaction-cases.json`
- [X] T057 [P] [US4] Add runtime logging compatibility tests that parse console JSON and controlled OpenTelemetry log records, verify required fields/correlation, prove no raw canary bytes reach either sink, and exercise unavailable OTel logging in `logistics-parent-service-autoconfigure/src/loggingTest/kotlin/com/hz/logistics/parentservice/autoconfigure/logging/LoggingRuntimeCompatibilityTest.kt`
- [X] T058 [P] [US4] Add context tests replacing one metrics, error, or logging owner at a time and asserting the other capabilities remain active in `logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/CapabilityBackOffTest.kt`

### Implementation for User Story 4

- [X] T059 [US4] Implement Micrometer metrics auto-configuration, application registry reuse, validated common tags, and metrics-only back-off in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/metrics/PlatformMetricsAutoConfiguration.kt`
- [X] T060 [US4] Implement the platform metrics policy/customizer and safe tag validation without exposing an OpenTelemetry Metrics API or configuring a vendor/OTLP metrics endpoint in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/metrics/PlatformMetricsPolicy.kt`
- [X] T061 [US4] Implement MVC controller/advice and framework-exception handling for validation, known errors, unhandled failures, content negotiation, committed responses, and safe ProblemDetail serialization in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/errors/mvc/PlatformMvcErrorAutoConfiguration.kt`
- [X] T062 [US4] Implement WebFlux controller/error-handler integration with non-blocking equivalent ProblemDetail serialization and independent handler back-off in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/errors/reactive/PlatformWebFluxErrorAutoConfiguration.kt`
- [X] T063 [US4] Implement baseline-plus-configured sensitive-value sanitization over messages, arguments, structured fields, headers, query values, and complete throwable projections in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/logging/PlatformLogSanitizer.kt`
- [X] T064 [US4] Implement the immutable pre-sink redacting fan-out appender so console JSON and OpenTelemetry consumers receive only the same sanitized event in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/logging/RedactingFanOutAppender.kt`
- [X] T065 [US4] Add the default structured JSON Logback configuration with timestamp, level, logger, message, thread, MDC correlation, sanitized exception projection, and both sinks in `logistics-parent-service-autoconfigure/src/main/resources/logback-spring.xml`
- [X] T066 [US4] Implement safe programmatic installation and logging-only back-off for `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.28.0-alpha` in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/logging/OpenTelemetryLogbackInstaller.kt`
- [X] T067 [US4] Implement logging auto-configuration for console/OTel enablement, custom sanitizer replacement with immutable baseline enforcement, application logging-resource precedence, and logging-only conditions in `logistics-parent-service-autoconfigure/src/main/kotlin/com/hz/logistics/parentservice/autoconfigure/logging/PlatformLoggingAutoConfiguration.kt`
- [X] T068 [US4] Register metrics and MVC/WebFlux error auto-configurations in dependency-safe order and register logging after tracing correlation in `logistics-parent-service-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Checkpoint**: Both stacks expose equivalent Micrometer, ProblemDetail, JSON correlation, and redaction behavior; logging redaction happens before every configured sink and each capability backs off independently.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Close the release gate, document adoption, and record compatibility/scope evidence required by the constitution.

- [X] T069 [P] Document the one-BOM/one-starter adoption model, MVC/WebFlux selection, canonical configuration, override points, and logging/redaction guarantees in `README.md`
- [X] T070 [P] Add the feature's compatibility review, Semantic Versioning assessment, stable-contract inventory, and migration-note policy in `specs/001-shared-platform-starter/compatibility-review.md`
- [X] T071 Run the full `./gradlew clean check` release gate and resolve compilation, skipped-suite, dependency-alignment, and selected-web-stack failures in `build.gradle.kts`, `logistics-parent-service-autoconfigure/build.gradle.kts`, and affected source/test files
- [X] T072 Run every focused command and acceptance scenario in `specs/001-shared-platform-starter/quickstart.md`, recording any fixture or documentation corrections in `specs/001-shared-platform-starter/quickstart.md`
- [X] T073 Review the final source tree against the three-module and shared-infrastructure boundaries and remove or correct any domain model, persistence, service endpoint, forced web dependency, alternate namespace, or fourth-module artifact in `settings.gradle.kts`, `logistics-parent-service-bom/`, `logistics-parent-service-autoconfigure/`, and `logistics-parent-service-starter/`
- [X] T074 Verify all compatibility-sensitive configuration, security, trace, error, logging, redaction, and module contracts have regression evidence and migration notes before release in `specs/001-shared-platform-starter/contracts/`, `specs/001-shared-platform-starter/compatibility-review.md`, and `README.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies; creates the Gradle aggregator, three modules, source layout, and pinned baselines.
- **Foundational (Phase 2)**: Depends on Setup; blocks all stories because every story uses the common configuration, registration, ProblemDetail, correlation, SPI, fixtures, and lifecycle wiring.
- **User Story 1 (Phase 3)**: Depends on Foundational and validates the consumable build shape and selected web-stack adoption.
- **User Story 2 (Phase 4)**: Depends on Foundational and User Story 1's build/adoption fixtures; it may proceed in parallel with User Stories 3 and 4 after the shared build is usable.
- **User Story 3 (Phase 5)**: Depends on Foundational and User Story 1's fixtures; logging/error correlation assertions use the shared abstractions and can proceed in parallel with security implementation.
- **User Story 4 (Phase 6)**: Depends on Foundational, the tracing correlation abstraction, and the security failure integration points; complete security and tracing behavior before final cross-stack correlation verification.
- **Polish (Phase 7)**: Depends on all desired stories and all required test suites being implemented.

### User Story Dependencies

- **US1 (P1)**: First deliverable; no story dependency after Foundational.
- **US2 (P1)**: Functionally independent after Foundational, but uses US1's MVC/WebFlux fixture and starter setup.
- **US3 (P1)**: Functionally independent after Foundational, but uses US1's fixture and shared correlation abstraction.
- **US4 (P2)**: Uses shared ProblemDetail/correlation contracts and validates cross-story security/tracing correlation; its metrics and logging implementations remain independently overridable.

### Within Each User Story

- Tests are created before their corresponding implementation and must initially fail for the missing behavior.
- Shared parsing/mapping/configuration utilities precede stack-specific adapters.
- Stack-specific implementation precedes integration and cross-stack comparison.
- A story checkpoint is required before moving its work into the final polish gate.

### Parallel Opportunities

- T004–T006 can be implemented in parallel after T001–T003 establish the root build shape.
- T009–T016 are parallelizable where they touch separate property, contract, fixture, and test files.
- T018–T022 can run in parallel because they cover separate modules/source sets.
- T027–T031 can run in parallel as independent security test suites; T032–T035 can then run in parallel before stack-specific chain tasks.
- T041–T045 can run in parallel; MVC and WebFlux tracing implementations/tests use separate source paths.
- T052–T058 can run in parallel; metrics, ProblemDetail, redaction, and back-off suites are separate files.
- T059–T067 can be split by metrics, errors, and logging ownership after the tests and shared factory are available.
- T069–T070 are independent documentation tasks and can run while the final verification is prepared.

## Parallel Example: User Story 1

```text
Task: T018 BOM alignment test in logistics-parent-service-bom/src/test/kotlin/com/hz/logistics/parentservice/bom/BomAlignmentTest.kt
Task: T019 starter dependency contract test in logistics-parent-service-starter/src/test/kotlin/com/hz/logistics/parentservice/starter/StarterDependencyContractTest.kt
Task: T020 selected-branch context test in logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/AutoConfigurationSelectionTest.kt
Task: T021 MVC adoption fixture in logistics-parent-service-autoconfigure/src/mvcIntegrationTest/
Task: T022 WebFlux adoption fixture in logistics-parent-service-autoconfigure/src/webfluxIntegrationTest/
```

## Parallel Example: User Story 2

```text
Task: T027 public endpoint grammar tests in logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/security/PublicEndpointPatternTest.kt
Task: T028 nested role mapper tests in logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/security/RoleClaimsAuthorityMapperTest.kt
Task: T030 MVC security acceptance tests in logistics-parent-service-autoconfigure/src/mvcIntegrationTest/kotlin/
Task: T031 WebFlux security acceptance tests in logistics-parent-service-autoconfigure/src/webfluxIntegrationTest/kotlin/
```

## Parallel Example: User Story 3

```text
Task: T041 W3C propagation tests in logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/tracing/W3cPropagationTest.kt
Task: T042 OTLP configuration tests in logistics-parent-service-autoconfigure/src/test/kotlin/com/hz/logistics/parentservice/autoconfigure/tracing/OtlpConfigurationTest.kt
Task: T043 MVC tracing acceptance tests in logistics-parent-service-autoconfigure/src/mvcIntegrationTest/kotlin/
Task: T044 WebFlux tracing acceptance tests in logistics-parent-service-autoconfigure/src/webfluxIntegrationTest/kotlin/
```

## Parallel Example: User Story 4

```text
Task: T052 MVC metrics tests and T053 WebFlux metrics tests
Task: T054 MVC ProblemDetail tests and T055 WebFlux ProblemDetail tests
Task: T056 redaction corpus tests and T057 logging runtime tests
Task: T058 independent capability back-off tests
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 and Phase 2 so the three-module build, property model, registration, and shared contracts are usable.
2. Complete Phase 3 to prove one BOM plus one starter works for representative MVC and WebFlux consumers without forcing the other stack.
3. Stop and validate the adoption contract with `./gradlew :logistics-parent-service-bom:check`, starter dependency inspection, and both adoption fixtures.

### Incremental Delivery

1. Add User Story 2 for secure default denial and equivalent JWT behavior.
2. Add User Story 3 for W3C propagation, local correlation, and reliable optional OTLP export.
3. Add User Story 4 for Micrometer, ProblemDetail, structured logging, and pre-sink redaction.
4. Run the full release gate after each story so earlier contracts remain stable.

### Parallel Team Strategy

1. One owner completes Setup and Foundational together because they define the shared build and contracts.
2. After the foundation is green, separate owners can work on US2 security, US3 tracing, and US4 metrics/errors/logging; US1 adoption tests remain the shared integration baseline.
3. Complete the cross-story correlation and full `check` gate before release.

## Notes

- Every task uses the required `- [ ] T###` checklist format.
- `[P]` marks tasks that touch separate files and have no dependency on unfinished work.
- `[US#]` labels map tasks to the four user stories in `spec.md`; Setup, Foundational, and Polish tasks intentionally have no story label.
- The logging implementation and verification tasks follow `contracts/logging.md`: baseline redaction is immutable, configured fields/paths are additive, and sanitization occurs before both console and OpenTelemetry sinks.
- The security tasks follow `contracts/security.md`: public patterns are permit-only and validated at startup; complete application chains trigger selected-stack back-off, while decoders and documented authority converters are reused without disabling default denial.
- No publication, CI/CD, business domain, persistence, service-specific endpoint, or service-specific authorization work is included.

## Phase 8: Convergence

- [X] T075 Complete the shared auto-configuration registration, capability enablement conditions, and ordering contract so each bound capability flag is effective independently and add context coverage for disabled-capability back-off per T-011 / FR-016 / plan: capability enablement and import ordering (partial)
- [X] T076 Strengthen configuration metadata coverage to assert every canonical group and public property has its exact type, description, and default semantics, including nullable and empty-collection defaults, per T-010 / FR-016 / plan: configuration metadata (partial)

## Phase 9: Convergence

- [X] T077 Preserve one valid execution-scoped fallback trace ID across ProblemDetail creation and structured diagnostic logging without leaking it between requests, per T-012 / FR-010 / FR-012 / contracts: observability correlation (partial)
- [X] T078 Harden `PlatformProblemDetailFactory` detail sanitization so SAFE and generic responses cannot expose passwords, tokens, secrets, JWT material, exception class names, or stack content before serialization, per T-013 / FR-013 / contracts: ProblemDetail detail policy (partial)

## Phase 10: Convergence

- [X] T079 Require context-managed `RestClient.Builder` and `WebClient.Builder` inputs in the reusable fixture and add coverage preventing raw default builders from bypassing tracing instrumentation per plan: managed outbound clients (partial)

---

## Phase 11: Convergence

- [X] T080 CRITICAL Compile the BOM dependency-resolution suite for JVM 21 instead of JVM 17, preserving the platform bytecode baseline per Constitution Additional Constraints / plan: Java 21 toolchain and bytecode target (contradicts)
- [X] T081 Implement and register the selected-stack MVC and WebFlux security auto-configurations so T020's non-web, Servlet, Reactive, and both-classpaths branch-selection checks pass per T020 / FR-004 (missing)
- [X] T082 Isolate the MVC adoption fixture from the WebFlux test runtime classpath and assert that MVC startup through the public starter does not require reactive web infrastructure per T021 / US1/AC1 (partial)
- [X] T083 Isolate the WebFlux adoption fixture from the MVC and Servlet test runtime classpath and assert that WebFlux startup through the public starter does not require Servlet web infrastructure per T022 / US1/AC2 (partial)

## Phase 12: Convergence

- [X] T084 Add active-security startup-failure coverage for every prohibited public-endpoint grammar category in `PublicEndpointPatternTest` per T027 / FR-005 (partial)
- [X] T085 Verify MVC and WebFlux application-provided authority converters are actually reused while default denial remains active in `SecurityAutoConfigurationContextTest` per T029 / FR-004 / FR-017 (partial)
- [X] T086 Exercise the MVC resource-server bearer flow through a controlled `JwtDecoder`, covering valid, invalid, expired, issuer-mismatched, and nested-role tokens with default and custom prefixes in `MvcSecurityIntegrationTest` per T030 / US2/AC2 / US2/AC4 (partial)
- [X] T087 Exercise the WebFlux resource-server bearer flow through a controlled `ReactiveJwtDecoder`, covering valid, invalid, expired, issuer-mismatched, and nested-role tokens with default and custom prefixes in `WebFluxSecurityIntegrationTest` per T031 / US2/AC2 / US2/AC4 (partial)

## Phase 13: Convergence

- [X] T088 Add MVC and WebFlux structured-log capture assertions that verify the same inbound trace ID appears in the safe ProblemDetail and emitted JSON event per T043 / T044 / FR-009 / FR-012 / SC-003 (partial)
- [X] T089 Make the MVC and WebFlux rejecting-collector scenarios call the successful outbound endpoint and assert a 200 response plus a valid propagated header when OTLP export fails per T043 / T044 / FR-010 (partial)
- [X] T090 Add separate no-`traceparent` assertions that prove MVC and WebFlux start and propagate a fresh valid trace, including the Reactor scheduler boundary, per T043 / T044 / FR-009 / US3/AC4 (partial)
- [X] T091 Add enabled-tracing positive controls, then prove tracing disablement and an application-owned `OpenTelemetry` instance alone back off platform tracing while other capability beans remain eligible per T045 / FR-017 (partial)
- [X] T092 Turn OTLP configuration coverage into exporter-behavior tests proving canonical HTTP/gRPC endpoint, protocol, headers, timeout, compression, sampling, absent-exporter, and non-blocking delayed-collector semantics per T042 / FR-010 (partial)

## Phase 14: Convergence

- [X] T093 CRITICAL Wire the shared `PlatformProblemDetailFactory` and `TraceCorrelationConfigurer` fallback into MVC and WebFlux unhandled-error handling so an inbound trace produces the same non-empty `traceId` in the RFC 7807 response on both stacks, while preserving independent handler back-off, per T049 / FR-012 / Constitution V (partial)
- [X] T094 Remove or explicitly justify and order-test the direct `@Import(PlatformTracingAutoConfiguration::class)` in `PlatformAutoConfiguration`, leaving `AutoConfiguration.imports` as the single registration path for tracing, per T051 / plan: auto-configuration registration (unrequested)

## Phase 15: Convergence

- [X] T095 CRITICAL Implement and register the Micrometer metrics policy so T052 and T053 pass for counter/timer/gauge recording, application registry reuse, safe common tags, and metrics-only back-off, per T052/T053 / FR-011 / Constitution IV (missing)
- [X] T096 CRITICAL Implement and register equivalent MVC and WebFlux ProblemDetail error handlers so T054 and T055 pass for authentication, authorization, client/unhandled failures, media type, trace correlation, safe details, committed responses, and error-only back-off, per T054/T055 / FR-012 / FR-013 / Constitution V (missing)
- [X] T097 CRITICAL Implement the baseline-plus-configured `PlatformLogSanitizer` and expose it through logging auto-configuration so T056 redacts all required event locations, nested throwable data, configured fields/paths, and preserves safe near-matches, per T056 / FR-015 / Constitution IV (missing)
- [X] T098 CRITICAL Implement and register the pre-sink redacting Logback JSON pipeline, safe OpenTelemetry log installer, and logging-only back-off so T057 receives valid correlated JSON/OTel events without raw canaries and remains usable when OTel logging is unavailable, per T057 / FR-014 / FR-015 / Constitution IV (missing)
- [X] T099 CRITICAL Complete independent metrics, error, and logging owner conditions and registration so T058 proves each replacement backs off only its capability while unrelated defaults remain active, per T058 / FR-017 / Constitution III / Constitution V (missing)

## Phase 16: Convergence

- [X] T100 Wire the implemented metrics auto-configuration into the registered auto-configuration order and make T052/T053 prove application registry reuse, safe common tags, and metrics-only back-off per T059/T060 / FR-011 / Constitution IV (partial)
- [X] T101 Wire the implemented MVC ProblemDetail auto-configuration and complete validation, content-negotiation, unhandled-failure, and committed-response handling so T054 returns the common safe contract per T061 / FR-012 / FR-013 / Constitution V (partial)
- [X] T102 Wire the implemented WebFlux ProblemDetail auto-configuration, preserve non-blocking serialization, and back off for an application-owned reactive error handler so T055 remains equivalent and independent per T062 / FR-012 / FR-017 / Constitution III (partial)
- [X] T103 Expose the implemented default `PlatformLogSanitizer` through logging auto-configuration and connect it to the pre-sink pipeline while preserving immutable baseline redaction and application overrides per T063 / FR-015 / Constitution IV (partial)

## Phase 17: Convergence

- [X] T104 Wire `PlatformCorrelationContext` through MVC, WebFlux, Reactor, and the pre-sink logging boundary so a pre-trace failure has one fallback `traceId` in both ProblemDetail and diagnostics, clears it at each execution boundary, and preserves independent application-error-handler back-off per T077 / T093 / FR-010 / FR-012 / FR-014 / SC-003
- [X] T105 Harden SAFE ProblemDetail detail sanitization with an immutable baseline that removes simple and qualified exception names, inline stack content, credentials, JWTs, secrets, and unnecessary personal data before serialization, with MVC/WebFlux corpus coverage per T078 / T096 / FR-013 / SC-004 / US4/AC2
- [X] T106 Extend the rejecting-collector WebFlux tracing scenario to call the successful managed outbound endpoint and assert HTTP 200 plus a valid propagated `traceparent` per T089 / T044 / FR-010
- [X] T107 Split MVC missing and malformed `traceparent` coverage, independently proving that an absent header starts and propagates a fresh valid W3C trace per T090 / T043 / FR-009 / US3/AC4
- [X] T108 Add OTLP exporter-behavior coverage for canonical HTTP/gRPC endpoints, protocol, headers, timeout, compression, sampling, absent exporter, and delayed/rejecting non-blocking export per T092 / T042 / FR-010
- [X] T109 Align MVC and WebFlux ProblemDetail mappings for unsupported media negotiation and method-security authentication failures, with paired contract tests for status, fields, media type, and trace correlation per T096 / FR-012 / FR-013 / SC-004
- [X] T110 Extend selected-stack error ownership detection and tests to compatible application `@ControllerAdvice` as well as reactive handlers, preserving unrelated metrics and logging capabilities per T099 / FR-017 / Constitution III / Constitution V
