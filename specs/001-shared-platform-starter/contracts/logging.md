# External Contract: Structured Logging and Redaction

## Logback Configuration

The platform auto-configuration jar supplies a default `logback-spring.xml`. It uses Spring Boot's Logstash structured JSON encoder and a platform redacting fan-out appender. A consuming application may provide its own `logback-spring.xml` or `logging.config`; that application resource wins and is responsible for preserving the compatible logging contract.

The platform default has this event flow:

```text
Logback logging call
        |
        v
baseline + application redaction
        |
        v
sanitized immutable event
        +----------------------+
        |                      |
        v                      v
structured console JSON   OpenTelemetryAppender
```

No default sink receives the raw event.

## JSON Event Contract

Every default console event is one valid JSON object with these fields:

| JSON field | Presence | Meaning |
|---|---|---|
| `@timestamp` | Required | ISO-8601 event timestamp. |
| `level` | Required | Logback severity name. |
| `logger_name` | Required | Logger/source name. |
| `message` | Required | Formatted, sanitized message. |
| `thread_name` | Required | Emitting thread or framework execution name. |
| `traceId` | When a valid trace exists | Current 32-hex trace identifier. |
| `spanId` | When a valid span exists | Current 16-hex span identifier. |
| additional structured fields | Optional | Sanitized MDC/SLF4J key-value fields. |
| exception projection | When logged | Sanitized exception type/message/stack representation according to encoder policy. |

One logical event must not be split into multiple invalid JSON records. Field-name or default-format changes are compatibility changes.

## Mandatory Baseline Redaction

The baseline is always enabled and cannot be removed by application properties or a custom sanitizer. Matching is case-insensitive for field/header names and separator-normalized (`-`, `_`, and `.`):

- `Authorization` and proxy authorization values, especially bearer credentials;
- compact JWT values, including when embedded in free text;
- `password`, `passwd`, and `pwd` categories;
- access, refresh, ID, API, and generic `token` categories;
- `secret`, `client-secret`, and API-key credential categories.

The value is replaced with `logistics.parent-service.logging.redaction-mask` (default `[REDACTED]`). The mask must not preserve a reversible prefix/suffix or token claims.

## Redaction Locations

Before fan-out, inspect and sanitize:

- formatted message and argument array;
- SLF4J fluent key-value pairs and markers where captured;
- MDC values and nested structured field maps/lists;
- explicitly logged request headers and query parameters;
- exception message, cause chain, suppressed exceptions, and safe stack projection;
- application-configured exact field names and structured paths.

Request/response bodies, raw JWT claims, and all headers are not logged by default. If application code explicitly adds such values, the sanitizer still applies to recognized keys/content.

## Application Personal-Data Rules

`additional-sensitive-fields` matches exact keys, header names, and query-parameter names case-insensitively at any structured level. `additional-sensitive-paths` matches an exact dot-separated structured path. Rules are literal data selectors, not regex or executable expressions.

Examples:

- field `customerEmail` masks every structured `customerEmail` key;
- path `shipment.recipient.phone` masks that nested path without automatically masking an unrelated `warehouse.phone` field.

Baseline rules are unioned with application rules. An invalid rule fails logging configuration rather than being silently ignored.

## Trace Correlation

When Micrometer/OpenTelemetry trace context exists, the sanitized event contains its `traceId` and `spanId`. If an error response is produced, its `traceId` equals the event's `traceId`. Reactive tests must assert correlation after a scheduler/context boundary, not only on the request thread.

## OpenTelemetry Logback Integration

- `spring-boot-starter-opentelemetry` supplies the platform's Boot-managed tracing/OTLP runtime, but it does not include an OpenTelemetry Logback appender.
- Coordinate: `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.28.0-alpha`.
- The BOM pins this exact version and aligns its OpenTelemetry API dependency to Boot-managed 1.62.0.
- The appender is initialized programmatically with the application `OpenTelemetry` instance after the context is ready.
- The appender is downstream of the redaction boundary and cannot receive raw events.
- If no compatible OTel log provider/exporter exists, console JSON continues and logging does not fail requests.
- Alpha-version compatibility is a mandatory dependency-resolution and runtime test gate.

## Back-Off and Customization

- An application `PlatformLogSanitizer` replaces the configurable sanitization policy while the platform wrapper still enforces immutable baseline rules.
- An application logging resource replaces the default resource; this affects logging only.
- A custom compatible resource must produce structured JSON, retain correlation, apply baseline redaction before every sink, and initialize any OTel appender safely.
- Logging back-off must not disable security, tracing, metrics, or problem handling.

## Redaction Corpus Verification

The synthetic corpus includes unique canary values in:

- Authorization/bearer headers with varied casing;
- compact JWTs in messages, fields, exceptions, causes, and suppressed exceptions;
- password, access token, refresh token, ID token, generic token, client secret, and API key fields;
- query parameters and nested map/list structures;
- configured `customerEmail` and `shipment.recipient.phone` fields/paths;
- strings containing near-matches that should remain usable where safe.

For each corpus item, capture console JSON and controlled OpenTelemetry log records, parse the JSON, and assert:

1. the raw canary is absent from every serialized/exported byte and field;
2. the configured mask is present where a value was expected;
3. the event remains valid JSON with required fields;
4. trace correlation remains intact;
5. nested causes/suppressed data cannot recover the canary.

## Compatibility

JSON field names, default format, correlation semantics, baseline sensitive categories, redaction timing, mask semantics, application selectors, appender coordinate, or logging back-off behavior require explicit compatibility review, Semantic Versioning classification, migration notes, and corpus/runtime regression tests.

## Release regression evidence and migration note

`LoggingRedactionTest` and `LoggingRuntimeCompatibilityTest` prove that the
same sanitized event reaches console and OpenTelemetry sinks, including nested
throwables and the configured sensitive corpus. `CapabilityBackOffTest` covers
logging-only override behavior. A custom sanitizer or logging resource is a
compatible migration only when baseline redaction and correlation remain
intact.
