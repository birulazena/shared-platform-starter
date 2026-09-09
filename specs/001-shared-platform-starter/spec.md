# Feature Specification: Shared Platform Starter

**Feature Branch**: `001-shared-platform-starter`

**Created**: 2026-08-19

**Status**: Draft

**Input**: User description: "Create a specification for a shared platform starter used by all ten HZ Logistics microservices. The platform must provide reusable cross-cutting capabilities through exactly three Gradle Kotlin DSL modules: BOM, auto-configuration, and a thin starter. It must support Kotlin, Java 21, Spring Boot 4.1.0, and both Servlet/MVC and Reactive/WebFlux applications without forcing either web stack. Cover secure-by-default OAuth2 resource-server security, configurable JWT role mapping, W3C tracing with OTLP, Micrometer metrics, RFC 7807 ProblemDetail errors, and structured redacted JSON logging."

## Clarifications

### Session 2026-08-19

- Q: What matching syntax should `logistics.parent-service.security.public-endpoints` use for determining which endpoints are publicly accessible? → A: Use one documented common path-pattern syntax applied identically in both MVC and WebFlux. A valid pattern is an absolute application path beginning with `/` and may contain literal segments, `?` for one character within a segment, `*` for zero or more non-`/` characters within a segment, or terminal `/**` for zero or more complete trailing segments. Query strings and fragments are excluded from matching; relative paths, empty/double segments, `..`, URI-template variables, inline regex, encoded-slash tricks, and non-terminal `**` are invalid. Invalid patterns fail startup while the platform security chain is active; valid entries only permit matching paths and all non-matching paths remain protected.
- Q: Should the canonical configuration namespace be `logistics.parent-service.*`, with capability namespaces such as `logistics.parent-service.security.*` and `logistics.parent-service.tracing.*`? → A: `logistics.parent-service.*`, with capability-specific subnamespaces.
- Q: Which default policy should govern redaction of personal data beyond the explicitly named secrets and credentials? → A: Redact named credential categories plus application-configured personal-data fields or paths.
- Q: Should application-provided beans be able to independently override each capability—security, tracing, metrics, errors, and logging—while unrelated platform defaults remain active? → A: Independent override and back-off for security, tracing, metrics, errors, and logging.
- Q: Which application-owned security components trigger platform back-off? → A: An application `SecurityFilterChain` or `SecurityWebFilterChain` disables the corresponding selected-stack platform chain. An application `JwtDecoder`, `ReactiveJwtDecoder`, or documented authority converter is reused by the platform chain and does not disable secure default denial. `security.enabled=false` disables only platform security; tracing, metrics, errors, and logging remain eligible.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Adopt one shared platform dependency (Priority: P1)

As an HZ Logistics service team, I want to add one supported platform starter to my service so that common security, diagnostics, errors, tracing, metrics, and logging behavior is available consistently without copying infrastructure into each service.

**Why this priority**: All ten microservices depend on the platform. A consistent, low-friction adoption path is the foundation for every other capability and prevents service-specific infrastructure from diverging.

**Independent Test**: Add the public starter and BOM to a representative MVC service and a representative WebFlux service, configure only the required environment values, and verify that both services start with the same platform capabilities while retaining their selected web stack.

**Acceptance Scenarios**:

1. **Given** a Kotlin service running on Java 21 with Spring Boot 4.1.0 and Servlet/MVC dependencies, **when** it adopts the single public starter, **then** the service starts with platform infrastructure and no Reactive/WebFlux web infrastructure is required or forced.
2. **Given** a Kotlin service running on Java 21 with Spring Boot 4.1.0 and Reactive/WebFlux dependencies, **when** it adopts the same public starter, **then** the service starts with platform infrastructure and no Servlet/MVC web infrastructure is required or forced.
3. **Given** any service consumes platform dependencies, **when** the platform version is changed, **then** the BOM supplies one aligned version set for platform dependencies and consumers do not need a second platform artifact for their chosen web stack.

---

### User Story 2 - Secure an API by default (Priority: P1)

As a service owner, I want every endpoint to require a valid access token by default, while allowing explicitly configured public endpoints and application-owned security customization, so that an accidental omission does not expose an API.

**Why this priority**: Authentication is a safety boundary for every shared service and must be reliable before the platform is adopted broadly.

