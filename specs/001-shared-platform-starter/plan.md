# Implementation Plan: Shared Platform Starter

**Branch**: `001-shared-platform-starter` | **Date**: 2026-08-19 | **Spec**: [spec.md](./spec.md)

**Input**: Binding feature specification from `specs/001-shared-platform-starter/spec.md`, completed quality checklist from `specs/001-shared-platform-starter/checklists/requirements.md`, and project constitution from `.specify/memory/constitution.md`

## Summary

Build one reusable Kotlin/JVM platform for ten HZ Logistics services with exactly three Gradle Kotlin DSL platform modules: a dependency BOM, an auto-configuration implementation, and a thin starter. The implementation targets Java 21 and Spring Boot 4.1.0, keeps Servlet/MVC and Reactive/WebFlux dependencies optional, and activates separate conditional security and error branches for the selected web application type. Shared, independently overridable defaults provide issuer-based JWT resource-server security, nested role extraction, W3C trace propagation with optional OTLP export, Micrometer metrics, RFC 7807-compatible `ProblemDetail` errors, and structured Logback JSON events that are redacted before any console or OpenTelemetry sink receives them.

## Technical Context

**Language/Version**: Kotlin 2.3.21 (Spring Boot 4.1.0 managed line), Java 21 toolchain and bytecode target

**Primary Dependencies**: Gradle Wrapper 9.3.0 with Kotlin DSL; Spring Boot 4.1.0; Spring Framework 7.0.8; Spring Security 7.1.0 OAuth2 Resource Server/Jose; Spring Boot Actuator; `org.springframework.boot:spring-boot-starter-opentelemetry` for Micrometer/OpenTelemetry tracing and OTLP; and `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.28.0-alpha` for the required OTel logging sink. The Boot starter owns the Micrometer/OpenTelemetry runtime graph; the Logback appender is the only separately managed observability integration because Spring Boot does not ship it. Every runtime and test dependency version is supplied or constrained by `logistics-parent-service-bom`, primarily by importing the Spring Boot 4.1.0 BOM and adding an explicit constraint for the approved alpha appender.

**Storage**: N/A. The platform contains no persistence, business state, migrations, or domain data.

**Testing**: JUnit Jupiter 6 through Spring Boot Test; `ApplicationContextRunner`, `WebApplicationContextRunner`, and `ReactiveWebApplicationContextRunner` for conditions and back-off; MockMvc and WebTestClient with Spring Security mock JWTs; OpenTelemetry SDK test exporters plus a controlled local OTLP collector; Micrometer `SimpleMeterRegistry`; Logback captured-output and redaction corpus tests; Gradle dependency-resolution assertions for BOM alignment.

**Target Platform**: Kotlin-based Spring Boot services running on a Java 21 JVM, typically Linux containers; consumers select either Servlet/MVC or Reactive/WebFlux, and non-web application contexts remain supported for non-web capabilities.

**Project Type**: Multi-module reusable platform library. The Gradle root is a non-published aggregator, not a fourth platform module.

**Performance Goals**: No business throughput target is introduced. Trace export is asynchronous and exporter absence or outage must not block or fail request processing; redaction must run before serialization/export and remain bounded to each event; web-stack selection must add no infrastructure for the nonselected stack.

**Constraints**: Exactly three platform modules; Gradle Kotlin DSL; Kotlin implementation; Java 21; Spring Boot exactly 4.1.0; no forced MVC or WebFlux; W3C `traceparent`; secure default denial; problem JSON without sensitive data; independent capability back-off; all configuration under `logistics.parent-service.*`; no service-specific logic; no publication or CI/CD work in this feature.

**Scale/Scope**: One shared platform consumed by ten microservices; three modules; five independently configured capability areas; two equivalent web-stack branches; one non-web mode; reusable infrastructure and compatibility tests only.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

### Pre-Research Gate

