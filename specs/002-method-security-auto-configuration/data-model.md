# Data Model: Automatic Method Security Configuration

This feature adds no persistent or business-domain data model. Its relevant model is the runtime eligibility and authority state already owned by Spring Boot and Spring Security.

## Method-security eligibility

| Field | Source | Validation / rule |
|---|---|---|
| `securityEnabled` | `PlatformProperties.security.enabled` / `SecurityProperties.enabled` | Defaults to `true`; `false` prevents both platform web and method-security contributions. |
| `webApplicationType` | Spring Boot application context | Exactly `SERVLET` selects MVC, exactly `REACTIVE` selects WebFlux; a non-web context selects neither. |
| `requiredClassesPresent` | `@ConditionalOnClass` | The branch requires its own Spring Security enablement/interceptor classes and web-stack marker; the opposite stack is never a prerequisite. |
| `applicationMethodSecurityOwner` | Named framework infrastructure beans registered by a matching manual enablement annotation | If present, the matching platform method configuration backs off to avoid duplicate named advisors/interceptors. |
| `applicationWebChainOwner` | `SecurityFilterChain` or `SecurityWebFilterChain` bean | Backs off only the corresponding platform web chain. It does not alter `applicationMethodSecurityOwner`. |

### Eligibility states

```text
security.enabled=false  ──────────────────────────────> Platform method security absent
non-web context / missing branch classes ──────────────> Platform method security absent
matching manual method-security owner ────────────────> Platform method configuration backs off
enabled + selected MVC + required classes ─────────────> MVC method security present
enabled + selected WebFlux + required classes ─────────> Reactive method security present
```

Only one of the final two active states is permitted in one application context. A custom web chain may coexist with either active method-security state.

## Authority input to method expressions

| Authority category | Existing producer | Method-security contract |
|---|---|---|
| Standard scopes | Spring `JwtGrantedAuthoritiesConverter` inside `PlatformJwtAuthenticationConverter` | `scope` and `scp` remain available as `SCOPE_<permission>`. |
| Configured nested roles | `RoleClaimsAuthorityMapper` | A configured role-claims path yields trimmed, de-duplicated authorities with the configured prefix, defaulting to `ROLE_`. |
| Missing/malformed roles | `RoleClaimsAuthorityMapper` | No authority is invented; a role-protected method is denied unless another authority satisfies its expression. |

Method security consumes the current `Authentication` and performs no extra claim traversal, authority transformation, persistence, or state transition.

## Observable authorization outcomes

| Web/request state | Method state | Expected result |
|---|---|---|
| Default platform web chain, no token | Method not reached | `401` |
| Authenticated token with required mapped authority | Matching method rule | `200` |
| Authenticated token without required mapped authority | Denied method rule | `403` |
| Authenticated token and `@DenyAll` | Denied method rule | `403` |
| Authenticated token and `@PermitAll` | Method rule allows | `200` |
| Platform security disabled, no application method owner | Annotated method | No platform method denial; any result is owned by independently configured application security |