**Independent Test**: Exercise protected and public endpoints in both web stacks with no token, an invalid token, a valid token, and a valid token whose roles come from a configured nested claim. Verify the response status, mapped authorities, and application override behavior.

**Acceptance Scenarios**:

1. **Given** the default platform security is active, **when** an unauthenticated client calls a protected endpoint, **then** the request is rejected with an authentication error.
2. **Given** the default platform security is active, **when** a client presents a token with an invalid signature, expired time, or mismatched issuer, **then** the request is rejected and no protected resource is executed.
3. **Given** an endpoint matches a pattern listed in `logistics.parent-service.security.public-endpoints`, **when** an unauthenticated client calls that endpoint, **then** the request is allowed; non-matching endpoints remain protected.
4. **Given** a valid token contains role values at an application-configured nested claims path, **when** an endpoint checks an application role, **then** the role is available with the configured prefix, whose default is `ROLE_`.
5. **Given** an application provides a compatible security bean or supported override, **when** the service starts, **then** the platform backs off from the conflicting default and the application-owned behavior is used.

---

### User Story 3 - Follow a request across services (Priority: P1)

As an operator investigating a request, I want the trace context to survive inbound and outbound calls and appear in diagnostics, so that I can connect activity across the ten services and locate failures quickly.

**Why this priority**: Distributed request correlation is essential for operating a shared logistics platform and for using the common error and logging contracts.

**Independent Test**: Send a request with a valid W3C `traceparent` to each web-stack variant, make an outbound call, and inspect the propagated header, trace identifiers in logs and errors, and exported telemetry when an OTLP destination is configured.

**Acceptance Scenarios**:

1. **Given** an inbound request has a valid W3C `traceparent`, **when** the request is handled, **then** the platform continues the trace context and makes the trace and span identifiers available to diagnostics.
2. **Given** an outbound request is made while handling a traced request, **when** the request leaves the service, **then** it carries the corresponding W3C trace context.
3. **Given** an OTLP destination is configured, **when** traced activity occurs, **then** telemetry is eligible for export to that destination without requiring service-specific tracing code.
4. **Given** an inbound request has no trace context or has an invalid `traceparent`, **when** the request is handled, **then** the platform starts a safe new trace rather than trusting malformed context or failing the business request solely because propagation metadata is invalid.

---

### User Story 4 - Diagnose errors and activity consistently (Priority: P2)

As a developer or operator, I want metrics, structured logs, and errors to use consistent formats and correlation fields, so that dashboards, log searches, and client error handling work the same way in MVC and WebFlux services.

**Why this priority**: Consistent diagnostics reduce investigation time and make shared operational tooling useful across all ten services.

**Independent Test**: Trigger a successful request, an authentication failure, an authorization failure, an application error, and a validation-style error in both stacks. Inspect the metric output, JSON log events, and `application/problem+json` responses for required fields, correlation, and redaction.

**Acceptance Scenarios**:

1. **Given** application code records a custom counter, timer, or gauge through the supported metrics API, **when** the metric is emitted, **then** it is available through the service's configured metrics registry without requiring direct use of an OpenTelemetry metrics API.
2. **Given** a platform-handled or unhandled request error, **when** the service returns the response, **then** the body is an RFC 7807-compatible `ProblemDetail` representation containing a `traceId` and the response uses the problem-media type.
3. **Given** a request produces a log event, **when** the event is written, **then** it is structured JSON with standard severity/time/message fields and trace correlation fields when a trace exists.
4. **Given** a log message or exception contains an authorization header, JWT, password, token, secret, or unnecessary personal data, **when** the event is written, **then** the sensitive value is removed or masked and is not recoverable from the emitted event.

### Edge Cases