| Constitutional gate | Status | Plan evidence |
|---|---|---|
| Exactly three modules with fixed BOM, auto-configuration, and starter responsibilities | PASS | The root is an aggregator only; the source tree contains exactly the three named platform modules. |
| Secure-by-default issuer-based JWT security with configurable nested roles and `ROLE_` default | PASS | Separate MVC and WebFlux chains deny by default, require an issuer while the platform default is active, and share one role-mapping contract. |
| Web-stack neutrality and safe conditional back-off | PASS | Web dependencies are compile-only in auto-configuration and supplied only by the consuming application; branch conditions include classpath and selected application type. |
| W3C/OpenTelemetry/OTLP, Micrometer, structured Logback JSON, correlation, and redaction | PASS | The BOM-aligned observability design uses W3C propagation, optional non-fatal OTLP export, Micrometer application APIs, and a pre-sink redaction boundary. |
| Stable ProblemDetail contract and reusable-infrastructure quality gates | PASS | MVC, WebFlux, security, propagation, metrics, error, logging, and redaction suites are explicit release gates. |
| Kotlin, Java 21, Gradle Kotlin DSL, Spring Boot 4.1.0, English artifacts | PASS | All baselines are pinned and all generated planning artifacts are English. |
| Shared infrastructure only | PASS | No business models, persistence, service endpoints, policies, dashboards, or backend provisioning are designed. |

No violation or temporary exception is required. Phase 0 may proceed.

### Post-Design Gate

| Constitutional gate | Status | Design evidence |
|---|---|---|
| Three-module architecture remains exact | PASS | `contracts/module-and-backoff.md` fixes the dependency direction and identifies the root only as an aggregator. |
| Security remains secure and portable | PASS | `contracts/security.md` defines issuer validation, the common PathPattern subset, role extraction, default denial, equivalent failures, and chain-specific back-off. |
| Neither web stack is forced | PASS | Optional compile-time web APIs are isolated in separate conditional packages; the starter has no MVC or WebFlux starter dependency. |
| Observability and diagnostics meet the platform standard | PASS | `contracts/observability.md` and `contracts/logging.md` define W3C propagation, controlled OTLP behavior, Micrometer use, JSON correlation, and pre-sink redaction. |
| Error and compatibility contracts are stable and tested | PASS | `contracts/problem-detail.md` fixes media type and fields, and `quickstart.md` makes both web-stack and cross-cutting verification runnable. |
| Independent override is preserved | PASS | Each capability has a distinct missing-bean or application-resource back-off trigger; back-off tests assert unrelated capabilities remain active. |

The Phase 1 design introduces no unresolved clarification, constitution violation, or out-of-scope service behavior.

## Project Structure

### Documentation (this feature)

```text
specs/001-shared-platform-starter/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── configuration.md
│   ├── module-and-backoff.md
│   ├── observability.md
│   ├── problem-detail.md
│   ├── security.md
│   └── logging.md
└── checklists/
    └── requirements.md
```

`tasks.md` is the generated executable worklist for this feature and is maintained by the `$speckit-tasks` workflow.

### Source Code (repository root)

```text
.
├── settings.gradle.kts                    # Non-published three-project aggregator
├── build.gradle.kts                       # Shared repositories, Kotlin/Java 21, test conventions
├── gradle.properties
├── gradle/
│   └── wrapper/
├── gradlew
├── gradlew.bat
├── logistics-parent-service-bom/
│   └── build.gradle.kts                   # java-platform; Boot 4.1.0 import and all constraints
├── logistics-parent-service-autoconfigure/
│   ├── build.gradle.kts                   # Kotlin library; optional web compile classpaths
│   └── src/
│       ├── main/
│       │   ├── kotlin/com/hz/logistics/parentservice/autoconfigure/
│       │   │   ├── properties/            # PlatformProperties root plus five capability groups
│       │   │   ├── security/              # Common mapping plus isolated mvc/reactive branches
│       │   │   ├── tracing/               # W3C and OTLP integration/customization
│       │   │   ├── metrics/               # Micrometer policy and customizer SPI
│       │   │   ├── errors/                # Shared factory plus isolated mvc/reactive handlers
│       │   │   └── logging/               # Sanitizer, redacting fan-out appender, OTel installer
│       │   └── resources/
│       │       ├── META-INF/spring/
│       │       │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│       │       └── logback-spring.xml
│       ├── test/kotlin/                    # Unit, binding, context, BOM-consumption tests
│       ├── mvcIntegrationTest/kotlin/      # MockMvc sample context and MVC acceptance suite
│       ├── webfluxIntegrationTest/kotlin/  # WebTestClient sample context and reactive suite
│       ├── loggingTest/kotlin/             # JSON shape and pre-sink redaction corpus suite
│       └── test/resources/
│           └── redaction-corpus/           # Synthetic credentials and configured PII samples
└── logistics-parent-service-starter/
    └── build.gradle.kts                    # Thin dependency entry point; no implementation source
```

