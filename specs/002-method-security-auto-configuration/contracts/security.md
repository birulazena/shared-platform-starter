# External Contract: Automatic Method Security

## Consumer experience

An MVC or WebFlux consumer that uses the existing starter and leaves `logistics.parent-service.security.enabled` at its default `true` receives method authorization automatically. The consumer does not need to add `@EnableMethodSecurity` or `@EnableReactiveMethodSecurity`.

| Selected application type | Platform method mechanism | Supported contract |
|---|---|---|
| Servlet/MVC | `@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)` | `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, `@PostFilter`, `@Secured`, `@RolesAllowed`, `@PermitAll`, `@DenyAll` |
| Reactive/WebFlux | `@EnableReactiveMethodSecurity` | Publisher-returning `@PreAuthorize` and `@PostAuthorize`, including Reactor-context-aware authorization |
| Non-web | None | The platform contributes no method-security infrastructure |

## Activation and isolation

Each branch requires all of the following:

1. `logistics.parent-service.security.enabled=true`, or an absent property using its existing default;
2. the matching selected Spring Boot web application type;
3. the matching Spring Security method-security classes and matching web-stack classes.

The implementation checks these exact classpath markers:

- MVC: `DispatcherServlet`, `EnableMethodSecurity`, `AuthorizationManagerBeforeMethodInterceptor`, `AuthorizationManagerAfterMethodInterceptor`, `PreFilterAuthorizationMethodInterceptor`, `PostFilterAuthorizationMethodInterceptor`, and `jakarta.annotation.security.RolesAllowed`;
- WebFlux: `DispatcherHandler`, `EnableReactiveMethodSecurity`, `AuthorizationManagerBeforeReactiveMethodInterceptor`, `AuthorizationManagerAfterReactiveMethodInterceptor`, `PreFilterAuthorizationReactiveMethodInterceptor`, `PostFilterAuthorizationReactiveMethodInterceptor`, and Reactor `Mono`.

The MVC branch never creates reactive method infrastructure. The WebFlux branch never creates MVC method infrastructure. This remains true when tests or a consumer have both API families on the classpath: selected application type is decisive.

## Authority contract

Method expressions use the existing authenticated authority set without a new claim mapper:

- configured nested roles are exposed with the current role prefix, default `ROLE_`;
- standard `scope` and `scp` claims remain `SCOPE_<permission>` authorities;
- malformed, missing, or mixed-type configured role claims grant nothing.

Accordingly, `hasAuthority('ROLE_dispatcher')` and `hasAuthority('SCOPE_shipments.read')` have the same meaning at method level as they do in the current selected web security flow.

## Web-chain ownership and disabled behavior

An application `SecurityFilterChain` or `SecurityWebFilterChain` backs off only the corresponding platform HTTP chain. It does not, by itself, disable the selected automatic method-security configuration. The application-owned chain remains responsible for bearer authentication and its request policy; the platform method layer evaluates the resulting authentication.

With `security.enabled=false`, the platform contributes neither its default web chain nor its automatic method-security mechanism. It does not disable an application's own web or method-security configuration.

## Manual enablement compatibility

Manual matching `@EnableMethodSecurity` or `@EnableReactiveMethodSecurity` remains optional. When its named Spring Security infrastructure is already registered, the matching platform method auto-configuration backs off to prevent duplicate advisors/interceptors. That consumer configuration owns its selected flags and customizations. A web-chain bean alone is not such a signal.

The MVC ownership sentinels are `_prePostMethodSecurityConfiguration`, `_securedMethodSecurityConfiguration`, and `_jsr250MethodSecurityConfiguration`. The reactive ownership sentinels are `_reactiveMethodSecurityConfiguration` for authorization-manager mode and `reactiveMethodSecurityConfiguration` plus `methodSecurityInterceptor` for legacy mode. The platform branch backs off if any sentinel for the selected stack is present.

## Verification matrix

| Case | MVC | WebFlux |
|---|---|---|
| Required authority, default web chain | `200` | `200` |
| Missing role/scope authority | `403` | `403` |
| Missing bearer token for protected endpoint | `401` before method invocation | Same |
| Application-owned bearer chain | Platform chain absent; method layer still `200`/`403` | Same |
| `security.enabled=false` without app method enablement | No platform method enforcement | Same |
| Both web APIs on classpath | Only MVC method infrastructure | Only reactive method infrastructure |
