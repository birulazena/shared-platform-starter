# Quickstart Validation Guide: Shared Platform Starter

This guide describes the runnable acceptance path for the implemented shared
platform. It validates reusable infrastructure only; it does not add logistics
endpoints, business logic, identity-provider provisioning, or production
telemetry backends.

## Prerequisites

- JDK 21 selected by `JAVA_HOME`
- Bash-compatible shell (or use `gradlew.bat` on Windows)
- No external Keycloak, database, metrics backend, or OTLP collector is required
- Network access to resolve Maven Central dependencies on the first build

Confirm the toolchain and logical project set:

```bash
java -version
./gradlew --version
./gradlew projects
```

Expected:

- Java 21 toolchain/target;
- Gradle 9.3.0 wrapper;
- exactly three subprojects named `logistics-parent-service-bom`, `logistics-parent-service-autoconfigure`, and `logistics-parent-service-starter`;
- the root appears only as the build aggregator.

## One-Command Release Gate

```bash
./gradlew clean check
```

`check` must include fast unit/context tests, BOM alignment, MVC integration, WebFlux integration, and logging corpus verification. A release is not acceptable if any affected suite is skipped.

## Focused Validation Commands

```bash
./gradlew :logistics-parent-service-autoconfigure:test
./gradlew :logistics-parent-service-autoconfigure:mvcIntegrationTest
./gradlew :logistics-parent-service-autoconfigure:webfluxIntegrationTest
./gradlew :logistics-parent-service-autoconfigure:loggingTest
```

The custom integration source-set tasks are part of the planned build and must be wired into `check`.

### Automatic Method-Security Focused Checks

```bash
./gradlew :logistics-parent-service-autoconfigure:test \
  --tests '*MethodSecurityAutoConfigurationAnnotationTest' \
  --tests '*AutoConfigurationSelectionTest' \
  --tests '*SecurityAutoConfigurationContextTest' \
  --tests '*CapabilityBackOffTest'
./gradlew :logistics-parent-service-autoconfigure:mvcIntegrationTest \
  --tests '*MvcSecurityIntegrationTest' \
  --tests '*MvcSecurityCustomPrefixIntegrationTest' \
  --tests '*MvcSecurityActuatorOptOutIntegrationTest' \
  --tests '*MvcApplicationOwnedSecurityIntegrationTest' \
  --tests '*MvcSecurityDisabledMethodIntegrationTest' \
  --tests '*MvcProblemDetailIntegrationTest'
./gradlew :logistics-parent-service-autoconfigure:webfluxIntegrationTest \
  --tests '*WebFluxSecurityIntegrationTest' \
  --tests '*WebFluxSecurityCustomPrefixIntegrationTest' \
  --tests '*WebFluxSecurityActuatorOptOutIntegrationTest' \
  --tests '*WebFluxApplicationOwnedSecurityIntegrationTest' \
  --tests '*WebFluxSecurityDisabledMethodIntegrationTest' \
  --tests '*WebFluxProblemDetailIntegrationTest'
```

These focused commands cover automatic method authorization, matching manual
enablement back-off, application-owned bearer chains, disabled security,
custom role prefixes, protected actuator endpoints, and the ProblemDetail
contract on both supported web stacks.

## Scenario 1: BOM Alignment and Thin Starter

Run:

```bash
./gradlew :logistics-parent-service-bom:check
./gradlew :logistics-parent-service-starter:dependencies --configuration runtimeClasspath
```

Verify:

- Spring Boot resolves to exactly `4.1.0`;
- Spring Framework resolves to `7.0.8`, Spring Security to `7.1.0`, Micrometer to `1.17.0`, Micrometer Tracing to `1.7.0`, OpenTelemetry API/SDK to `1.62.0`, Kotlin to `2.3.21`, and Logback to `1.5.34`;
- `org.springframework.boot:spring-boot-starter-opentelemetry` is the source of the Boot-managed Micrometer/OpenTelemetry tracing and OTLP runtime graph;
- `opentelemetry-logback-appender-1.0` resolves to exactly `2.28.0-alpha`;
- the starter depends on auto-configuration and non-web prerequisites but does not resolve `spring-boot-starter-web` or `spring-boot-starter-webflux`;
- versionless platform dependency declarations resolve through the BOM.

## Scenario 2: Auto-Configuration Conditions and Back-Off

Run:

```bash
./gradlew :logistics-parent-service-autoconfigure:test --tests '*AutoConfiguration*'
./gradlew :logistics-parent-service-autoconfigure:test --tests '*BackOff*'
```

