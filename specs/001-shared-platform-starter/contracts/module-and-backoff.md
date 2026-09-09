# External Contract: Modules, Dependencies, and Back-Off

## Consumable Artifacts

| Artifact | Public responsibility | Forbidden responsibility |
|---|---|---|
| `logistics-parent-service-bom` | Align versions for both platform modules and every external platform dependency, including observability, logging, and tests. | Kotlin implementation, auto-configuration, runtime behavior. |
| `logistics-parent-service-autoconfigure` | Contain all properties, SPI types, shared infrastructure implementation, conditional branches, registration metadata, and default logging resource. | Selecting a consumer web server or duplicating starter/BOM responsibilities. |
| `logistics-parent-service-starter` | Provide one thin dependency entry point to the auto-configuration and non-web prerequisites. | Implementation classes, separate MVC/WebFlux variants, forced web dependencies. |

The root Gradle project is an aggregator only. It is not published and is not a fourth platform module.

## Consumer Dependency Contract

A consumer uses the BOM and single starter, then selects its own web stack:

```kotlin
dependencies {
    implementation(enforcedPlatform("com.hz.logistics:logistics-parent-service-bom:<platform-version>"))
    implementation("com.hz.logistics:logistics-parent-service-starter")

    // Consumer selects exactly one for its deployment:
    implementation("org.springframework.boot:spring-boot-starter-web")
    // or implementation("org.springframework.boot:spring-boot-starter-webflux")
}
```

The starter must not resolve either web starter transitively. A non-web application may adopt the starter; non-web capabilities may activate, but no web security or error branch may be created.

## BOM Contract

The BOM must:

- import and pin `org.springframework.boot:spring-boot-dependencies:4.1.0`;
- align both consumable platform artifacts to the platform release version;
- manage `org.springframework.boot:spring-boot-starter-opentelemetry` through the imported Spring Boot BOM as the single Micrometer/OpenTelemetry tracing and OTLP dependency;
- explicitly constrain `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.28.0-alpha`;
- govern Kotlin, Spring Framework, Spring Security, the Boot observability starter, Logback, the approved external Logback appender, test libraries, and any additional implementation dependency;
- allow all platform module dependency declarations and supported consumer examples to omit individual dependency versions;
- fail dependency verification if Spring Boot resolves to a version other than `4.1.0`.

## Auto-Configuration Selection

| Application state | MVC branch | WebFlux branch | Non-web defaults |
|---|---|---|---|
| Servlet application with MVC classes | Eligible | Inactive | Eligible |
| Reactive application with WebFlux classes | Inactive | Eligible | Eligible |
| Both API classpaths, application type SERVLET | Eligible | Inactive | Eligible |
| Both API classpaths, application type REACTIVE | Inactive | Eligible | Eligible |
| No web application | Inactive | Inactive | Eligible |

Each branch must require both its API class and selected `WebApplicationType`. Merely having both libraries on the classpath must never create both branches.

## Independent Back-Off Contract

| Capability | Default contribution | Compatible application owner/back-off trigger | Scope of back-off |
|---|---|---|---|
| MVC security | Platform `SecurityFilterChain`, entry point, denied handler, converter/decoder defaults | Application `SecurityFilterChain`; application `JwtDecoder`/converter is reused without disabling the platform chain | MVC security only |
| WebFlux security | Platform `SecurityWebFilterChain`, reactive entry/denied handler, converter/decoder defaults | Application `SecurityWebFilterChain`; application `ReactiveJwtDecoder`/converter is reused without disabling the platform chain | WebFlux security only |
| Tracing | Spring Boot OTel starter, W3C propagation, Micrometer/OpenTelemetry bridge, optional OTLP customization | Compatible application `OpenTelemetry`, `SdkTracerProvider`, `ContextPropagators`, or documented exporter customizer | Tracing/export only |
| Metrics | Platform Micrometer policy/common-tag customizer and fallback registry behavior | Application registry plus an application `PlatformMetricsCustomizer` for policy replacement | Metrics only |
| Errors | Shared `PlatformProblemDetailFactory` and selected stack handler | Application factory or selected-stack compatible error handler | Errors only |
| Logging | Default `logback-spring.xml`, `PlatformLogSanitizer`, redacting fan-out, OTel installer | Application logging resource/config; application `PlatformLogSanitizer` replaces sanitizer policy but must preserve baseline categories | Logging only |

Back-off is observable through Spring's condition evaluation report and tests. Diagnostic messages name types/conditions, not secret configuration values.

## Override Compatibility Rules

- A security override owns authorization policy and must retain any organizational requirements applicable to that service; the platform does not merge arbitrary chains.
- A tracing override is compatible only if W3C `traceparent` remains supported for this platform contract.
- A metrics override continues to expose Micrometer APIs to application code.
- An error override is compatible only if response bodies preserve the problem contract and nonempty `traceId`.
- A logging override is compatible only if structured JSON, correlation, and mandatory baseline redaction remain true.
- The platform verifies back-off mechanics, not arbitrary application override correctness.

## Registration and Ordering

- Auto-configuration candidates are listed in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Optional web types are isolated in separately conditioned classes so an absent stack cannot cause linkage failure.
- Shared property and utility configuration is ordered before stack branches.
- Platform configuration must be ordered to customize Spring Boot infrastructure without creating a competing duplicate observability or logging SDK.

## Compatibility

Changing module responsibilities, dependency direction, back-off triggers, supported bean types, selected-stack behavior, or the Spring Boot/Java baseline requires explicit compatibility review, Semantic Versioning classification, migration notes, and dependency/context regression tests.

## Release regression evidence and migration note

`BomAlignmentTest`, `StarterDependencyContractTest`,
`AutoConfigurationSelectionTest`, `CapabilityBackOffTest`, the starter
runtime dependency report, and the full `check` gate verify the three-module
boundary and independent back-off. Consumer migration is one enforced BOM plus
one starter; web applications select MVC or WebFlux themselves, and no fourth
platform module or alternate starter is supported.
