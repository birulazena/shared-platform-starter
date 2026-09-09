# HZ Logistics Parent Service

HZ Logistics Parent Service is a reusable Kotlin/Spring Boot platform for HZ
Logistics services. It provides shared security, tracing, metrics policy,
ProblemDetail responses, structured logging, and redaction. It does not provide
business endpoints, persistence, a database, an identity provider, or a
telemetry collector.

This repository is a library build, not a runnable business service. A
consuming service adds the BOM and starter, selects its own web stack, and
configures the platform capabilities it needs.

## Platform baseline

| Component | Version |
|---|---:|
| Java | 21 |
| Kotlin | 2.3.21 |
| Gradle Wrapper | 9.3.0 |
| Spring Boot | 4.1.0 |
| Platform release | 0.1.0 |

The root project is an aggregator only. It is not published as a fourth
platform module.

## Modules

| Module | Responsibility | Consumer usage |
|---|---|---|
| logistics-parent-service-bom | Aligns Spring Boot, Kotlin, Spring Security, Micrometer/OpenTelemetry, Logback, and test dependencies. | Import as one enforced BOM. |
| logistics-parent-service-autoconfigure | Contains the implementation, configuration properties, and conditional Spring Boot auto-configuration. | Do not normally add directly. It is brought in by the starter. |
| logistics-parent-service-starter | Thin public entry point with the auto-configuration and non-web runtime prerequisites. | Add to every consumer service. |

The starter deliberately does not depend on either
spring-boot-starter-web or spring-boot-starter-webflux. A service chooses one
web model explicitly, or chooses neither for a non-web application.

## Add the starter to a service

Use the same platform version for the BOM and starter:

~~~kotlin
dependencies {
    implementation(enforcedPlatform("com.hz.logistics:logistics-parent-service-bom:0.1.0"))
    implementation("com.hz.logistics:logistics-parent-service-starter:0.1.0")

    // Select exactly one for an HTTP service:
    implementation("org.springframework.boot:spring-boot-starter-web")
    // implementation("org.springframework.boot:spring-boot-starter-webflux")
}
~~~

For a worker, scheduled job, consumer, or other non-web application, omit both
web starters. For an HTTP service, do not add both web starters. The platform
detects the selected Spring application type and activates only the matching
MVC or WebFlux branch.

The starter already supplies the shared Actuator, Security resource-server,
OpenTelemetry runtime, and approved OpenTelemetry Logback appender
dependencies. Do not add the platform auto-configuration module separately
unless you are deliberately managing the dependency graph yourself.

### Local publication

The repository can publish its three modules to the local Maven repository:

~~~bash
./gradlew publishToMavenLocal
~~~

Then use the same coordinates without changing the consumer dependency
declarations. The consumer must explicitly search the local Maven repository:

~~~kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
~~~

If the consumer manages repositories in settings.gradle.kts, put the same
repositories in dependencyResolutionManagement instead. Keep mavenLocal() for
local development only; released consumers should use the team's Maven
repository and do not need the local repository.

## Consumer configuration

All platform-owned settings use one namespace:
logistics.parent-service.*. The five capability groups are independent and can
be enabled or disabled separately.

### Recommended complete example

This example is for an MVC service. Replace the web starter and
spring.main.web-application-type only when the service is reactive. The
spring.application.name value is used as the service identity by Spring Boot
telemetry and should be set explicitly.

~~~yaml
spring:
  application:
    name: shipment-api
  main:
    web-application-type: servlet

