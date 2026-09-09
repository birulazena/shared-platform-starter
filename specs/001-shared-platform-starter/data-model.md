# Data Model: Shared Platform Starter

The feature has no persistent or logistics-domain data model. Its entities are immutable build metadata, bound configuration, request-scoped diagnostic/security values, and externally serialized contracts.

## 1. Platform Module Set

Represents the only three consumable platform artifacts.

| Field | Type | Rules |
|---|---|---|
| `bom` | Module coordinate | Exactly `logistics-parent-service-bom`; owns every dependency version and constraint. |
| `autoconfigure` | Module coordinate | Exactly `logistics-parent-service-autoconfigure`; owns all Kotlin implementation and conditional auto-configuration. |
| `starter` | Module coordinate | Exactly `logistics-parent-service-starter`; contains dependency declarations only. |
| `version` | Semantic version | One aligned release version for all three modules. |
| `springBootBaseline` | Version | Exactly `4.1.0` for this feature. |
| `javaTarget` | Integer | Exactly `21`. |

Validation invariants:

- The root Gradle project is an aggregator and must not be published or counted as a platform module.
- `starter -> autoconfigure` is allowed; `autoconfigure -> starter` and `bom -> implementation module` are forbidden.
- Neither implementation module may add `spring-boot-starter-web` or `spring-boot-starter-webflux` as a transitive dependency.
- Every declared platform dependency, including test, observability, and logging coordinates, resolves through the BOM.

## 2. Platform Configuration

`PlatformConfiguration` is the aggregate of five independently bound capability groups. It binds only the canonical `logistics.parent-service.*` root.

```text
PlatformConfiguration
├── SecurityProperties  logistics.parent-service.security.*
├── TracingProperties   logistics.parent-service.tracing.*
├── MetricsProperties   logistics.parent-service.metrics.*
├── ErrorProperties     logistics.parent-service.errors.*
└── LoggingProperties   logistics.parent-service.logging.*
```

### 2.1 SecurityProperties

| Field | Type | Default | Validation |
|---|---|---|---|
| `enabled` | Boolean | `true` | Independent security switch. Disabling it is explicit and observable at startup. |
| `issuer` | URI? | none | Required only while the platform default security chain is active. Must be an absolute `http` or `https` URI without user-info, query, or fragment. |
| `publicEndpoints` | List<String> | empty | Each entry must pass the common public PathPattern subset; duplicates are removed. |
| `publicActuatorEndpoints` | Boolean | `true` | Applies only to present and exposed health/info endpoints. |
| `roleClaimsPath` | String? | none | Dot-separated nonblank key segments; no empty segment; no expression syntax. |
| `rolePrefix` | String | `ROLE_` | Non-null, maximum 64 characters; an explicitly empty value is permitted. |

Conditional invariant: missing/invalid `issuer` fails startup only if `enabled=true`, the application is Servlet or Reactive, and no compatible application security chain has caused that branch to back off.

### 2.2 TracingProperties

| Field | Type | Default | Validation |
|---|---|---|---|
| `enabled` | Boolean | `true` | Disables the platform tracing contribution only; does not disable other capabilities. |
| `samplingProbability` | Decimal | `0.1` | Inclusive range `0.0..1.0`. Correlation IDs remain available for unsampled traces. |
| `otlp.endpoint` | URI? | none | Absolute `http`/`https` endpoint for HTTP/protobuf or valid absolute gRPC target for gRPC. Absence means no exporter. |
| `otlp.protocol` | Enum | `HTTP_PROTOBUF` | `HTTP_PROTOBUF` or `GRPC`. |
| `otlp.headers` | Map<String,String> | empty | Header names nonblank; values are treated as secrets and never logged. |
| `otlp.timeout` | Duration | `10s` | Positive and at most `60s`; exporter work remains off request threads. |
| `otlp.compression` | Enum | `GZIP` | `NONE` or `GZIP`. |

Invariant: W3C Trace Context is the platform propagation format. Export can be absent or fail without invalidating local propagation or request processing.

### 2.3 MetricsProperties

| Field | Type | Default | Validation |
|---|---|---|---|
| `enabled` | Boolean | `true` | Disables only the platform metrics contribution. |
| `commonTags` | Map<String,String> | empty | Keys and values nonblank; no credential or personal-data values. |

Invariant: application metrics use Micrometer `MeterRegistry`; the platform does not expose an OpenTelemetry `MeterProvider` as the application API and does not configure a metrics endpoint. The Spring Boot OpenTelemetry starter may make an OTLP registry available transitively, but backend selection remains application-owned.

### 2.4 ErrorProperties

| Field | Type | Default | Validation |
|---|---|---|---|
| `enabled` | Boolean | `true` | Controls only platform error handlers. |
| `detailPolicy` | Enum | `GENERIC` | `GENERIC` or `SAFE`; `SAFE` permits only explicitly classified safe validation/platform messages. |
| `includeInstance` | Boolean | `true` | When true, uses the request path without query/fragment. |

Invariants:

- `traceId` is mandatory and cannot be disabled.
- Stack traces, token material, passwords, secrets, and unnecessary personal data are never valid response detail.
- A response body uses `application/problem+json` and has the same external fields in MVC and WebFlux.

### 2.5 LoggingProperties

