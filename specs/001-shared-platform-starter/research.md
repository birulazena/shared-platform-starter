# Phase 0 Research: Shared Platform Starter

This research resolves every technical unknown for the Spring Boot 4.1.0 platform baseline. The feature specification and constitution remain binding; no decision below removes or weakens a requirement.

## 1. Build and Compatibility Baseline

**Decision**: Pin the Gradle Wrapper to 9.3.0, compile Kotlin 2.3.21 with a Java 21 toolchain and JVM target, and pin Spring Boot to exactly 4.1.0. Import `org.springframework.boot:spring-boot-dependencies:4.1.0` into the platform BOM and do not use a dynamic or `latest` version.

**Rationale**: Spring Boot 4.1.0 supports Gradle 8.14+ and 9.x. Gradle 9.3.0 runs on Java 21 and is compatible with the Kotlin 2.3.21 line managed by Spring Boot 4.1.0. The Boot BOM resolves Spring Framework 7.0.8, Spring Security 7.1.0, Micrometer 1.17.0, Micrometer Tracing 1.7.0, OpenTelemetry 1.62.0, Kotlin 2.3.21, and Logback 1.5.34. Pinning all three baselines prevents an implicit Spring Boot upgrade.

**Alternatives considered**: Gradle 8.14.4 is supported and can run on Java 21, but its embedded Kotlin line differs from the application compiler line. A current/latest Gradle or Boot selector was rejected because it makes compatibility non-reproducible. Java 17 was rejected because the constitution requires Java 21.