**Structure Decision**: Use a single Gradle build whose root includes only the three required subprojects. The BOM owns all dependency versions. The auto-configuration module owns every Kotlin implementation class and the default `logback-spring.xml`. The starter contains dependency declarations only and never depends on `spring-boot-starter-web` or `spring-boot-starter-webflux`. MVC and WebFlux sample applications live in dedicated test source sets inside the auto-configuration module, so they validate real consumption without becoming additional Gradle subprojects.

## Module Dependency and Responsibility Model

```text
consumer application
├── enforcedPlatform(logistics-parent-service-bom)
├── implementation(logistics-parent-service-starter)
└── implementation(application-selected MVC or WebFlux starter)

logistics-parent-service-starter
└── api(logistics-parent-service-autoconfigure + non-web platform prerequisites)

logistics-parent-service-autoconfigure
├── implementation(non-web Spring Boot/Security APIs + spring-boot-starter-opentelemetry)
├── compileOnly(OTel Logback appender and Logback APIs used by the logging implementation)
└── compileOnly(Servlet/MVC and Reactive/WebFlux APIs)

logistics-parent-service-bom
└── constraints(all platform, Spring Boot, observability, logging, and test coordinates)
```

The BOM does not depend on implementation modules. The auto-configuration module does not depend on the starter. The starter depends on auto-configuration but contains no behavior. Consumer applications alone select a web stack.

## Design Decisions

- Register all candidates through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`; isolate MVC and WebFlux types in separately conditioned auto-configuration classes.
- Compile configured public patterns once with Spring `PathPatternParser` and use the same grammar and matching helper in both security branches. Accept literal paths, `?` and segment `*`, and terminal `/**`; reject relative paths, empty/double segments, `..`, URI-template variables, inline regex, encoded-slash tricks, and non-terminal `**` at startup while security is active.
- Create default security chains only when their selected stack is active and the application has not provided the corresponding `SecurityFilterChain` or `SecurityWebFilterChain`; application `JwtDecoder`/`ReactiveJwtDecoder` beans and documented authority converters are reused without disabling the platform chain. Security disablement backs off only security.
- Use `spring-boot-starter-opentelemetry` as the single Boot-managed tracing/OTLP dependency instead of declaring Micrometer and OpenTelemetry SDK modules individually. Map the canonical `logistics.parent-service.tracing.*` properties to Spring Boot's tracing/export configuration or supported builder customizers. W3C propagation remains active without an exporter; an OTLP endpoint activates trace export, and application-provided observability components trigger only tracing back-off.
- Route all platform-handled errors, including authentication and authorization failures, through a shared problem factory so MVC and WebFlux serialize the same fields and safe details.
- Make redaction the single fan-out boundary before JSON encoding and the OpenTelemetry Logback appender. This prevents a second sink from seeing the raw event and is verified using a synthetic corpus that includes nested exception data.
- Record compatibility-sensitive surfaces in contracts and require Semantic Versioning assessment and migration notes whenever they change.

## Complexity Tracking

No constitution violation requires justification.