logistics:
  parent-service:
    security:
      enabled: true
      issuer: https://identity.example.test/realms/logistics
      public-endpoints:
        - /status
        - /public/**
      public-actuator-endpoints: true
      role-claims-path: realm_access.roles
      role-prefix: ROLE_

    tracing:
      enabled: true
      sampling-probability: 0.1
      otlp:
        endpoint: http://localhost:4318/v1/traces
        protocol: HTTP_PROTOBUF
        timeout: 10s
        compression: GZIP

    metrics:
      enabled: true
      common-tags:
        service: shipment-api
        environment: production

    errors:
      enabled: true
      detail-policy: GENERIC
      include-instance: true

    logging:
      enabled: true
      console-enabled: true
      otel-enabled: true
      redaction-mask: "[REDACTED]"
      additional-sensitive-fields:
        - customerEmail
      additional-sensitive-paths:
        - shipment.recipient.phone

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics

  # The platform owns trace configuration above. These properties are only
  # for the additional log and metrics exporters.
  opentelemetry:
    logging:
      export:
        otlp:
          endpoint: http://localhost:4318/v1/logs

  # The platform applies Micrometer policy but does not choose a metrics
  # registry or destination. This explicitly enables the OTLP metrics registry.
  otlp:
    metrics:
      export:
        enabled: true
        url: http://localhost:4318/v1/metrics
~~~

The management block is the part that is commonly missed. The platform owns
trace propagation, sampling, correlation, and the canonical trace exporter.
However, metrics.common-tags alone does not export metrics, and
console/structured logging alone does not export logs to an OTLP collector.
Configure the management exporters below for logs and metrics.

The example uses the platform configuration for traces and Spring Boot's
exporter configuration for logs and metrics:

| Signal | Export property | Collector path |
|---|---|---|
| Logs | management.opentelemetry.logging.export.otlp.endpoint | /v1/logs |
| Metrics | management.otlp.metrics.export.url | /v1/metrics |

The collector must be reachable from the service. The platform does not start
or provision one. For a remote collector, replace localhost with the collector
host and keep credentials outside source control by using environment variable
placeholders or the deployment platform's secret configuration.

Trace sampling and OTLP trace export are configured only in the canonical
logistics.parent-service.tracing.* namespace. Do not duplicate them under
management.tracing.* or management.opentelemetry.tracing.*. In particular, do
not add a second sampling probability or trace endpoint to the consumer
application. The management trace properties are relevant only when an
application deliberately owns the complete tracing setup and the platform
tracing contribution has backed off.

Without logistics.parent-service.tracing.otlp.endpoint, local tracing, W3C
propagation, correlation, and ProblemDetail/log trace IDs remain active; only
the platform-owned OTLP trace exporter is not created. HTTP/protobuf endpoints
must be absolute HTTP(S) URIs. The GRPC option accepts a valid gRPC target.
Header names and values are validated, but header values are always treated as
secrets and are never written to diagnostics, logs, ProblemDetails, or
telemetry attributes.

### Automatic method security

With `logistics.parent-service.security.enabled=true`, or with the property
absent (its default is `true`), the selected web stack receives platform method
authorization without a consumer-side enablement annotation:

| Selected stack | Automatic mechanism | Supported method annotations |
|---|---|---|
| Servlet/MVC | `@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)` | `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, `@PostFilter`, `@Secured`, `@RolesAllowed`, `@PermitAll`, `@DenyAll` |
| Reactive/WebFlux | `@EnableReactiveMethodSecurity` | Publisher-returning `@PreAuthorize` and `@PostAuthorize`, including Reactor-context-aware checks |

Only the selected web application type contributes method-security
infrastructure. A non-web application receives none from the platform, even if
both MVC and WebFlux APIs are present on the classpath.

Method expressions reuse the authorities from the existing JWT resource-server
flow. Configured nested roles keep the configured prefix (default `ROLE_`), and
standard `scope` and `scp` claims remain available as `SCOPE_<permission>`.
For example, `hasAuthority('ROLE_dispatcher')` and
`hasAuthority('SCOPE_shipments.read')` work at method level without a new
converter or claim-mapping property.

An application-owned `SecurityFilterChain` or `SecurityWebFilterChain` backs
off only the corresponding platform HTTP chain. It does not disable selected
stack method authorization; the application-owned chain must still authenticate
bearer tokens when the method layer is expected to evaluate them. If matching
method-security infrastructure is already present, the platform method branch
backs off to avoid duplicate advisors/interceptors. A web-chain bean alone is
not a method-security back-off signal. Setting
`logistics.parent-service.security.enabled=false` disables both platform web and
method security, while leaving application-owned security configuration and
unrelated platform capabilities available.

For a protected method endpoint, the layered result is `200` for a valid token
with the required authority, `403` for a valid token without it, and `401` for
no bearer token before the method is invoked. The MVC and WebFlux contracts are
equivalent at this boundary.

## Configuration reference

Every property below is part of the public compatibility contract. Defaults are
applied when the property is absent. A property marked conditional is required
only when the corresponding default branch is active.

### Security

| Property | Default | Required? | Purpose and usage |
|---|---:|---|---|
| logistics.parent-service.security.enabled | true | No | Enables the platform web and method-security contributions. Set false when the service owns security itself or does not need platform security. |
| logistics.parent-service.security.issuer | none | Conditional | Absolute HTTP(S) issuer used for JWT discovery and issuer validation. Required when the default MVC/WebFlux security chain is active. Not required for non-web services, a disabled capability, or an application-owned complete security chain. |
| logistics.parent-service.security.public-endpoints | [] | No | Permit-only application paths. Every other request requires authentication when the default chain is active. |
| logistics.parent-service.security.public-actuator-endpoints | true | No | Makes present and exposed health and info Actuator endpoints public. It does not expose endpoints; use management.endpoints.web.exposure.include for that. |
| logistics.parent-service.security.role-claims-path | none | No | Dot-separated nested JWT claim path, for example realm_access.roles. Supports a string or list of role values. |
| logistics.parent-service.security.role-prefix | ROLE_ | No | Prefix added to mapped authorities. An empty string is valid when the application uses unprefixed authorities. |

Public endpoint patterns use a deliberately small shared grammar in MVC and
WebFlux: absolute paths, ?, segment *, and terminal /**. Regexes, URI
variables, relative paths, and ** in the middle of a pattern are rejected at
startup. Query strings are not part of the matching path.

### Tracing and OTLP traces

| Property | Default | Required? | Purpose and usage |
|---|---:|---|---|
| logistics.parent-service.tracing.enabled | true | No | Enables W3C propagation, local tracing integration, correlation, and the platform tracing contribution. |
| logistics.parent-service.tracing.sampling-probability | 0.1 | No | Parent-based recording/export probability from 0.0 through 1.0. Sampling does not remove propagation or correlation IDs. |
| logistics.parent-service.tracing.otlp.endpoint | none | No | Activates the platform-owned asynchronous OTLP trace exporter when present. |
| logistics.parent-service.tracing.otlp.protocol | HTTP_PROTOBUF | No | OTLP transport: HTTP_PROTOBUF or GRPC. |
| logistics.parent-service.tracing.otlp.headers | {} | No | Export headers for the canonical platform trace exporter. Values are secrets. |
| logistics.parent-service.tracing.otlp.timeout | 10s | No | Timeout for one exporter operation. Must be positive and no longer than 60s. |
| logistics.parent-service.tracing.otlp.compression | GZIP | No | OTLP payload compression: NONE or GZIP. |

Valid inbound W3C traceparent and tracestate values are continued. Missing or
malformed context starts a safe local trace. Outbound requests created from
Spring Boot's managed RestClient.Builder, RestTemplateBuilder, or
WebClient.Builder receive W3C propagation. Directly constructed clients are
outside this guarantee.

### Metrics

| Property | Default | Required? | Purpose and usage |
|---|---:|---|---|
| logistics.parent-service.metrics.enabled | true | No | Enables the platform Micrometer policy. It does not create a registry or select an exporter. |
| logistics.parent-service.metrics.common-tags | {} | No | Bounded, non-sensitive tags applied to every selected MeterRegistry, for example environment=production. |

Business metrics should use Micrometer APIs (Counter, Timer, Gauge, and
instrument builders). The application owns the registry and backend. For OTLP
metrics, configure management.otlp.metrics.export.url as shown above. For
Prometheus or another backend, add and configure that backend in the consumer;
the platform does not force one.

Do not put credentials, personal data, request IDs, or unbounded values in
metric tags. Sensitive tag names or values are rejected by the default metrics
policy.

### Errors and ProblemDetail

| Property | Default | Required? | Purpose and usage |
|---|---:|---|---|
| logistics.parent-service.errors.enabled | true | No | Enables the platform MVC/WebFlux error handlers and shared ProblemDetail factory. |
| logistics.parent-service.errors.detail-policy | GENERIC | No | GENERIC returns safe stable details. SAFE may disclose a bounded safe application detail, but never secrets or stack traces. |
| logistics.parent-service.errors.include-instance | true | No | Adds the sanitized request path as instance; query strings and fragments are excluded. |

When a platform handler writes an error, the response uses
application/problem+json and contains stable type, title, status, and detail
fields, an optional instance, and a non-empty traceId. MVC and WebFlux expose
the same external field contract.

### Structured logging and redaction

| Property | Default | Required? | Purpose and usage |
|---|---:|---|---|
| logistics.parent-service.logging.enabled | true | No | Enables the platform logging pipeline. |
| logistics.parent-service.logging.console-enabled | true | No | Enables the default structured JSON console sink. |
| logistics.parent-service.logging.otel-enabled | true | No | Sends sanitized events to an available OpenTelemetry log pipeline. It does not configure the pipeline endpoint; use management.opentelemetry.logging.export.otlp.endpoint. |
| logistics.parent-service.logging.redaction-mask | [REDACTED] | No | Non-blank replacement for redacted values. |
| logistics.parent-service.logging.additional-sensitive-fields | [] | No | Additional exact field/header/query names to redact, case-insensitively. Example: customerEmail. |
| logistics.parent-service.logging.additional-sensitive-paths | [] | No | Additional exact dot-separated structured paths. Example: shipment.recipient.phone. |

The default resource is logback-spring.xml. It creates structured JSON and
sanitizes one immutable event before fan-out to the console and OpenTelemetry
sinks. Baseline redaction always covers authorization values, bearer/basic
credentials, JWTs, passwords, access/refresh/ID/API tokens, secrets, and API
keys. The configured mask is used for both baseline and custom rules.

If the consuming service provides its own logback-spring.xml or logging.config,
that resource takes precedence over the platform resource. The service then
owns the logging configuration and must preserve structured JSON, trace
correlation, and baseline redaction if it wants the platform logging contract.

## Observability end-to-end

The platform treats the signals as related but independent:

~~~text
request / scheduled work
        |
        +--> W3C trace context and Micrometer observation
        |       +--> OTLP traces (when an exporter is configured)
        |       +--> traceId/spanId in logs and ProblemDetail
        |
        +--> Micrometer MeterRegistry
        |       +--> common tags from logistics.parent-service.metrics
        |       +--> OTLP metrics (when management.otlp.metrics.export is configured)
        |
        +--> sanitized Logback event
                +--> structured console JSON
                +--> OTLP logs (when management.opentelemetry.logging.export is configured)
~~~

### Minimum configuration for local export

Assuming an OTLP/HTTP collector listens on localhost:4318, use this additional
block in the consumer application:

~~~yaml
management:
  opentelemetry:
    logging:
      export:
        otlp:
          endpoint: http://localhost:4318/v1/logs
  otlp:
    metrics:
      export:
        enabled: true
        url: http://localhost:4318/v1/metrics
~~~

For deterministic local tracing tests, set
logistics.parent-service.tracing.sampling-probability to 1.0. In production,
choose a rate appropriate for traffic volume and cost.

This block configures destinations; it does not replace the platform capability
flags. In particular:

- logging.otel-enabled must remain true for sanitized events to be sent to the OTel log pipeline;
- metrics.enabled controls the platform metrics policy, while the management.otlp.metrics.export.* block selects the OTLP metrics exporter;
- local console JSON and trace correlation can work without any collector; exporter failures are diagnostic-only and must not fail requests;
- a collector must accept the selected protocol and signal paths. The platform does not retry synchronously on request threads.

### Telemetry credentials

Never hard-code OTLP credentials in a committed YAML file. Use secret-backed
placeholders. For the platform-owned trace exporter, headers are configured at
logistics.parent-service.tracing.otlp.headers; those values are always treated
as secrets. For the Spring Boot-managed exporters, use the equivalent Boot
exporter header settings supported by the service's Spring Boot version or
inject them through the deployment environment. In all cases, credentials are
excluded from diagnostics, logs, ProblemDetails, and telemetry attributes.

## Application overrides and back-off

Back-off is capability-scoped. Replacing one contribution does not disable the
other capabilities.

| Capability | Application override | What backs off |
|---|---|---|
| Security | Complete SecurityFilterChain or SecurityWebFilterChain | Only the selected platform security chain. Automatic method security remains active unless matching method-security infrastructure is application-owned. |
| JWT | Compatible JwtDecoder, ReactiveJwtDecoder, or authentication converter | Only the corresponding platform default. The default chain still requires a valid issuer configuration. |
| Tracing | OpenTelemetry, SdkTracerProvider, ContextPropagators, supported exporter/customizer, or processor | Only the platform tracing contribution that conflicts with the application owner. |
| Metrics | PlatformMetricsCustomizer and an application-owned MeterRegistry | Only the platform common-tag policy/customizer. |
| Errors | PlatformProblemDetailFactory | Only the default factory/handler contribution that is replaced. Preserve the documented response contract when compatibility is required. |
| Logging | PlatformLogSanitizer or an application logging resource | Only the configurable sanitizer policy or default resource. Baseline redaction must not be bypassed by a compatible custom sanitizer. |

For example, defining a PlatformMetricsCustomizer does not turn off tracing,
security, errors, or logging. An application-owned logging resource has
precedence over the platform logback-spring.xml; this is logging-only back-off.

## Security, tracing, errors, and logging guarantees

- Security is stateless and deny-by-default when enabled. The default chain validates issuer-backed JWTs and has equivalent MVC/WebFlux public-pattern and nested-role behavior.
- Automatic method authorization is enabled for the selected MVC or WebFlux stack when security is enabled or its property is absent; it reuses the existing `ROLE_` and `SCOPE_` authorities and preserves the `200`/`401`/`403` layered outcomes.
- W3C trace context is the cross-service propagation format. Missing or malformed inbound context is safe and non-fatal. Managed outbound clients propagate the current context.
- Traces are sampled with a parent-based ratio sampler. Sampling controls recording/export, not whether correlation IDs are available.
- OTLP export is asynchronous. A missing, unavailable, or rejecting collector is diagnostic-only and must not turn a successful request into a failure.
- ProblemDetail responses use a stable application/problem+json contract and always contain a usable traceId when the platform error factory is active.
- Structured logging is JSON and correlated with trace context. Redaction is performed before any sink receives the event, so console and OTel sinks see the same sanitized event.
- Credentials, JWTs, authorization data, passwords, tokens, secrets, and configured sensitive fields are not allowed to reach a default sink.

## Run and verify the repository

### Prerequisites

- JDK 21;
- a Bash-compatible shell, or gradlew.bat on Windows;
- network access to resolve Maven Central dependencies on the first build.

Check the toolchain and included modules:

~~~bash
java -version
./gradlew --version
./gradlew projects
~~~

Expected projects are exactly:

~~~text
:logistics-parent-service-bom
:logistics-parent-service-autoconfigure
:logistics-parent-service-starter
~~~

### Full release gate

~~~bash
./gradlew clean check
~~~

The root check task runs BOM alignment, unit/context tests, MVC integration,
WebFlux integration, and the logging/redaction suite. This repository does not
contain a bootRun application; run a consuming service's bootRun task after
adding the starter and application configuration above.

### Focused checks

~~~bash
./gradlew :logistics-parent-service-bom:check
./gradlew :logistics-parent-service-autoconfigure:test
./gradlew :logistics-parent-service-autoconfigure:mvcIntegrationTest
./gradlew :logistics-parent-service-autoconfigure:webfluxIntegrationTest
./gradlew :logistics-parent-service-autoconfigure:loggingTest
./gradlew :logistics-parent-service-starter:check
~~~

The MVC and WebFlux suites intentionally consume the public starter and select
their web stack explicitly. The logging suite verifies valid JSON, correlation,
baseline redaction, configured sensitive fields, nested exceptions, and the
OpenTelemetry Logback appender.

For the automatic method-security scenarios, the focused commands are:

~~~bash
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
~~~

These scenarios cover selected-stack isolation, matching manual-enablement
back-off, application-owned HTTP chains, disabled platform security,
custom-prefix and actuator behavior, mapped role/scope authorities, and the
`200`/`401`/`403` matrix.

## Contract documentation

The detailed compatibility contracts and release evidence are maintained in
[specs/001-shared-platform-starter/compatibility-review.md](specs/001-shared-platform-starter/compatibility-review.md):

- [configuration contract](specs/001-shared-platform-starter/contracts/configuration.md)
- [security contract](specs/001-shared-platform-starter/contracts/security.md)
- [observability contract](specs/001-shared-platform-starter/contracts/observability.md)
- [ProblemDetail contract](specs/001-shared-platform-starter/contracts/problem-detail.md)
- [logging and redaction contract](specs/001-shared-platform-starter/contracts/logging.md)
- [module and back-off contract](specs/001-shared-platform-starter/contracts/module-and-backoff.md)
- [quickstart validation guide](specs/001-shared-platform-starter/quickstart.md)

The generated Spring configuration metadata in the auto-configuration module is
the source of editor completion for the complete canonical property set. Only
logistics.parent-service.* is supported; alternate roots are not aliases.

## Versioning and migration

The current platform version is 0.1.0.

- PATCH releases contain compatible fixes.
- MINOR releases add backward-compatible capabilities.
- MAJOR releases are required for breaking changes to module responsibilities, dependency direction, configuration names/defaults, security behavior, W3C/OTLP propagation, ProblemDetail fields, logging/redaction behavior, or supported web-stack selection.

Every compatibility-sensitive change must update the affected contract,
compatibility review, regression evidence, and migration notes before release.
The supported adoption migration is additive: import the BOM, add the starter,
select a web stack when applicable, and configure the canonical namespace.