- If the issuer configuration is missing or invalid while the default security is enabled, the service fails in a clear, secure manner rather than silently running without token validation.
- If a JWT has a valid signature but the configured nested role path is absent, null, or has a non-string/non-list value, authentication may succeed but no roles are invented; authorization fails when a required role is missing.
- If the role prefix is explicitly changed, authority matching uses the configured value consistently; the default remains `ROLE_`.
- If public endpoint patterns overlap protected patterns, the common path-pattern syntax is evaluated deterministically and only matching configured entries are public; an unconfigured endpoint remains protected.
- If a configured public endpoint pattern is invalid, platform security fails startup with a validation error rather than silently ignoring the entry.
- If both MVC and WebFlux classes are present but the application selects one web application type, only the selected conditional branch creates web infrastructure and no duplicate security, error, or observation beans are created.
- If an application has no web stack, non-web platform capabilities may still load, but no MVC or WebFlux web infrastructure is created.
- If the application provides its own compatible security, error, tracing, metrics, or logging integration, the corresponding platform default backs off independently, unrelated platform capabilities remain active, and supported overrides are observable in startup behavior and tests. A complete MVC/WebFlux security chain triggers chain back-off; application decoders and authority converters are reused without disabling default denial.
- If the OTLP collector is unavailable or no OTLP destination is configured, request handling and error responses continue; export failures do not become application failures.
- If trace context is lost during asynchronous or reactive execution, the platform preserves the correlation context across supported execution boundaries or emits a new safe correlation value rather than attaching unrelated context.
- If an error occurs before normal request handling establishes a trace, the error response still contains a usable `traceId` and the corresponding diagnostic event uses the same identifier when possible.
- If content negotiation requests an unsupported error representation, the service returns the common problem representation or a safe equivalent status response without exposing stack traces or secrets.
- If a sensitive value appears in a nested exception, structured field, query parameter, or request header, redaction applies before the event is serialized. The baseline covers JWTs, `Authorization` headers, passwords, tokens, and secrets; applications may additionally configure personal-data fields or paths for redaction.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The platform MUST be delivered through exactly three Gradle Kotlin DSL modules: `logistics-parent-service-bom` for aligned dependency versions, `logistics-parent-service-autoconfigure` for implementation and conditional auto-configuration, and `logistics-parent-service-starter` as a thin dependency entry point. No fourth platform module or separate MVC/WebFlux starter is in scope.
- **FR-002**: The platform implementation MUST use Kotlin and MUST support Java 21 runtime compatibility and Spring Boot 4.1.0. Dependency versions, including observability and logging dependencies, MUST be controlled by the BOM, and the baseline MUST NOT be implicitly upgraded.
- **FR-003**: The public starter MUST allow a consuming application to choose Servlet/MVC or Reactive/WebFlux independently. It MUST NOT force either web stack by bringing in or requiring the other stack's web starter.
- **FR-004**: Auto-configuration MUST provide separate conditional MVC and WebFlux behavior based on the selected application web model and available classes, MUST avoid creating both branches for one application, and MUST back off when a compatible application bean owns the same responsibility. An application `SecurityFilterChain` or `SecurityWebFilterChain` MUST disable only the corresponding platform security chain; application decoders and documented authority converters MUST be reusable without disabling secure default denial.
- **FR-005**: When the default platform security is active, all application endpoints MUST require authentication unless they are explicitly included in `logistics.parent-service.security.public-endpoints`; health and info actuator endpoints MUST be public by default when those endpoints are present, with the default configurable. Public endpoint configuration MUST use the documented common path-pattern grammar from the security contract, applied identically in MVC and WebFlux. Each configured pattern MUST make only matching paths public; all non-matching paths MUST remain protected. Invalid patterns MUST fail startup while the platform security chain is active.
- **FR-006**: The platform MUST validate OAuth2 Resource Server JWTs using a configurable issuer under `logistics.parent-service.security.*`, including issuer, signature, time, and standard token validity checks, and MUST support Keycloak-compatible issuer configuration without hardcoding a Keycloak-specific role claim location.
- **FR-007**: The platform MUST allow applications to configure a nested JWT role claims path under `logistics.parent-service.security.*`. When configured, it MUST read string role values from that path and map them to authorities; when absent or malformed, it MUST not invent authorities. The default role prefix MUST be `ROLE_`, and the prefix MUST be configurable.
- **FR-008**: Security behavior, including authentication failures, authorization failures, nested role mapping, public endpoints, default denial, and application-provided security overrides, MUST be equivalent in MVC and WebFlux from a client perspective.
- **FR-009**: The platform MUST propagate W3C Trace Context through the `traceparent` header for inbound and outbound requests in both web stacks. Valid inbound context MUST be continued; missing or invalid context MUST result in a safe new trace.
- **FR-010**: The platform MUST provide OpenTelemetry tracing with configurable OTLP export under `logistics.parent-service.tracing.*`. Trace propagation and local correlation MUST remain usable when no destination is configured, and collector/exporter unavailability MUST not fail request processing.
- **FR-011**: The platform MUST provide application metrics through Micrometer under `logistics.parent-service.metrics.*`. Application custom metrics MUST be recordable through Micrometer-compatible APIs, and platform documentation and tests MUST not require business code to use the OpenTelemetry Metrics API directly.
- **FR-012**: The platform MUST provide a common RFC 7807-compatible error contract under `logistics.parent-service.errors.*` for MVC and WebFlux. Platform-generated and unhandled application errors MUST use a problem representation with standard `type`, `title`, `status`, `detail`, and `instance` semantics when applicable, MUST include a non-empty `traceId`, and MUST use `application/problem+json` when a response body is returned.
- **FR-013**: Error responses MUST not expose stack traces, access tokens, JWT contents, passwords, secrets, or unnecessary personal data. Authentication and authorization error responses MUST conform to the same common contract where a response body is returned.
- **FR-014**: The platform MUST provide structured JSON logging through Logback and `logback-spring.xml` under `logistics.parent-service.logging.*`, including timestamp, severity, logger/source, message, and trace correlation fields when available. The approved OpenTelemetry Logback integration version MUST remain governed by the BOM.
- **FR-015**: Sensitive-data redaction MUST apply before a log event is emitted and MUST cover JWTs, `Authorization` headers, passwords, tokens, and secrets in messages, structured fields, headers, query parameters, and nested exception data. Applications MUST be able to configure additional personal-data fields or paths for redaction; the platform MUST apply those configured rules in the same locations.
- **FR-016**: Applications MUST be able to configure issuer, public endpoints, nested role claims, role prefix, tracing/OTLP behavior, metrics behavior, error behavior, and logging/redaction behavior through the specified `logistics.parent-service.security.*`, `logistics.parent-service.tracing.*`, `logistics.parent-service.metrics.*`, `logistics.parent-service.errors.*`, and `logistics.parent-service.logging.*` namespaces or supported application-provided beans.
- **FR-017**: Auto-configuration MUST be safe to compose with application-owned compatible beans. Applications MUST be able to independently override security, tracing, metrics, errors, and logging. A complete application security chain MUST trigger selected-stack security-chain back-off, while application decoders and documented authority converters MUST be reused without disabling default denial. Each supported override MUST cause only the corresponding platform default to back off without preventing unrelated platform capabilities from loading.
- **FR-018**: The platform release MUST include unit and reusable-infrastructure coverage for BOM alignment, conditional auto-configuration, back-off behavior, both web stacks, security with mock JWTs, public endpoints, nested role extraction, role-prefix changes, W3C trace context, OTLP configuration behavior, metrics, ProblemDetail contracts, trace correlation, JSON logging, and sensitive-data redaction.
- **FR-019**: The platform MUST remain limited to shared infrastructure. It MUST NOT introduce logistics business rules, domain models, service-specific endpoints, service-specific metrics, service-specific authorization policies, or service-owned data persistence.
- **FR-020**: Changes to security defaults, configuration names, error fields, trace propagation, logging fields/redaction, module responsibilities, or supported compatibility baselines MUST include explicit compatibility review and migration notes appropriate to the Semantic Versioning impact.