The `ApplicationContextRunner` suites must cover:

- no web classpath: no MVC/WebFlux security or error beans, non-web capabilities still eligible;
- Servlet application: MVC branch only;
- Reactive application: WebFlux branch only;
- both API classpaths with explicit Servlet type: MVC branch only;
- both API classpaths with explicit Reactive type: WebFlux branch only;
- with security enabled or the property absent: the selected method-security
  branch is active and the opposite branch is absent;
- with `security.enabled=false`: both selected platform web and method-security
  branches are absent while unrelated capabilities remain eligible;
- matching manual method-security infrastructure backs off the selected
  platform method branch, while an application web-chain bean alone does not;
- one application-owned capability bean at a time: only its corresponding default backs off and the other four remain active;
- missing issuer fails only when the default selected-stack security branch would activate;
- every canonical property binds, and no alternate root namespace binds.

## Scenario 3: MVC Acceptance

Run:

```bash
./gradlew :logistics-parent-service-autoconfigure:mvcIntegrationTest
```

The fixture is a minimal MVC test application that adds the BOM, the single starter, and the MVC starter. It uses MockMvc, mock JWTs, a controlled issuer decoder, a `SimpleMeterRegistry`, and an in-process OTLP collector.

Expected assertions:

- startup succeeds without WebFlux infrastructure;
- no token and invalid/expired/issuer-mismatch tokens receive `401` problems;
- a configured public pattern and present health/info default are public, while adjacent/nonmatching paths remain protected;
- nested string/list roles map with `ROLE_` and a custom/empty prefix; malformed claims add no roles;
- `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, `@PostFilter`, `@Secured`,
  `@RolesAllowed`, `@PermitAll`, and `@DenyAll` work without
  `@EnableMethodSecurity` in the fixture;
- `scope` and `scp` claims produce `SCOPE_<permission>` authorities usable in
  method expressions;
- a valid token with the required method authority returns `200`, a valid token
  without it returns `403`, and no token returns `401` before method invocation;
- an application bearer-authenticating `SecurityFilterChain` backs off the
  platform MVC chain only, while method authorization remains active;
- a `security.enabled=false` fixture with an independently owned permit-all
  chain does not receive platform method enforcement;
- a valid inbound `traceparent` is continued and an outbound managed RestClient receives W3C context;
- a custom counter, timer, and gauge are visible in the test registry;
- authentication, authorization, validation, and unhandled failures satisfy the problem contract;
- captured logs are valid sanitized JSON with trace correlation.

## Scenario 4: WebFlux Acceptance

Run:

```bash
./gradlew :logistics-parent-service-autoconfigure:webfluxIntegrationTest
```

The fixture is a minimal Reactive test application that adds the same BOM/starter and the WebFlux starter. It uses WebTestClient, reactive mock JWTs, a controlled reactive issuer decoder, a `SimpleMeterRegistry`, and the same collector behavior.

Expected assertions mirror the MVC list, with these additional checks:

- startup succeeds without MVC/Servlet infrastructure;
- publisher-returning `@PreAuthorize` and `@PostAuthorize` work without
  `@EnableReactiveMethodSecurity`, including delayed, empty, and scheduled
  Reactor cases;
- `scope` and `scp` claims reuse `SCOPE_<permission>` authorities in method
  expressions, and the `200`/`403`/`401` matrix matches MVC;
- an application bearer-authenticating `SecurityWebFilterChain` replaces only
  the platform WebFlux chain; a disabled platform leaves independently owned
  permit-all security in control;
- a Reactor scheduling boundary preserves the correct trace/log/error correlation;
- outbound managed WebClient propagation is W3C-compliant;
- reactive authentication/error writing remains nonblocking;
- the external status, headers, problem JSON, and public-pattern results equal the MVC contract.

## Scenario 5: Public Pattern Grammar

Run the common matcher tests and both security suites:

```bash
./gradlew :logistics-parent-service-autoconfigure:test --tests '*PublicEndpointPattern*'
./gradlew :logistics-parent-service-autoconfigure:mvcIntegrationTest --tests '*Security*'
./gradlew :logistics-parent-service-autoconfigure:webfluxIntegrationTest --tests '*Security*'
```

The corpus must include literal, `?`, segment `*`, terminal `/**`, overlapping permits, encoded characters, query strings, adjacent protected paths, and rejected URI-variable/regex/relative/mid-`**` patterns. Both stacks consume the same compiled pattern model described in [contracts/security.md](./contracts/security.md).

## Scenario 6: Tracing and OTLP Reliability

Run:

```bash
./gradlew :logistics-parent-service-autoconfigure:test --tests '*Tracing*'
./gradlew :logistics-parent-service-autoconfigure:mvcIntegrationTest --tests '*Tracing*'
./gradlew :logistics-parent-service-autoconfigure:webfluxIntegrationTest --tests '*Tracing*'
```

Verify four controlled modes:

1. no endpoint: W3C propagation/correlation active, no exporter;
2. recording local collector: sampling `1.0` produces a received OTLP trace;
3. invalid/rejecting collector: requests still return their intended status and logs/errors remain correlated;
4. application OpenTelemetry/export customizer: platform tracing contribution backs off or composes as documented without changing other capabilities; the platform adapts its canonical properties to Boot's OpenTelemetry configuration rather than requiring consumer-side `management.opentelemetry.*` settings.

For a failing request, compare the inbound/outbound trace ID, problem `traceId`, and parsed log `traceId`. Span IDs are allowed to differ.

## Scenario 7: ProblemDetail Contract

Run:

```bash
./gradlew :logistics-parent-service-autoconfigure:mvcIntegrationTest --tests '*ProblemDetail*'
./gradlew :logistics-parent-service-autoconfigure:webfluxIntegrationTest --tests '*ProblemDetail*'
```

For `401`, `403`, validation/client error, and `500`, verify:

- status equals body `status`;
- media type is `application/problem+json` when a body is returned;
- `type`, `title`, `status`, `detail`, and enabled `instance` semantics are stable;
- `traceId` is nonempty and correlated;
- stack trace, token/JWT, password, secret, query data, and personal-data canaries are absent;
- MVC and WebFlux JSON have the same externally visible field contract.

See [contracts/problem-detail.md](./contracts/problem-detail.md).

## Scenario 8: Metrics

Run:

```bash
./gradlew :logistics-parent-service-autoconfigure:mvcIntegrationTest --tests '*Metrics*'
./gradlew :logistics-parent-service-autoconfigure:webfluxIntegrationTest --tests '*Metrics*'
```

Expected: the fixtures record a counter, timer, and gauge using only Micrometer APIs; all three are visible in `SimpleMeterRegistry`; configured safe common tags exist; no OpenTelemetry Metrics API or configured metrics endpoint is needed.

## Scenario 9: Structured Logging and Redaction

Run:

```bash
./gradlew :logistics-parent-service-autoconfigure:loggingTest
```

The suite loads the real platform `logback-spring.xml`, initializes the separately managed approved 2.28.0-alpha OTel appender against a controlled OpenTelemetry log sink, and emits the corpus described in [contracts/logging.md](./contracts/logging.md). The tracing/OTLP runtime itself comes from `spring-boot-starter-opentelemetry`.

Expected:

- every console line parses as one JSON object with required fields;
- correlated events include valid `traceId`/`spanId`;
- every credential and configured personal-data canary is absent from console bytes and OTel log records;
- nested causes and suppressed exceptions cannot recover a canary;
- the appender initializes with Boot-managed OpenTelemetry 1.62.0 and Logback 1.5.34;
- an unavailable OTel log exporter does not prevent console logging or fail a request.

## Scenario 10: Final Scope and Compatibility Review

Before release, inspect the change set and confirm:

- there are exactly three platform subprojects and no separate web starter;
- no logistics domain model, business workflow, persistence, service endpoint, service metric, or service authorization policy exists;
- all configuration keys use `logistics.parent-service.*` with no alternate root namespace;
- Spring Boot remains exactly 4.1.0;
- compatibility-sensitive changes have an explicit Semantic Versioning assessment and migration notes;
- `tasks.md` was not generated by this planning workflow.

## Expected Completion Result

All commands pass locally with JDK 21, both integration suites demonstrate equivalent external behavior, the nonselected stack is absent from each fixture, and the complete `check` task provides the release evidence required by the constitution.

## Validation Notes

The release validation on 2026-08-21 found and corrected the web-stack public-endpoint filters above: the endpoint assertions live in `MvcSecurityIntegrationTest` and `WebFluxSecurityIntegrationTest`, so `*PublicEndpoint*` matched no test class. The focused replacement `*Security*` executes the complete security/public-endpoint suites. The same validation corrected the tracing filters from `*Trace*` to `*Tracing*`, matching `MvcTracingIntegrationTest` and `WebFluxTracingIntegrationTest`.

The validation host used Java 17 as the Gradle launcher JVM, while Gradle selected the configured Java 21 toolchain for compilation, tests, and bytecode. Consumer services still require Java 21 as stated in the prerequisites and project constitution.
