# Feature Specification: Automatic Method Security Configuration

**Feature Branch**: `002-method-security-auto-configuration`

**Created**: 2026-08-25

**Status**: Draft

**Input**: User description: "Extend the existing HZ Logistics Parent Service starter so that MVC and WebFlux method-level authorization is enabled automatically when logistics.parent-service.security.enabled=true, while preserving the existing JWT authority mapping, web-stack isolation, security disablement, and application-owned web security chain behavior."

**Relationship**: Follow-up feature to `specs/001-shared-platform-starter`.

## Clarifications

### Session 2026-08-25

- Q: Should the existing `logistics.parent-service.security.enabled` property control both platform JWT/web security and automatic method security? → A: Yes; the existing property is the single enablement switch for both security layers.
- Q: Should the starter support both MVC and WebFlux through separate conditional auto-configurations while activating only the selected web stack in each application context? → A: Yes; preserve the existing starter-based stack selection mechanism and activate only the matching method-security branch.
- Q: Should an application-owned `SecurityFilterChain` or `SecurityWebFilterChain` disable only the corresponding platform web-security chain while preserving automatic method security? → A: Yes; custom request-level security replaces only the platform web chain, while method security remains active.
- Q: Should method security be enabled automatically in non-web worker applications that use neither MVC nor WebFlux? → A: No; automatic method security is out of scope for non-web applications, which may configure their own mechanism explicitly.
- Q: Which annotation families and automatic enablement mechanisms are mandatory for MVC and WebFlux? → A: MVC uses `@EnableMethodSecurity` and requires `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, `@PostFilter`, `@Secured`, `@RolesAllowed`, `@PermitAll`, and `@DenyAll`; WebFlux uses `@EnableReactiveMethodSecurity` and requires at least `@PreAuthorize` and `@PostAuthorize` for publisher-returning methods.
- Q: What happens when a consumer already enables method security for the selected web stack? → A: The matching platform method-security auto-configuration backs off when its Spring Security infrastructure sentinel is already registered; the consumer-owned method-security configuration remains active with its selected options, and an application-owned web chain alone is not a back-off signal.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Authorize MVC methods automatically (Priority: P1)

As an MVC service team, I want method-level authorization to work as soon as I add the platform starter and enable platform security, so that I can protect service methods without adding platform-enablement annotations to the consumer application.

**Why this priority**: MVC services must receive the complete authorization behavior promised by the shared security platform while keeping consumer applications small and consistent.

**Independent Test**: Start an MVC consumer fixture with the starter, `logistics.parent-service.security.enabled=true`, a controlled JWT decoder, and no explicit method-security enabling annotation. Invoke representative methods protected by every required MVC annotation with tokens that contain the required authorities, lack them, or are absent.

**Acceptance Scenarios**:

1. **Given** an MVC consumer fixture has platform security enabled and does not declare `@EnableMethodSecurity`, **when** an authenticated caller presents the required mapped role or permission, **then** a method protected by `@PreAuthorize` or `@PostAuthorize` is allowed when its expression succeeds and rejected when its expression fails.
2. **Given** an MVC method accepts a collection or returns a collection protected by `@PreFilter` or `@PostFilter`, **when** the caller has authorities for only part of the data, **then** the method receives or returns only the permitted elements according to the annotation contract.
   **When** the caller has authority for no elements, **then** the fixture observes an empty filtered collection at the method boundary and no unauthorized element is returned.
3. **Given** an MVC method is protected by `@Secured` or `@RolesAllowed`, **when** the caller has the required role authority, **then** the method succeeds; **when** the caller lacks it, **then** method authorization rejects the invocation.
4. **Given** an MVC method is annotated with `@PermitAll`, **when** method authorization evaluates the invocation, **then** the method is not denied by that annotation; **when** the method is annotated with `@DenyAll`, **then** every caller is denied by method authorization. Any selected web-security authentication requirement remains in force before method invocation.
5. **Given** an MVC consumer declares `@EnableMethodSecurity` with any selected MVC options, **when** the application starts, **then** the platform MVC method-security auto-configuration backs off without duplicate advisors or interceptors, while the consumer-owned configuration remains authoritative.

---

### User Story 2 - Authorize reactive methods automatically (Priority: P1)

As a WebFlux service team, I want reactive method-level authorization to work automatically with the same platform setting, so that publisher-returning service methods enforce authorization without MVC method-security infrastructure or consumer-side enablement annotations.

**Why this priority**: Reactive services need an equivalent security boundary, but reactive authorization must remain compatible with asynchronous execution and must not be implemented by activating the Servlet mechanism.

**Independent Test**: Start a WebFlux consumer fixture with platform security enabled, no explicit method-security enabling annotation, and controlled reactive JWT authentication. Invoke methods returning reactive publishers with matching and non-matching authorities.

**Acceptance Scenarios**:

1. **Given** a WebFlux consumer fixture has platform security enabled and declares no explicit MVC or reactive method-security enabling annotation, **when** a valid token calls a method protected by `@PreAuthorize` or `@PostAuthorize`, **then** a matching authority permits the publisher result and a missing authority produces an authorization failure.
2. **Given** a reactive method performs authorization around a publisher result, **when** the publisher is evaluated asynchronously, **then** the authorization decision remains attached to the current reactive security context and does not allow a caller because of a missing or unrelated context.
3. **Given** the selected application type is WebFlux, **when** the application starts, **then** only reactive method-security behavior is eligible and no MVC method-security infrastructure is created.
4. **Given** a WebFlux consumer declares `@EnableReactiveMethodSecurity`, **when** the application starts, **then** the platform WebFlux method-security auto-configuration backs off without duplicate advisors or interceptors, while the consumer-owned configuration remains authoritative.

---

### User Story 3 - Preserve layered web and method authorization (Priority: P1)

As a service owner, I want web authentication and method authorization to remain separate policy layers, so that changing the web security chain does not accidentally remove method protection.

**Why this priority**: A consumer-owned web chain is an existing supported customization point. Method security must remain a reliable platform capability rather than being coupled to ownership of HTTP request authorization.

**Independent Test**: Exercise protected MVC and WebFlux methods with valid tokens, tokens missing the required role, and no token. Repeat with an application-owned `SecurityFilterChain` or `SecurityWebFilterChain` that supports bearer authentication and owns request authorization.

**Acceptance Scenarios**:

1. **Given** platform web security is active and an endpoint exposes a method requiring a role, **when** a valid token contains that role, **then** the request returns `200`; **when** the valid token lacks that role, **then** the request returns `403`; **when** no token is supplied, **then** the protected endpoint returns `401` before the method executes.
2. **Given** an MVC application supplies its own `SecurityFilterChain`, **when** the service starts, **then** the platform MVC web chain backs off while platform MVC method security remains active; a valid token with the required role succeeds and a valid token without it is rejected by method authorization.
3. **Given** a WebFlux application supplies its own `SecurityWebFilterChain`, **when** the service starts, **then** the platform WebFlux web chain backs off while platform reactive method security remains active; a valid token with the required role succeeds and a valid token without it is rejected by method authorization.
4. **Given** `logistics.parent-service.security.enabled=false`, **when** a consumer fixture has no application-owned method-security enablement, **then** platform web security and platform method security are both disabled. An annotated method is not rejected by the platform method-security contribution, while any independently application-owned security remains application-owned.

---

### User Story 4 - Reuse existing JWT authorities across method expressions (Priority: P1)

As a service developer, I want method expressions to use the same authorities as endpoint authorization, so that introducing method checks does not require changing token claims or authorization vocabulary.

**Why this priority**: The starter already defines JWT role and scope mapping. Method security is useful only if `@PreAuthorize` and related checks observe that established mapping exactly.

**Independent Test**: Configure the existing nested role-claims path and invoke method-protected endpoints using controlled tokens containing role claims, `scope`, or `scp` claims. Check both successful and rejected `hasAuthority` expressions in MVC and WebFlux fixtures.

**Acceptance Scenarios**:

1. **Given** `role-claims-path` points to the configured nested role claim and the token contains the required role, **when** `@PreAuthorize` checks the corresponding default authority, **then** the expression succeeds with the existing `ROLE_` prefix.
2. **Given** a token contains a permission in the standard `scope` claim or the equivalent `scp` claim, **when** `@PreAuthorize` checks `hasAuthority('SCOPE_<permission>')`, **then** the expression succeeds; a token without that permission is rejected.
3. **Given** a token contains a role claim but not the required role or permission, **when** a method expression checks that authority, **then** the method is not invoked and the caller receives an authorization failure rather than an invented authority.
4. **Given** the application uses an existing configured role prefix or role-claims path, **when** method authorization evaluates the mapped authorities, **then** those existing settings remain effective and no vendor-specific claim path is introduced by this feature.

### Edge Cases

- If `security.enabled` is omitted, the existing default of enabled applies and method security is eligible along with platform web security.
- If the configured role claim is missing, null, malformed, or contains mixed non-string values, no role authority is invented and a role-protected method fails authorization when no other required authority exists.
- If both `scope` and `scp` are present, authorities follow the established standard scope mapping without duplicate decisions changing the result.
- A `hasAuthority` expression is evaluated against the complete mapped authority name, including `ROLE_` for configured roles and `SCOPE_` for standard scopes; permission checks do not silently switch to role semantics.
- A `@PreFilter` or `@PostFilter` invocation with no matching elements produces the annotation-defined empty result or rejection behavior and must not leak an unauthorized element.
- Reactive method checks must remain correct when a publisher is delayed, empty, or scheduled on another supported reactive execution boundary.
- MVC collection fixtures must make the no-match contract observable: the service receives an empty pre-filtered collection and the post-filtered response is empty, with no unauthorized element exposed.
- Reactive fixtures must assert the framework-defined result for authorized delayed, empty, and rescheduled publishers and rejection for a missing authority, rather than only asserting that the application starts.
- If both MVC and WebFlux APIs are available on a test classpath, the selected application type determines the active method-security mechanism; the nonselected mechanism must not create beans or alter startup.
- An application-owned web chain may change request authentication and authorization policy, but its presence alone must not disable the selected platform method-security mechanism.
- This feature does not enable method security for a non-web application unless the application explicitly owns and enables an appropriate mechanism; platform web-stack selection remains the existing boundary.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When `logistics.parent-service.security.enabled=true` or the property is absent and therefore defaults to true, the platform MUST enable both its JWT/web security and method-level authorization for the consumer's selected supported web stack without requiring a consumer-side method-security enabling annotation.
- **FR-002**: The MVC method-security capability MUST use `@EnableMethodSecurity` and support `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, `@PostFilter`, `@Secured`, `@RolesAllowed`, `@PermitAll`, and `@DenyAll` with their standard authorization semantics.
- **FR-003**: The WebFlux method-security capability MUST use `@EnableReactiveMethodSecurity` and the reactive method-security mechanism, and MUST support reactive method authorization, including at minimum `@PreAuthorize` and `@PostAuthorize`, for publisher-returning methods.
- **FR-004**: MVC and WebFlux MUST activate separate stack-specific method-security mechanisms while preserving the existing starter-based web-stack selection. Selecting MVC MUST NOT create reactive method-security infrastructure, and selecting WebFlux MUST NOT create MVC method-security infrastructure, even when classes for both stacks are available.
- **FR-005**: Method-security activation MUST follow the selected web application type and existing classpath conditions. A service using neither supported web stack, including a non-web worker application, MUST NOT receive MVC or WebFlux method-security infrastructure from this feature.
- **FR-006**: Setting `logistics.parent-service.security.enabled=false` MUST disable the platform method-security contribution together with the platform web-security contribution. It MUST NOT disable unrelated platform capabilities, and it MUST NOT prevent an application from explicitly owning its own security behavior.
- **FR-007**: An application-provided `SecurityFilterChain` MUST disable only the platform MVC web-security chain. An application-provided `SecurityWebFilterChain` MUST disable only the platform WebFlux web-security chain. Neither application-owned chain MUST, by its presence alone, disable platform method security for the selected stack; method security MUST remain active.
- **FR-008**: Method authorization MUST evaluate the same authenticated authorities already produced by the platform JWT resource-server flow, including authorities extracted from the configured `role-claims-path`.
- **FR-009**: The default configured role prefix MUST remain `ROLE_`, and role expressions used by MVC annotations and `@PreAuthorize`/`@PostAuthorize` MUST match those prefixed authorities exactly. Existing custom role-prefix behavior MUST remain compatible.
- **FR-010**: Standard JWT scope authorities from both `scope` and `scp` claims MUST remain available with the existing `SCOPE_` prefix, and method expressions using `hasAuthority('SCOPE_<permission>')` MUST be able to allow or reject calls based on those authorities.
- **FR-011**: With platform web security active, a protected method endpoint MUST preserve the existing layered outcomes: a valid token with the required authority receives `200`, a valid token without it receives `403`, and no token receives `401` without invoking the protected method.
- **FR-012**: The feature MUST preserve the existing JWT validation, nested role extraction, role-prefix, scope mapping, problem response, and selected-stack web-security contracts defined by `001-shared-platform-starter` unless a change is explicitly required by this feature and documented as a compatibility impact.
- **FR-013**: The implementation MUST remain within the existing three-module platform structure. It MUST NOT create a new Gradle module, a separate MVC/WebFlux starter, or business-domain logic.
- **FR-014**: MVC and WebFlux integration coverage MUST prove automatic method-security activation without explicit consumer enablement, every required MVC annotation, the primary reactive annotations, mapped role and scope authorization, 200/401/403 outcomes, `security.enabled=false`, selected-stack isolation, and application-owned web-chain independence.
- **FR-015**: If a consumer explicitly enables method security for the selected stack, the matching platform method-security auto-configuration MUST back off when its existing Spring Security infrastructure sentinel is present, without duplicate advisors/interceptors or loss of the consumer-owned configuration. An application-owned web chain alone MUST NOT trigger this back-off.

