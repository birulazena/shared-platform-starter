<!--
Sync Impact Report
- Version change: 1.0.0 -> 1.0.1
- Modified principles: placeholder Principle 1 -> I. Platform Consistency and Modular Architecture
- Modified principles: placeholder Principle 2 -> II. Secure-by-Default Authentication
- Modified principles: placeholder Principle 3 -> III. Web-Stack Neutrality and Safe Auto-Configuration
- Modified principles: placeholder Principle 4 -> IV. Standardized Observability and Structured Diagnostics
- Modified principles: placeholder Principle 5 -> V. Stable API Contracts, Error Handling, and Quality Gates
- Added sections: Additional Constraints; Development Workflow and Quality
- Modified constraints: canonical platform configuration namespace aligned with
  the approved feature specification as `logistics.parent-service.*`
- Removed sections: none
- Follow-up TODOs: none
-->

# HZ Logistics Parent Service Constitution

## Core Principles

### I. Platform Consistency and Modular Architecture

The platform MUST centralize cross-cutting capabilities for all ten company
microservices. Its architecture MUST consist of exactly these three modules:

- `logistics-parent-service-bom` MUST align dependency versions for the platform
  and its consumers.
- `logistics-parent-service-autoconfigure` MUST contain implementation code and
  conditional auto-configuration.
- `logistics-parent-service-starter` MUST remain a thin dependency entry point
  that exposes the supported platform experience without duplicating
  implementation logic.

Future features MUST preserve these module responsibilities. A feature that does
not belong in the shared platform MUST remain outside the starter. This structure
keeps platform behavior consistent while allowing each service to adopt only the
capabilities it needs.

### II. Secure-by-Default Authentication

The platform MUST use Spring Security OAuth2 Resource Server with JWT validation
and Keycloak-compatible issuer configuration. All application endpoints MUST
require authentication by default. Health and info actuator endpoints MAY be
public by default, and any additional public endpoints MUST be configurable
through application properties.

The starter MUST NOT hardcode Keycloak-specific JWT claim locations, including
`realm_access.roles` and `resource_access`. JWT role extraction MUST be
configurable through application properties with both a configurable nested
claims path and a configurable role prefix. The default role prefix MUST be
`ROLE_`.

The same security behavior MUST be supported for Servlet/MVC and Reactive/WebFlux
applications. Applications MUST be able to customize or replace the default
security behavior through supported properties or application-provided beans.
Security tests MUST prove the default denial of unauthenticated access, the
configured public endpoint behavior, nested-claim extraction, role-prefix
handling, and application-level overrides.

### III. Web-Stack Neutrality and Safe Auto-Configuration

The common starter MUST NOT depend on or force `spring-boot-starter-web` or
`spring-boot-starter-webflux`. Each service MUST choose its own web stack.

The autoconfiguration MUST provide separate conditional Servlet/MVC and
Reactive/WebFlux branches. Each branch MUST use classpath and
web-application conditions, MUST back off when an application provides its own
compatible bean, and MUST avoid creating conflicting infrastructure. The single
public starter artifact MUST support both blocking and reactive services without
requiring separate public starter artifacts.

Auto-configuration changes MUST include context or integration coverage for the
conditions, back-off behavior, and supported web-stack variants. This prevents a
reusable platform dependency from changing an application's web model or
silently replacing application-owned infrastructure.

### IV. Standardized Observability and Structured Diagnostics

The platform MUST provide consistent observability across all services. It MUST
support W3C Trace Context propagation, including the `traceparent` header, for
both inbound and outbound requests. It MUST provide OpenTelemetry tracing with
OTLP-based export.

Application custom metrics MUST use Micrometer APIs. Business code MUST NOT use
the OpenTelemetry Metrics API directly. The platform MUST provide structured
JSON logging through Logback and `logback-spring.xml`, with log correlation to
trace context where supported.

The currently approved logging integration is
`io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.21.0-alpha`.
All dependency versions, including observability dependencies, MUST be
controlled through the platform BOM. JWTs, `Authorization` headers, passwords,
tokens, secrets, and unnecessary personal data MUST NOT be logged. Diagnostic
fields and redaction behavior MUST be covered by tests.

### V. Stable API Contracts, Error Handling, and Quality Gates

All services MUST expose errors through a common RFC 7807-compatible Spring
`ProblemDetail` contract. The default contract MUST use the standard minimal
ProblemDetail fields and MUST include a `traceId` for correlation. Servlet and
reactive implementations MAY differ internally, but their externally visible
error contract MUST remain consistent.