### Acceptance Criteria for Supported Web Stacks

The feature is accepted only when every row below passes for one MVC sample application and one WebFlux sample application, using the same public starter and equivalent configuration intent.

| Area | MVC acceptance criterion | WebFlux acceptance criterion |
|---|---|---|
| Startup and selection | Starts with MVC dependencies, creates only MVC-compatible web infrastructure, and does not require WebFlux. | Starts with WebFlux dependencies, creates only WebFlux-compatible web infrastructure, and does not require MVC. |
| Default security | Unauthenticated access to an unconfigured endpoint is denied; valid mock JWT access succeeds. | Unauthenticated access to an unconfigured endpoint is denied; valid mock JWT access succeeds. |
| Public endpoints | Common path-pattern configuration, plus default health/info paths when present, makes only matching paths accessible without a token; other paths remain protected. | The same common path-pattern configuration, plus default health/info paths when present, makes only matching paths accessible without a token; other paths remain protected. |
| JWT roles | A configured nested role claim is mapped with `ROLE_` by default and with a custom prefix when configured. | A configured nested role claim is mapped with `ROLE_` by default and with a custom prefix when configured. |
| Overrides | An application `SecurityFilterChain` disables only the MVC platform chain; an application decoder or authority converter is reused without disabling default denial. Other compatible capability overrides take precedence independently without duplicate infrastructure or disabling unrelated capabilities. | An application `SecurityWebFilterChain` disables only the WebFlux platform chain; an application reactive decoder or authority converter is reused without disabling default denial. Other compatible capability overrides take precedence independently without duplicate infrastructure or disabling unrelated capabilities. |
| Trace propagation | Valid `traceparent` is continued and inserted into outbound calls; invalid or missing context starts a new safe trace. | Valid `traceparent` is continued and inserted into outbound calls; invalid or missing context starts a new safe trace, including across reactive execution. |
| OTLP | Configured telemetry can be sent to an OTLP destination; absent/unavailable export does not fail requests. | Configured telemetry can be sent to an OTLP destination; absent/unavailable export does not fail requests. |
| Metrics | A custom Micrometer metric is recorded and available through the configured registry. | A custom Micrometer metric is recorded and available through the configured registry. |
| Errors | Authentication, authorization, and application errors return the common RFC 7807-compatible representation with a non-empty `traceId`. | Authentication, authorization, and application errors return the same externally visible representation with a non-empty `traceId`. |
| Logging and redaction | Request logs are structured JSON, include trace correlation when available, and contain no test JWT, authorization header, password, token, secret, or application-configured personal-data field. | Request logs are structured JSON, include trace correlation when available, and contain no test JWT, authorization header, password, token, secret, or application-configured personal-data field. |