### Acceptance Matrix

| Capability | MVC | WebFlux |
|---|---|---|
| Automatic activation with security enabled | Required | Required |
| Consumer-side method-security enablement | Not required | Not required |
| Required annotation coverage | `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, `@PostFilter`, `@Secured`, `@RolesAllowed`, `@PermitAll`, `@DenyAll` | Reactive mechanism, including `@PreAuthorize` and `@PostAuthorize` |
| Role authority source | Existing configured role-claims path and prefix | Same |
| Scope authority source | `scope` and `scp`, using `SCOPE_` | Same |
| Valid token with required authority | `200` | `200` |
| Valid token without required authority | `403` | `403` |
| No token on web-protected endpoint | `401` | `401` |
| Application-owned web chain | Web chain only backs off | Web chain only backs off |
| Matching consumer method-security enablement | Platform method branch backs off; consumer configuration remains active | Platform method branch backs off; consumer configuration remains active |
| `security.enabled=false` | Platform web and method security disabled | Platform web and method security disabled |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the eight required MVC annotation behaviors are demonstrated by automated acceptance assertions in an MVC consumer fixture with no explicit method-security enabling annotation.
- **SC-002**: 100% of the required reactive method-security acceptance assertions for `@PreAuthorize` and `@PostAuthorize` pass in a WebFlux consumer fixture with no explicit MVC or reactive method-security enabling annotation.
- **SC-003**: In both supported web stacks, 100% of the authorization matrix cases return the documented result: required authority `200`, missing authority `403`, and missing token `401` when web security protects the endpoint.
- **SC-004**: 100% of role and scope method-expression tests use authorities produced by the existing JWT mapping contract, including the configured nested role path, default `ROLE_` prefix, and `SCOPE_` authorities from both `scope` and `scp` claims.
- **SC-005**: A consumer fixture can remove manual method-security enablement and still pass all applicable method-authorization acceptance tests without adding a replacement platform module or business logic.
- **SC-006**: With `security.enabled=false`, 100% of tests confirm that the platform contributes neither the platform web-security enforcement nor platform method-security enforcement, while unrelated platform capabilities remain eligible.
- **SC-007**: With an application-owned web chain present, 100% of both MVC and WebFlux tests confirm that only the platform web chain backs off and method authorization still distinguishes an authorized token from a token missing the required authority.
- **SC-008**: When both web-stack APIs are available but one web application type is selected, 100% of context checks confirm that no infrastructure from the nonselected method-security mechanism is created and the selected application starts successfully.
- **SC-009**: Existing security integration coverage for JWT validation, role mapping, scope mapping, public endpoints, and problem responses remains green after the feature is added, with no new consumer-facing configuration required beyond the existing security setting.
- **SC-010**: 100% of selected-stack context tests confirm that matching consumer method-security enablement backs off the corresponding platform method branch without duplicate infrastructure, while a web-chain bean alone does not cause method-security back-off.

## Assumptions

- The feature builds on the contracts and defaults established by `specs/001-shared-platform-starter`, including `security.enabled=true` by default, controlled mock JWTs for reusable-infrastructure tests, and selected-stack web security.
- Consumer applications select one supported web stack per application context. MVC and WebFlux behavior is validated separately even if a broad test environment exposes both APIs.
- Method-level authorization is an additional policy layer. It does not replace endpoint authentication, JWT validation, public endpoint rules, or application-owned web security.
- An application-owned web chain that needs method tests is responsible for preserving bearer-token authentication; this feature only requires that chain ownership not disable platform method security.
- Standard framework semantics define the detailed behavior of the listed annotations, collection filtering, and reactive publisher authorization. The feature contract specifies which mechanisms are available and how they interact with platform authorities.
- A matching consumer-owned method-security annotation is a supported compatibility path: it owns the selected method-security options, while the platform contributes no duplicate selected-stack method infrastructure.
- Implementation-specific Kotlin classes, configuration registrations, dependency declarations, fixture names, and test file paths are intentionally deferred to `plan.md`.

## Out of Scope

- Creating a new Gradle module, a separate MVC or WebFlux starter, or changing the existing three-module platform architecture.
- Adding business-domain authorization rules, logistics workflows, persistence, endpoints, or a new permission model.
- Changing JWT signature validation, issuer validation, role-claims-path syntax, role-prefix configuration, scope claim parsing, public endpoint matching, or common error responses except where required to expose the existing authorities to method security.
- Replacing application-owned security chains or forcing application-specific request authorization policies.
- Supporting method-security mechanisms for nonselected or unsupported web stacks.
- Requiring every possible method-security annotation to have identical semantics in WebFlux when the reactive mechanism does not expose that MVC annotation; the reactive minimum is `@PreAuthorize` and `@PostAuthorize`.