| Field | Type | Default | Validation |
|---|---|---|---|
| `enabled` | Boolean | `true` | Controls the platform logging contribution only. |
| `consoleEnabled` | Boolean | `true` | At least one application-owned sink may replace it. |
| `otelEnabled` | Boolean | `true` | Active only when a compatible `OpenTelemetry` instance/log provider is available. |
| `redactionMask` | String | `[REDACTED]` | Nonblank and must not include a source value. |
| `additionalSensitiveFields` | Set<String> | empty | Case-insensitive exact field/header/query-parameter names; nonblank. |
| `additionalSensitivePaths` | Set<String> | empty | Dot-separated structured paths; no wild expression evaluation. |

Invariants:

- Baseline categories—JWT/bearer authorization, password, token, and secret—always apply and cannot be removed.
- Redaction occurs before JSON serialization and before OpenTelemetry forwarding.
- Structured output includes timestamp, severity, logger/source, message, and trace/span identifiers when a trace exists.

## 3. Security Authority Mapping

Represents deterministic conversion of a validated JWT claim into Spring Security authorities.

| Field | Type | Rules |
|---|---|---|
| `claimsPath` | List<String> | Derived from configured dot-separated path; empty means no platform role extraction. |
| `sourceValue` | String or collection? | Accepted only when all emitted role values are strings. |
| `roles` | Ordered set of String | Trimmed, nonblank, duplicate-free; malformed source yields an empty set. |
| `prefix` | String | `ROLE_` by default; may be explicitly empty. |
| `authorities` | Ordered set of GrantedAuthority | Each authority is `prefix + role`; no authority is invented. |

Relationship: one validated authenticated request may have zero or one mapping evaluation and zero or more resulting authorities.

## 4. Authenticated Request Context

| Field | Type | Rules |
|---|---|---|
| `principal` | JWT subject/identity | Comes only from a successfully validated JWT. |
| `authorities` | Set | Includes standard authorities plus configured role mapping; application authorization remains service-owned. |
| `traceContext` | TraceContext | One current local context for diagnostics. |
| `requestPath` | Sanitized path | Excludes query data when used as a problem `instance`. |

This entity is request-scoped and is never persisted or placed wholesale into logs.

## 5. Trace Context

| Field | Type | Rules |
|---|---|---|
| `traceId` | 32 lowercase hexadecimal characters | Nonzero and W3C-valid. |
| `spanId` | 16 lowercase hexadecimal characters | Nonzero when a span exists. |
| `traceFlags` | Byte | Carries the W3C sampled flag. |
| `traceState` | String? | Preserved only if valid. |
| `remoteParent` | Boolean | True only for successfully extracted valid inbound context. |

State behavior:

- Valid inbound `traceparent` -> continue as remote parent and create a local server span.
- Missing or invalid inbound `traceparent` -> ignore it and start a safe new local trace.
- Outbound call from a managed builder -> inject current valid W3C context.
- No exporter/export failure -> context remains locally valid and request processing continues.

## 6. Metric

| Field | Type | Rules |
|---|---|---|
| `name` | String | Follows Micrometer naming conventions. |
| `kind` | Enum | Counter, timer, or gauge for acceptance coverage. |
| `tags` | Map<String,String> | Includes safe application/platform common tags; excludes secrets and unbounded sensitive values. |
| `registry` | MeterRegistry | Application-selected or test registry. |

Metrics are runtime observations, not persisted platform entities.

## 7. ProblemDetail Error

| Field | Type | Rules |
|---|---|---|
| `type` | URI | Stable problem category; defaults to `about:blank` only where appropriate. |
| `title` | String | Stable, non-sensitive summary. |
| `status` | Integer | Equals the HTTP response status. |
| `detail` | String | Generic/safe according to `detailPolicy`; never raw exception or credential content. |
| `instance` | URI? | Request path when enabled; excludes query and fragment. |
| `traceId` | String | Required, nonblank, and equal to the diagnostic correlation value for the failure. |

Authentication (`401`), authorization (`403`), validation/client (`4xx`), and unhandled (`500`) states share this shape when a body is returned.

## 8. Structured Log Event

| Field | Type | Rules |
|---|---|---|
| `timestamp` | Instant | Required. |
| `severity` | String | Required Logback level. |
| `logger` | String | Required logger/source. |
| `message` | String | Required and sanitized. |
| `traceId` | String? | Present when trace context exists. |
| `spanId` | String? | Present when span context exists. |
| `fields` | Map<String, sanitized value> | All key-value/MDC data after baseline and configured redaction. |
| `exception` | Sanitized throwable projection? | Cause/suppressed chain sanitized before serialization. |

Relationship: one raw in-process logging call becomes one sanitized event, which may fan out to console JSON and OpenTelemetry sinks. Raw sensitive values are not retained by either sink.

## 9. Capability Activation and Back-Off State

Each capability has an independent startup state:

```text
UNBOUND -> BOUND -> VALIDATED -> ACTIVE
                         |         |
                         |         +-> BACKED_OFF (compatible application owner exists)
                         +-> FAILED_VALIDATION (only when its default would activate)

BOUND -> DISABLED (capability enabled=false)
BOUND -> INACTIVE_NO_STACK (web-only branch and no matching web application type)
```

One capability's `BACKED_OFF`, `DISABLED`, or `FAILED_VALIDATION` decision must not change the activation decision for any unrelated capability. Startup diagnostics expose which state and condition caused each branch decision without printing secret property values.
