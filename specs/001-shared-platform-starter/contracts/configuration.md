# External Contract: Platform Configuration

## Namespace

Only the following canonical namespaces are supported:

- `logistics.parent-service.security.*`
- `logistics.parent-service.tracing.*`
- `logistics.parent-service.metrics.*`
- `logistics.parent-service.errors.*`
- `logistics.parent-service.logging.*`

No alternate root alias is supported or bound. Unknown or deprecated roots are not silently translated.

## Reference Configuration

```yaml
logistics:
  parent-service:
    security:
      enabled: true
      issuer: https://identity.example.com/realms/logistics
      public-actuator-endpoints: true
      public-endpoints:
        - /public/**
        - /docs/*.json
      role-claims-path: realm_access.roles
      role-prefix: ROLE_
    tracing:
      enabled: true
      sampling-probability: 0.1
      otlp:
        endpoint: https://otel-collector.example.com:4318/v1/traces
        protocol: HTTP_PROTOBUF
        timeout: 10s
        compression: GZIP
        headers:
          Authorization: ${OTLP_AUTHORIZATION}
    metrics:
      enabled: true
      common-tags:
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
```

The sample uses only the canonical contract. It does not imply that a collector, identity provider, metrics backend, or application endpoints are provisioned by the platform. The platform adapts the canonical tracing properties to Spring Boot's OpenTelemetry tracing/export configuration; consumers do not need to configure Boot's `management.opentelemetry.*` namespace directly.

## Property Contract

| Property | Type | Default | Required/behavior |
|---|---|---|---|
| `logistics.parent-service.security.enabled` | Boolean | `true` | Controls only platform security defaults. |
| `logistics.parent-service.security.issuer` | URI | none | Required if the platform security chain will activate. Absolute HTTP(S), no user-info/query/fragment. |
| `logistics.parent-service.security.public-actuator-endpoints` | Boolean | `true` | Permits present/exposed health and info endpoints only. |
| `logistics.parent-service.security.public-endpoints` | List<String> | `[]` | Permit-only path patterns using the common grammar in [security.md](./security.md). |
| `logistics.parent-service.security.role-claims-path` | String | none | Optional dot-separated nested claim keys; no vendor default. |
| `logistics.parent-service.security.role-prefix` | String | `ROLE_` | Empty is allowed explicitly; otherwise applied verbatim. |
| `logistics.parent-service.tracing.enabled` | Boolean | `true` | Controls only the platform tracing contribution. |
| `logistics.parent-service.tracing.sampling-probability` | Decimal | `0.1` | Inclusive `0.0..1.0`. |
| `logistics.parent-service.tracing.otlp.endpoint` | URI/target | none | Absence disables export, not local W3C propagation. |
| `logistics.parent-service.tracing.otlp.protocol` | Enum | `HTTP_PROTOBUF` | `HTTP_PROTOBUF` or `GRPC`. |
| `logistics.parent-service.tracing.otlp.headers` | Map | `{}` | Export headers; values are always sensitive. |
| `logistics.parent-service.tracing.otlp.timeout` | Duration | `10s` | Positive, maximum `60s`. |
| `logistics.parent-service.tracing.otlp.compression` | Enum | `GZIP` | `NONE` or `GZIP`. |
| `logistics.parent-service.metrics.enabled` | Boolean | `true` | Controls only the platform metrics contribution. |
| `logistics.parent-service.metrics.common-tags` | Map | `{}` | Safe, bounded common tags. The consumer owns registry/backend selection; the platform does not configure a metrics endpoint. |
| `logistics.parent-service.errors.enabled` | Boolean | `true` | Controls only platform error handlers. |
| `logistics.parent-service.errors.detail-policy` | Enum | `GENERIC` | `GENERIC` or `SAFE`; neither permits raw stack/secret data. |
| `logistics.parent-service.errors.include-instance` | Boolean | `true` | Adds the request path, without query/fragment. |
| `logistics.parent-service.logging.enabled` | Boolean | `true` | Controls only platform logging integration. |
| `logistics.parent-service.logging.console-enabled` | Boolean | `true` | Enables the default structured console sink. |
| `logistics.parent-service.logging.otel-enabled` | Boolean | `true` | Forwards sanitized events when an OTel log pipeline is present. |
| `logistics.parent-service.logging.redaction-mask` | String | `[REDACTED]` | Nonblank replacement text. |
| `logistics.parent-service.logging.additional-sensitive-fields` | Set<String> | `[]` | Case-insensitive exact keys/header/query names. |
| `logistics.parent-service.logging.additional-sensitive-paths` | Set<String> | `[]` | Dot-separated exact structured field paths. |

## Validation and Failure Contract

- Property binding errors identify the canonical property name and expected shape but never include secret values.
- A malformed public endpoint pattern fails startup while platform security is active; it is never ignored as “no match.”
- Missing or invalid issuer configuration fails startup only when the platform default security chain would otherwise activate.
- Invalid sampling probability, OTLP endpoint/protocol/timeout, or redaction configuration fails its capability startup with a clear validation error.
- OTLP connection/export failures after successful configuration are runtime diagnostics, not application startup or request failures.
- Disabling or overriding one capability does not alter the other four.

## Configuration Metadata

The auto-configuration module must generate Spring configuration metadata for every property above, including type, default, description, and deprecation metadata. Metadata may advertise only the canonical root.

## Compatibility

A rename, removal, type change, default change, accepted-value change, or semantic change to any property is a compatibility change requiring explicit review, a Semantic Versioning decision, migration notes, and regression tests for affected web stacks.

## Release regression evidence and migration note

The property contract is covered by `PlatformPropertiesBindingTest`,
`ConfigurationMetadataTest`, `SecurityAutoConfigurationContextTest`, and
`OtlpConfigurationTest`, with MVC/WebFlux acceptance coverage where the
property changes affect a selected web branch. The initial `0.1.0` migration is
additive: configure only `logistics.parent-service.*`; no alternate root or
deprecated alias is supported.