### Quality Gates

- The BOM resolves one consistent platform dependency set for the supported baseline.
- Context and integration tests prove the selected web-stack conditions and application-bean back-off behavior.
- Public-endpoint tests prove the documented grammar, path-only matching, permit-only semantics, and startup failure for invalid patterns.
- Mock JWT security tests prove default denial, public endpoint configuration, issuer validation, nested role mapping, default/custom prefixes, complete-chain back-off, decoder/converter reuse, and application overrides.
- MVC and WebFlux integration tests prove equivalent external security, trace, metrics, error, logging, and redaction behavior.
- ProblemDetail contract tests prove required standard fields, `traceId`, problem media type, safe details, and stable behavior for authentication, authorization, and application errors.
- Trace-context tests prove inbound continuation, outbound `traceparent` propagation, invalid-context handling, and correlation between error responses and logs.
- Structured logging tests inspect serialized events and prove JSON shape, trace correlation, baseline redaction of every sensitive-data category named in this specification, and redaction of representative application-configured personal-data fields.

### Requirement-to-Test Coverage

The following verification groups provide an acceptance path for every functional requirement:

| Requirement group | Acceptance evidence |
|---|---|
| FR-001–FR-004 | Module and dependency inspection plus application-context tests prove exactly three module responsibilities, baseline compatibility, no forced web stack, selected-branch conditions, and bean back-off. |
| FR-005–FR-008 | MVC and WebFlux mock-JWT integration tests prove default denial, issuer validation, public endpoints, malformed/missing nested roles, default/custom prefixes, equivalent responses, and application-owned security overrides. |
| FR-009–FR-011 | Inbound/outbound trace tests, invalid-context tests, reactive context tests, OTLP configuration tests, and metrics-registry tests prove propagation, non-blocking export behavior, and Micrometer application metrics. |
| FR-012–FR-015 | MVC and WebFlux error-contract tests plus captured-log tests prove ProblemDetail fields/media type, `traceId`, safe error details, JSON structure, trace correlation, and redaction before serialization. |
| FR-016–FR-017 | Property-binding and application-bean override tests prove each required configuration namespace is effective and compatible security, tracing, metrics, error, and logging defaults back off independently. |
| FR-018 | The complete reusable-infrastructure quality suite is a release gate and must pass for every affected web stack. |
| FR-019–FR-020 | Scope review and compatibility review verify no business behavior is introduced and changes carry the required migration/Semantic Versioning assessment. |

### Key Entities