**Sources**: [Spring Boot 4.1 Gradle plugin requirements](https://docs.spring.io/spring-boot/4.1/gradle-plugin/introduction.html), [Spring Boot 4.1.0 managed versions](https://docs.spring.io/spring-boot/4.1/appendix/dependency-versions/index.html), [Gradle Java/Kotlin compatibility](https://docs.gradle.org/current/userguide/compatibility.html).

## 2. Three-Module Build Shape and BOM Ownership

**Decision**: Use a non-published root aggregator that includes exactly `logistics-parent-service-bom`, `logistics-parent-service-autoconfigure`, and `logistics-parent-service-starter`. Implement the BOM with Gradle's `java-platform` plugin, import the Spring Boot 4.1.0 BOM, constrain both external dependencies and the two consumable platform artifacts, and make consumer validation use the platform for versionless dependency declarations.

**Rationale**: An aggregator is Gradle build infrastructure rather than a fourth platform artifact. A `java-platform` project is the native Gradle model for a BOM and can align Spring, security, observability, logging, Kotlin, and test coordinates. The starter stays thin and the auto-configuration module remains the single implementation location.

**Alternatives considered**: A fourth “core” module and separate MVC/WebFlux starters were rejected by FR-001. Duplicating version declarations in each module was rejected because the BOM must control the entire platform set. Publishing the root project was rejected because it would create another consumable artifact.

**Source**: [Gradle Java Platform Plugin](https://docs.gradle.org/current/userguide/java_platform_plugin.html).

## 3. Starter Dependency Coordinates Without a Forced Web Stack

**Decision**: The thin starter exposes the auto-configuration module and non-web prerequisites based on Spring Boot 4.1.0: OAuth2 resource-server/Jose support, Actuator, `spring-boot-starter-opentelemetry` for the complete Boot-managed Micrometer/OpenTelemetry tracing and OTLP graph, and the approved OpenTelemetry Logback appender because that appender is not part of Spring Boot. It must not depend on `spring-boot-starter-web` or `spring-boot-starter-webflux`. The auto-configuration module compiles against MVC, Servlet, WebFlux, and reactive security APIs as optional/compile-only dependencies and isolates their references by branch.

**Rationale**: Spring Security's resource-server and Jose libraries validate JWTs without choosing a server runtime. Spring Boot's dedicated OpenTelemetry starter already brings its Micrometer metrics/tracing integration, OpenTelemetry support, and OTLP exporters, so listing those modules separately duplicates the dependency graph and weakens Boot's auto-configuration ownership. The OTel Logback appender remains separate because Spring Boot explicitly leaves that integration to the application. Compile-only web APIs allow the shared jar to contain both implementations while leaving the consumer responsible for selecting its single web starter.

**Alternatives considered**: Making both web starters transitive would force conflicting application models. Making neither branch available in the auto-configuration jar would require separate published artifacts. Treating the selected web stack as a runtime-only platform dependency was rejected because it would make startup fail for consumers that did not declare it.

**Sources**: [Spring Boot 4.1.0 managed coordinates](https://docs.spring.io/spring-boot/4.1/appendix/dependency-versions/coordinates.html), [Spring Security resource-server JWT dependencies](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

## 4. Auto-Configuration Registration and Branch Conditions

**Decision**: Register auto-configuration candidates in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Put Servlet/MVC and Reactive/WebFlux security and error configuration in separate `@AutoConfiguration` classes guarded by both `@ConditionalOnClass` and `@ConditionalOnWebApplication(type = SERVLET|REACTIVE)`. Guard each default bean with a responsibility-specific `@ConditionalOnMissingBean` and each capability with its canonical `enabled` property.

**Rationale**: This is the supported Spring Boot 4 extension mechanism. Using both classpath and application-type conditions prevents accidental dual activation when both API sets are present. Bean-level missing conditions allow each capability to back off without suppressing unrelated defaults.

**Alternatives considered**: Component scanning was rejected because library auto-configuration should be explicitly registered and ordered. One auto-configuration class containing both branches was rejected because absent optional types can cause linkage and condition-order problems. A single global “platform enabled” switch was rejected because it breaks independent override.

**Source**: [Creating Spring Boot auto-configuration](https://docs.spring.io/spring-boot/4.1/reference/features/developing-auto-configuration.html).

## 5. Common Public Endpoint Pattern Contract

**Decision**: Define one cross-stack subset of Spring `PathPattern`: every configured pattern starts with `/`; literals are allowed; `?` and `*` match only within one segment; a terminal `/**` matches zero or more remaining segments. Reject query strings, fragments, relative paths, `..`, empty segments, URI variables, inline regex, and mid-pattern `**`. Parse once with `PathPatternParser` and match a segment-aware `RequestPath` in both branches. Patterns apply to all HTTP methods. Health and info use stack-specific Actuator endpoint matchers so a custom management base path remains correct.

**Rationale**: Both Spring MVC and WebFlux use parsed `PathPattern`, so a documented common subset yields equivalent encoded-path behavior and avoids deprecated `AntPathMatcher`. Restricting the grammar keeps public access reviewable. Because the configuration contains permits only, overlapping entries cannot broaden access beyond their union and list order has no effect.

**Alternatives considered**: Ant patterns were rejected because `AntPathMatcher` is deprecated and string decoding is less safe. Regular expressions were rejected as difficult to audit and inconsistent across request matcher APIs. Separate MVC and WebFlux syntaxes were rejected by the resolved requirement.

**Sources**: [Spring MVC PathPattern syntax](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html), [parsed path matching rationale](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/handlermapping-path.html).

## 6. Issuer-Based JWT Validation and Secure Startup

**Decision**: When platform security is enabled and no application security chain owns the selected branch, require a nonblank absolute HTTP(S) issuer URI without user-info, query, or fragment and create or reuse the stack-appropriate issuer decoder (`JwtDecoder` or `ReactiveJwtDecoder`). Issuer discovery and decoder defaults validate signature, expiry/not-before timing, issuer, and standard JWT validity. Run issuer validation only when the platform default chain is active, so a complete application-owned chain can back off without being forced to provide platform issuer properties. HTTPS is the deployment expectation outside controlled local test/development environments.

**Rationale**: Spring Security's issuer configuration discovers keys and validates `iss`, while Nimbus-backed decoders perform signature and timestamp validation. Conditional validation gives the required fail-secure startup without invalidating an intentional application override. Keycloak is compatible through its standards-based issuer metadata; no Keycloak-specific claim path is assumed.

**Alternatives considered**: A hardcoded JWK set URL or realm path was rejected as vendor-specific. Permitting startup without an issuer was rejected as insecure. Validating the issuer unconditionally at property-binding time was rejected because it would prevent supported application-owned security back-off.

**Sources**: [Servlet JWT issuer configuration](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [Reactive JWT issuer configuration](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html).

## 7. Nested Role Extraction

**Decision**: Use a dot-separated claim-key path such as `realm_access.roles`, with each segment restricted to a nonblank JSON object key token. No default claim path exists. Walk nested maps without expression evaluation; accept one nonblank string or a collection containing only strings; trim, discard blanks, de-duplicate in encounter order, and prefix each role with the configured prefix (`ROLE_` by default, an explicitly empty prefix permitted). Missing, null, mixed-type, or non-string values produce no mapped role authorities.

**Rationale**: A small deterministic traversal is portable between `JwtAuthenticationConverter` and `ReactiveJwtAuthenticationConverterAdapter`, supports Keycloak-shaped tokens without hardcoding them, and cannot execute SpEL supplied by configuration. Malformed claims never invent privilege.

**Alternatives considered**: Spring Security's `ExpressionJwtGrantedAuthoritiesConverter` can read nested claims but exposes a broader SpEL grammar than this public contract needs. Hardcoded `realm_access.roles` and `resource_access` were rejected. Falling back to guessed roles or coercing arbitrary values was rejected as privilege escalation risk.

**Source**: [Spring Security nested authority conversion](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html#oauth2resourceserver-jwt-authorization-extraction).

## 8. Independent Capability Back-Off

**Decision**: Give every capability a distinct back-off surface. MVC backs off on an application `SecurityFilterChain`; WebFlux on `SecurityWebFilterChain`. Application decoders/converters are reused independently. Tracing reuses application `OpenTelemetry`, `SdkTracerProvider`, or `ContextPropagators` components when supplied. Metrics uses application registries and backs off its platform policy customizer on an application `PlatformMetricsCustomizer`. Errors back off the shared factory or stack handler independently. Logging backs off its `PlatformLogSanitizer` bean and yields entirely to an application `logback-spring.xml`/`logging.config` resource when present. Tests must replace one capability at a time and assert the other four remain active.

**Rationale**: Responsibility-specific missing-bean conditions align with Spring Boot's composition model and satisfy the clarified independent override requirement. Reusing lower-level application beans allows customization without requiring a full capability replacement.

**Alternatives considered**: One marker bean disabling the entire platform was rejected. Backing off security merely because a decoder exists was rejected because an application may want the platform chain with its decoder. Attempting to merge arbitrary application handlers or logging configurations was rejected as unpredictable; only documented compatible surfaces are supported.

## 9. W3C Trace Propagation and Correlation

**Decision**: Configure Micrometer Tracing over OpenTelemetry with W3C propagation as the platform default for inbound server instrumentation and outbound clients created from Spring Boot's auto-configured `RestClient.Builder`, `RestTemplateBuilder`, or `WebClient.Builder`. Use the active span trace/span identifiers for MDC, errors, and logs. If no active trace exists while producing an error, generate one valid correlation identifier for that error/log pair without treating it as a remote parent.

**Rationale**: Spring Boot 4.1.0 directly supports the W3C propagation enum and automatically instruments its managed client builders. OpenTelemetry extraction ignores invalid carrier values rather than throwing; normal server instrumentation then starts a safe new trace. Propagation remains useful even when no exporter exists.

**Alternatives considered**: B3-only propagation was rejected by FR-009. Hand-written request filters and client interceptors were rejected because they duplicate framework instrumentation and are easy to lose across reactive boundaries. Accepting a malformed `traceparent` as correlation was rejected by the W3C validity requirement.

**Sources**: [Spring Boot trace propagation](https://docs.spring.io/spring-boot/4.1/reference/actuator/tracing.html), [OpenTelemetry propagator requirements](https://opentelemetry.io/docs/specs/otel/context/api-propagators/), [Spring Boot 4 W3C propagation enum](https://docs.spring.io/spring-boot/4.1/api/java/org/springframework/boot/micrometer/tracing/autoconfigure/TracingProperties.Propagation.PropagationType.html).

## 10. OTLP Activation and Failure Behavior

**Decision**: Use `spring-boot-starter-opentelemetry` for tracing and OTLP auto-configuration. Keep tracing and local propagation enabled independently of export: no configured `logistics.parent-service.tracing.otlp.endpoint` means no trace exporter is activated, while a configured endpoint maps to Spring Boot's `management.opentelemetry.tracing.export.otlp.*` model or to the supported OTLP exporter builder customizers. Protocol, headers, timeout, compression, sampling, asynchronous export, and diagnostic-only exporter failure behavior remain part of the platform's canonical property contract. Tests cover no endpoint, a recording local HTTP collector, a rejecting/closed collector, and application-provided exporter customization.

**Rationale**: Spring Boot 4.1.0 provides a dedicated OpenTelemetry starter, the Micrometer bridge, OTLP exporters, and builder customizers. Reusing that auto-configuration avoids a parallel SDK and ensures the platform only adapts its canonical namespace while retaining the requirement that collector absence or outage must not break business requests.

**Alternatives considered**: Always exporting to localhost was rejected because no endpoint must be a valid local-only mode. Synchronous export on the request thread was rejected as a reliability and latency risk. Using the OpenTelemetry Java agent was rejected because this feature is a reusable starter library.

**Sources**: [Spring Boot OpenTelemetry tracing with OTLP](https://docs.spring.io/spring-boot/4.1/reference/actuator/tracing.html), [Spring Boot OpenTelemetry support](https://docs.spring.io/spring-boot/4.1/reference/actuator/observability.html).

## 11. Micrometer Metrics

**Decision**: Expose Spring Boot's Micrometer `MeterRegistry` model to applications through the metrics infrastructure included by `spring-boot-starter-opentelemetry`, and let consumers choose how a registry is used. Platform metrics properties control enablement and common tags. The Boot starter may provide OTLP registry support transitively, but the platform does not configure a metrics endpoint or require it. Test counters, timers, and gauges with `SimpleMeterRegistry`; do not expose or require the OpenTelemetry Metrics API.

**Rationale**: Micrometer is Spring Boot's supported application metrics facade, and Boot creates registry infrastructure from available implementations. Keeping metrics usage behind Micrometer keeps business code backend-neutral and respects the constitution; the presence of optional Boot-managed OTLP registry support does not select a collector or backend for a consuming service.

**Alternatives considered**: Direct `MeterProvider` use was rejected by FR-011. Configuring an OTLP metrics endpoint or forcing Prometheus export was rejected because backend provisioning and selection are service-owned, even though the Boot OpenTelemetry starter may include OTLP registry support transitively. A custom metrics abstraction was rejected as redundant.

**Source**: [Spring Boot Micrometer metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html).

## 12. ProblemDetail Across MVC, WebFlux, and Security

**Decision**: Define one shared `PlatformProblemDetailFactory` that creates Spring `ProblemDetail` objects with `type`, `title`, `status`, safe `detail`, request `instance`, and nonempty `traceId`. MVC uses a high-precedence `ResponseEntityExceptionHandler`-based advice plus JSON-writing authentication/authorization handlers. WebFlux uses the equivalent controller advice/WebExceptionHandler path plus reactive security entry/access-denied handlers. Every body sets `application/problem+json`; authentication, authorization, binding, known platform, and unhandled errors share the same field and redaction policy.

**Rationale**: Spring Framework natively serializes `ProblemDetail`, chooses problem media types, and supports non-standard top-level properties such as `traceId`. A shared factory keeps the externally visible contract identical while allowing stack-specific plumbing. RFC 9457 now supersedes RFC 7807, and Spring's implementation preserves the RFC 7807 field/media-type model required here.

**Alternatives considered**: Relying only on `/error` was rejected because Spring Security failures occur before controllers and MVC/WebFlux defaults are not guaranteed identical. A custom DTO was rejected because Spring already supplies the standard type. Exposing exception messages or stack traces was rejected as sensitive.

**Sources**: [Spring Framework MVC error responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html), [Spring WebFlux exception handling](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-exceptions.html), [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457).

## 13. Structured JSON Logging and Pre-Sink Redaction

**Decision**: Supply a default `logback-spring.xml` that uses Spring Boot's Logstash structured JSON encoder contract and routes every event through a platform redacting fan-out appender before console JSON encoding or OpenTelemetry forwarding. The sanitizer processes formatted messages, argument values, MDC/key-value fields, headers/query data included as structured fields, and the complete throwable cause/suppressed chain. It always masks JWT/bearer authorization, password, token, and secret categories and additionally masks configured exact field/path names. Trace and span IDs remain when present.

**Rationale**: Spring Boot 4 provides native JSON formats and a Logback encoder, while the default formats include MDC data. A single pre-sink fan-out boundary is necessary: serializer-only filtering would still expose raw events to the OpenTelemetry appender, and console-only filtering would leave other sinks unsafe. Baseline rules cannot be disabled by application configuration.

**Alternatives considered**: Post-serialization regular-expression replacement was rejected because a second sink could receive raw data and nested structures are easy to miss. A Logback filter that only accepts/denies events was rejected because it cannot safely transform every field. Logging request bodies by default was rejected because it expands sensitive-data exposure.

**Source**: [Spring Boot structured logging and Logback encoder](https://docs.spring.io/spring-boot/reference/features/logging.html).

## 14. Approved OpenTelemetry Logback Appender Compatibility

**Decision**: Pin `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.28.0-alpha` explicitly in the platform BOM and install its `OpenTelemetryAppender` with the application `OpenTelemetry` instance after startup. Place it behind the redaction boundary. Add a dependency-resolution test proving OpenTelemetry API 1.62.0, Logback 1.5.34, and appender 2.28.0-alpha; add a runtime test that initializes Logback, emits a correlated redacted event, and verifies the controlled OpenTelemetry log sink receives only sanitized fields.

**Rationale**: OpenTelemetry Java Instrumentation 2.28.0 targets OpenTelemetry SDK 1.62.0, exactly the version managed by Spring Boot 4.1.0. The appender supports Logback 1.0 and later, which includes Boot's 1.5.34. Spring Boot documents that third-party OTel appenders require `logback-spring.xml` configuration and programmatic access to the `OpenTelemetry` bean. The artifact is still alpha, so an explicit pin and runtime compatibility gate are mandatory.

**Alternatives considered**: Replacing the constitution-approved artifact was rejected. Allowing its latest alpha version was rejected because alpha APIs can break. Attaching it beside the redacting appender was rejected because raw events could escape. Treating coordinate alignment alone as sufficient was rejected because initialization and event-shape compatibility are runtime concerns.

**Sources**: [OpenTelemetry Java Instrumentation 2.28.0 release](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/tag/v2.28.0), [OpenTelemetry Logback appender guide](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/v2.28.0/instrumentation/logback/logback-appender-1.0/library/README.md), [Spring Boot OpenTelemetry logging integration](https://docs.spring.io/spring-boot/4.1/reference/actuator/loggers.html), [Spring Boot 4.1.0 BOM](https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-dependencies/4.1.0).

## 15. Verification Strategy

**Decision**: Keep all fixtures inside the auto-configuration module and create four suites: fast unit/context tests, MVC integration tests, WebFlux integration tests, and logging corpus tests. Use mock JWTs for signature/expiry/issuer/role behavior where possible, explicit decoder unit tests for issuer validators, controlled OTLP collectors/exporters, test registries, valid/invalid W3C headers, captured problem responses, and captured JSON/OTel log events. Add one test per capability replacement to prove independent back-off.

**Rationale**: These tools test the shared infrastructure directly without introducing a real identity provider, telemetry backend, business service, or fourth module. Stack-specific suites provide equivalent acceptance evidence and make failures attributable.

**Alternatives considered**: Testcontainers Keycloak was rejected for the initial suite because mock JWTs and decoder tests cover the specified behavior. Production collectors and registries were rejected as nondeterministic. Unit tests alone were rejected because the constitution requires reusable-infrastructure and both-stack integration coverage.

## 16. Compatibility and Release Policy

**Decision**: Treat configuration key/default changes, public pattern grammar, security behavior, `ProblemDetail` fields, trace propagation, log field/redaction behavior, module dependency direction, and supported baseline changes as compatibility surfaces. Each such change requires an explicit review, Semantic Versioning classification, migration notes, and both-stack regression tests.

**Rationale**: These surfaces affect all ten services and are explicitly governed by FR-020 and the constitution. Recording them in contracts makes future review concrete.

**Alternatives considered**: Treating platform defaults as internal implementation details was rejected because consumers observe and rely on them. Silent baseline upgrades were rejected.

## Resolution Summary

All Phase 0 unknowns are resolved, and no requirement or constitutional gate requires an exception.
