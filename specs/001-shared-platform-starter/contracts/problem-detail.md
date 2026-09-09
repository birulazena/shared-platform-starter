# External Contract: ProblemDetail Errors

## Media Type and Shape

When an error response has a body, the platform returns `Content-Type: application/problem+json` and a Spring `ProblemDetail` representation compatible with the RFC 7807 field model (and its RFC 9457 successor):

```json
{
  "type": "urn:hz-logistics:problem:unauthorized",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Authentication is required or the access token is invalid.",
  "instance": "/api/shipments",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

## Field Contract

| Field | Presence | Rules |
|---|---|---|
| `type` | Required | Stable absolute URI/URN identifying a platform problem category. |
| `title` | Required | Stable, non-sensitive human-readable summary. |
| `status` | Required | Integer equal to the HTTP response status. |
| `detail` | Required | Generic or explicitly classified safe text; never raw exception/credential content. |
| `instance` | Conditional | Request path when enabled and available; no query or fragment. |
| `traceId` | Required | Nonblank valid correlation value. When a trace exists, it is that trace ID. |

Unknown implementation-specific fields must not be added by one web stack only. Future extensions require compatibility review.

## Baseline Problem Categories

| Category | Type | Status | Safe default detail |
|---|---|---:|---|
| Authentication failure | `urn:hz-logistics:problem:unauthorized` | 401 | `Authentication is required or the access token is invalid.` |
| Authorization failure | `urn:hz-logistics:problem:forbidden` | 403 | `Access to this resource is denied.` |
| Invalid request/validation | `urn:hz-logistics:problem:invalid-request` | applicable 4xx | Generic invalid-request text; field-level messages only when classified safe. |
| Unhandled application failure | `urn:hz-logistics:problem:internal-error` | 500 | `An unexpected error occurred.` |

The platform may preserve a safe status/title/type from a Spring `ErrorResponse`, but it must still sanitize detail, set `traceId`, and preserve the media type.

## Detail Policy

- `GENERIC` (default): emit only category-level safe default details.
- `SAFE`: permit validation or platform messages from an explicit safe classification; arbitrary exception messages are not safe by default.
- Neither mode emits stack traces, exception class names, JWT claims, bearer tokens, passwords, secrets, request bodies, OTLP headers, or unnecessary personal data.
- Before serialization, all selected details pass the same baseline sensitive-value sanitizer used by logging.

## Trace Correlation

- If a current trace exists, `traceId` equals its trace ID and the diagnostic log for the same failure carries that value.
- If failure occurs before normal tracing is established, the error path creates one valid correlation ID and uses it for both response and diagnostic event when possible.
- An invalid inbound `traceparent` is not echoed as `traceId`.

## Stack-Specific Integration

MVC and WebFlux may use different framework adapters, but the serialized contract must be equal for equivalent failures.

- MVC controller and framework exceptions route through the platform MVC advice/resolver.
- WebFlux controller and framework exceptions route through the platform reactive advice/error handler.
- Servlet authentication entry point and access-denied handler write the shared problem representation.
- Reactive authentication entry point and access-denied handler write the same representation non-blockingly.
- Application-provided compatible factory/handler triggers only error back-off.

## Content Negotiation and Committed Responses

- `application/problem+json` is preferred for every platform body even when the request did not explicitly request JSON.
- If the client requests an unsupported representation, return the safe problem representation when writable; otherwise return the correct status with no body rather than expose an alternate unsafe format.
- If a response is already committed, do not attempt a second body. Emit a sanitized correlated diagnostic event.
- Required protocol headers such as `WWW-Authenticate` are retained independently of the body.

## Verification Contract

For MVC and WebFlux, assert exact external behavior for:

- missing/invalid authentication (`401`);
- authenticated but forbidden access (`403`);
- representative binding/validation error (`4xx`);
- thrown unhandled exception (`500`);
- error before normal request handling establishes a span;
- unsupported `Accept` header and already-committed response handling where testable;
- application problem factory/handler override and unrelated capability survival.

Every response-body assertion checks problem media type, required standard fields, nonempty `traceId`, status equality, safe `instance`, and absence of the complete redaction corpus.

## Compatibility

Problem type URIs, required fields, default details, media type, trace correlation semantics, status mapping, and back-off behavior are compatibility surfaces. Changes require explicit review, Semantic Versioning classification, migration notes, and equivalent MVC/WebFlux contract tests.

## Release regression evidence and migration note

The shared factory is covered by `PlatformProblemDetailFactoryTest`; external
behavior is covered by `MvcProblemDetailIntegrationTest` and
`WebFluxProblemDetailIntegrationTest`, including safe details, committed
responses, unsupported `Accept`, correlation, and override back-off. Consumers
can adopt the initial contract without a migration; clients must treat
`type`, `status`, `detail`, media type, and nonempty `traceId` as the stable
surface.