- **Platform Module Set**: The exactly three public platform modules and their fixed responsibilities: version alignment, implementation/conditional configuration, and thin adoption entry point.
- **Platform Configuration**: One root configuration model represented by `PlatformProperties`, containing five capability property groups under the required `security`, `tracing`, `metrics`, `errors`, and `logging` namespaces.
- **Authenticated Request Context**: The validated identity, authorities, trace identifiers, and request metadata available while a request is processed.
- **Security Authority Mapping**: The relationship between a configured nested JWT claims path, extracted role values, and the configured authority prefix.
- **Trace Context**: The W3C-compatible inbound and outbound correlation state represented by `traceparent` and local trace/span identifiers.
- **Metric**: An application counter, timer, or gauge recorded through the supported metrics API and exposed through the service's configured metrics registry.
- **ProblemDetail Error**: The common client-facing error representation containing standard RFC 7807 semantics and a correlation `traceId`.
- **Structured Log Event**: A JSON event containing standard diagnostic fields, optional trace correlation, and redacted values.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All ten HZ Logistics microservices can adopt the same public starter artifact and BOM, with each service retaining its existing choice of MVC or WebFlux and requiring no additional platform web-stack artifact.
- **SC-002**: In representative automated tests for both supported web stacks, 100% of unconfigured protected endpoint requests without valid authentication are rejected, and 100% of explicitly configured public endpoint requests are allowed.
- **SC-003**: In 100% of trace-context test cases with valid inbound `traceparent` values, the same request correlation can be followed through the service's outbound call, error response, and structured logs.
- **SC-004**: 100% of platform-generated and unhandled error responses sampled from both stacks that include a body contain a non-empty `traceId`, use the common problem representation, and omit stack traces and sensitive credentials.
- **SC-005**: 100% of structured log events produced by the redaction test corpus contain valid JSON and contain none of the corpus's JWTs, authorization headers, passwords, tokens, secrets, or unnecessary personal data in clear text.
- **SC-006**: A representative service can record and observe a custom counter, timer, and gauge through the common metrics capability in both stacks without direct use of a vendor-specific metrics API.
- **SC-007**: Adding or removing the nonselected web stack from a representative service changes no platform behavior for the selected stack and causes no startup failure attributable to missing or conflicting web infrastructure.
- **SC-008**: A service team can enable the baseline platform capabilities using documented configuration and the single public starter without writing platform-specific security, trace propagation, error serialization, or log-redaction code.
- **SC-009**: Operators and service developers rate the common error, trace, metric, and logging formats as usable for cross-service investigation in a review of all ten consuming services, with at least 90% confirming that the `traceId` is sufficient to correlate a returned error with diagnostic records.

## Assumptions

- The ten consuming services are Kotlin-based Spring Boot applications that run on Java 21 and will each choose exactly one web model for a given deployment.
- The service environment supplies a valid issuer and signing-key discovery path for JWT validation; the platform does not provision or manage Keycloak realms, clients, users, signing keys, or OTLP collectors.
- Keycloak compatibility means issuer-based JWT validation and configurable authority extraction; the platform will not assume `realm_access.roles`, `resource_access`, or another vendor-specific claims location.
- Health and info actuator endpoints are public by default when present, as permitted by the constitution; services can make them protected through configuration.
- Nested role mapping is opt-in through configuration. When no nested path is configured, the platform does not create vendor-specific role authorities, while authenticated access and application-defined authorization rules remain available.
- OTLP export is activated when an OTLP destination is configured. Trace context propagation and local correlation remain available without an exporter, and exporter outages are non-blocking for request handling.
- A service may provide compatible application-owned beans to customize or replace each platform capability independently; the corresponding default backs off while unrelated capabilities remain active. The platform is responsible for documented back-off behavior, not for reconciling arbitrary incompatible beans.
- The platform will use mock JWTs and controlled test collectors/registries for required automated verification. A real Keycloak or production observability deployment is not required for the initial acceptance suite.
- Log fields and error details are designed for operational correlation, not for carrying business payloads. Business-specific fields and additional personal-data redaction policies remain service-owned and configurable through the platform logging namespace.
- Publication to Maven, Nexus, or Artifactory and CI/CD automation are outside this feature unless separately specified; module correctness and reusable-infrastructure quality gates are in scope.

## Out of Scope

- Logistics domain models, workflows, business rules, persistence, or service-specific endpoints.
- Service-specific authorization policies beyond the shared authentication, public-endpoint, and configurable role-mapping mechanisms.
- Provisioning or administering Keycloak, an OTLP collector, a metrics backend, or a log aggregation platform.
- A fourth platform module, separate MVC/WebFlux starter artifacts, or forced adoption of either web stack.
- Direct use of the OpenTelemetry Metrics API by business code.
- Defining service-specific dashboards, alert thresholds, retention policies, or log schemas beyond the common fields and redaction contract.
