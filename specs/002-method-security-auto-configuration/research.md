# Research: Automatic Method Security Configuration

## Decision: Add one isolated auto-configuration per selected web stack

Create `PlatformMvcMethodSecurityAutoConfiguration` in the existing MVC security package and `PlatformWebFluxMethodSecurityAutoConfiguration` in the existing reactive security package. Each is an `@AutoConfiguration` ordered after its matching platform web-security configuration.

**Rationale**: The starter already isolates `PlatformMvcSecurityAutoConfiguration` and `PlatformWebFluxSecurityAutoConfiguration` with `@ConditionalOnWebApplication`, string class-name checks, `security.enabled`, and imports ordering. Mirroring that boundary preserves stack neutrality and does not introduce a new module or architecture.

**Alternatives considered**:

- A single shared method-security configuration was rejected because direct Servlet and Reactive enablement annotations install incompatible infrastructure and would link the unselected stack.
- Separate MVC and WebFlux starters/modules were rejected by the project constitution and the feature scope.

## Decision: Use the standard framework enablement annotations with MVC flags

The MVC class uses `@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)`; the reactive class uses `@EnableReactiveMethodSecurity`.

**Rationale**: Spring Security enables pre/post annotations by default, but `@Secured` and JSR-250 annotations are disabled by default. The MVC flags are therefore required for the specified `@Secured`, `@RolesAllowed`, `@PermitAll`, and `@DenyAll` coverage. The reactive annotation installs Spring Security's publisher-aware authorization-manager infrastructure, which is appropriate for WebFlux methods returning `Mono`/`Flux`.

**Alternatives considered**:

- Reimplementing advisors, expressions, or Reactor context propagation was rejected because Spring Security already supplies the required semantics.
- Using MVC `@EnableMethodSecurity` in a reactive fixture was rejected; it is the existing duplication hazard and does not represent the requested reactive mechanism.

## Decision: Preserve the existing authority pipeline unchanged

Method authorization receives the `Authentication` populated by the existing selected web resource-server flow. No change is made to `PlatformProperties`, `SecurityProperties`, `RoleClaimsAuthorityMapper`, or `PlatformJwtAuthenticationConverter`.

**Rationale**: `PlatformJwtAuthenticationConverter` preserves Spring Security's standard `SCOPE_` authorities and adds the roles produced by `RoleClaimsAuthorityMapper` using the configured path and prefix. Method expressions must observe this exact authority set rather than introducing a second mapper or vendor-specific claim convention.

**Alternatives considered**:

- A method-security-specific JWT converter or `GrantedAuthority` mapper was rejected because it can diverge from endpoint behavior and duplicate claims processing.

## Decision: Keep method security independent from web-chain ownership

The method auto-configurations have the same enabled/type/class conditions as their stack but no missing-`Security(Filter|WebFilter)Chain` condition. A custom chain continues to back off only the existing platform chain bean.

**Rationale**: The feature requires a service-owned chain to replace request authentication/authorization policy without silently removing platform method protection. Current platform chain methods already contain the chain-specific back-off, so the new configuration class must remain outside it.

**Alternatives considered**:

- Nesting method enablement in each platform chain bean or its missing-chain condition was rejected because it would turn off method security whenever a supported application override is present.

## Decision: Back off for application-owned manual method enablement

Each new branch will guard against the named Spring Security method-security configuration/interceptor sentinels that the matching enablement annotation registers. The MVC guard covers pre/post, secured, and JSR-250 sentinels; the reactive guard covers authorization-manager and legacy reactive sentinels.

**Rationale**: Spring Security's two enablement annotations import named configuration and advisor/interceptor beans. A consumer that still enables the matching mechanism manually must remain an explicit owner rather than causing duplicate bean definitions or duplicate advice. This is a compatibility back-off, not a new consumer requirement.

**Alternatives considered**:

- No back-off was rejected because two enablement paths can collide.
- Conditioning on `SecurityFilterChain`/`SecurityWebFilterChain` was rejected because those beans govern request security, not method-security ownership.
- Requiring a consumer annotation was rejected by the feature requirements.

## Decision: Use the existing fast and adoption suites in TDD order

Extend the current context tests, selected-stack MVC/WebFlux security adoption tests, and legacy ProblemDetail fixtures. First write tests proving the conditions and user-visible authorization behavior, then add the production classes/import entries.

**Rationale**: The current source sets deliberately emulate consumer adoption: MVC and WebFlux suites each consume the public starter with only their selected web stack. `SecurityAutoConfigurationContextTest` and `AutoConfigurationSelectionTest` already test selected-stack conditions and chain back-off. The legacy ProblemDetail tests currently provide method-denial coverage but manually enable method security; removing those annotations turns them into automatic-enablement regression coverage.

**Alternatives considered**:

- A unit-only test strategy was rejected because auto-configuration selection, custom chains, servlet filters, and Reactor context require application context and integration coverage.
- A real Keycloak container was rejected because controlled mock JWTs already exercise the mapping and authorization contracts deterministically.

## Decision: No build dependency or module change

Do not edit Gradle dependencies or project settings.

**Rationale**: The auto-configure module already has compile-only Spring Security configuration, MVC, WebFlux, and Reactor APIs. The public starter already supplies Spring Boot Security and the resource-server APIs while still not selecting a web stack.

**Alternatives considered**:

- Adding a method-security module, MVC/WebFlux starter, or direct web starter dependency was rejected because it violates the existing three-module, web-neutral platform contract.