The platform starter MUST be tested as reusable infrastructure, not only as
isolated classes. Required quality coverage MUST include unit tests,
auto-configuration tests, Servlet/MVC integration tests, WebFlux integration
tests, security tests with mock JWTs, ProblemDetail contract tests,
trace-context tests, and structured logging tests. A change is not complete until
its applicable quality coverage passes for every affected supported web stack.

## Additional Constraints

- The build system MUST use Gradle Kotlin DSL. Implementation code MUST use
  Kotlin. The runtime language level MUST be Java 21. The framework baseline MUST
  be Spring Boot 4.0.7. Spring Boot MUST NOT be upgraded beyond 4.0.7 implicitly.
  Any future Spring Boot upgrade MUST include an explicit compatibility review,
  migration plan, dependency review, observability and logging validation, and a
  constitution or architecture decision update when applicable.
- The platform MUST retain the three-module architecture defined in Principle I
  and MUST use BOM-based dependency management. Maven, Nexus, and Artifactory
  publication are not current requirements. Future CI/CD or repository
  publication may be added without changing the core architecture principles.
- The common starter MUST NOT force MVC or WebFlux. Servlet and Reactive
  auto-configuration MUST remain conditional and MUST back off for compatible
  application-provided beans.
- Platform configuration MUST be organized under the canonical namespace
  `logistics.parent-service.*`, with capability-specific namespaces
  `logistics.parent-service.security.*`,
  `logistics.parent-service.tracing.*`,
  `logistics.parent-service.metrics.*`,
  `logistics.parent-service.errors.*`, and
  `logistics.parent-service.logging.*`. Defaults MUST be explicit,
  configurable, and overridable through supported application-level properties
  or application-provided beans.
- Endpoint access MUST be secure by default. Public endpoint behavior MUST be
  configurable. JWT nested claims paths and role prefixes MUST be configurable,
  with `ROLE_` as the default role prefix and no hardcoded Keycloak claim path.
- Errors MUST use an RFC 7807-compatible `ProblemDetail` contract with a
  correlation `traceId`. Trace propagation MUST support W3C `traceparent` for
  inbound and outbound requests, and telemetry export MUST support OTLP.
- Custom application metrics MUST use Micrometer. Structured JSON logging MUST
  use Logback and `logback-spring.xml`. Sensitive-data redaction MUST prevent
  logging JWTs, authorization headers, passwords, tokens, secrets, and
  unnecessary personal data.
- Detailed class lists, dependency declarations, package layouts,
  `application.yml` examples, and implementation steps are not defined by this
  constitution. They MUST be deferred to future `$speckit-specify`,
  `$speckit-plan`, `$speckit-tasks`, and `$speckit-implement` workflows.

## Development Workflow and Quality

- All future Spec Kit artifacts MUST be written in English.
- Feature requirements MUST be specified before implementation begins.
- Architecture and dependency decisions MUST be documented before coding begins.
- Every auto-configuration change MUST include appropriate application-context
  or integration coverage.
- Both MVC and WebFlux behavior MUST be validated whenever shared infrastructure
  is affected.
- Mock JWTs are sufficient for the default security test strategy. Testcontainers
  Keycloak is optional and MUST be introduced only when mock JWT tests cannot
  validate a real integration requirement.
- Dependency versions MUST remain aligned through the platform BOM.
- Breaking changes MUST include migration notes and explicit compatibility
  review.
- When CI/CD is introduced, formatting, compilation, tests, and dependency
  consistency MUST be mandatory CI quality gates.
- The platform starter and BOM MUST follow Semantic Versioning:
  - PATCH releases MUST contain compatible fixes only.
  - MINOR releases MUST contain backward-compatible capabilities only.
  - MAJOR releases MUST be used for breaking API, configuration, security,
    logging, error-contract, or compatibility changes.

## Governance

This constitution is the highest-level engineering policy for the HZ Logistics
Parent Service project. Every implementation plan and pull request MUST verify
compliance with it. A plan or pull request that violates a principle MUST either
be revised or include an approved amendment or documented temporary exception.

Amendments MUST state a documented reason, receive explicit review, use an
appropriate Semantic Versioning bump, and include migration notes whenever
behavior or compatibility changes. Changes to the Spring Boot baseline, security
defaults, error contract, tracing propagation, metrics model, or logging format
MUST receive explicit compatibility validation before approval. Implementation
complexity MUST be justified by a concrete platform or service need.

Temporary exceptions MUST identify an owner, state the reason, define the affected
scope, and include a removal plan. Exceptions do not change this constitution and
MUST be removed or renewed through explicit review before their removal date.

**Version**: 1.0.1 | **Ratified**: 2026-08-19 | **Last Amended**: 2026-08-19
