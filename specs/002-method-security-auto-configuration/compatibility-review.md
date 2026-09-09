# Compatibility Review: Automatic Method Security Configuration

**Review scope:** implemented feature `002-method-security-auto-configuration`

**Baseline:** platform `0.1.0`, Spring Boot `4.1.0`, Java `21`
**Disposition:** additive MINOR capability; no module or consumer-property migration is required

## Contract impact

| Stable surface | Implemented change | Compatibility assessment |
|---|---|---|
| Platform modules and starter neutrality | Two classes in the existing auto-configure module; no module, starter, or dependency change. | Compatible. |
| `logistics.parent-service.security.enabled` | Its existing default-true switch additionally governs platform method security. `false` disables both platform security layers. | Additive MINOR behavior; no new property or migration. |
| MVC security | Automatically enables the standard MVC method annotations, including secured and JSR-250 families, when the selected application is Servlet/MVC. | Additive for consumers that did not manually enable method security. Annotated methods may now be enforced when security is enabled, which is the deliberate feature behavior and merits a MINOR release. |
| WebFlux security | Automatically enables reactive publisher method authorization when the selected application is WebFlux. | Additive; no MVC infrastructure is created. |
| JWT role/scope mapping | No mapper/converter/property change. | Compatible; regression tests pin `ROLE_` and `SCOPE_` behavior. |
| Custom web chains | Still replace only the corresponding platform web chain; automatic method security remains separate. | Clarifies and preserves the intended layered contract. |
| Manual method enablement | Matching MVC/WebFlux Spring Security sentinels cause the selected platform method configuration to back off; an application web chain alone does not. | Compatible customization escape hatch; no manual annotation is required. |

## Migration notes

No consumer configuration, module, dependency, or property migration is required. Consumers can remove a redundant matching manual enablement annotation after verifying they do not rely on its non-default flags, custom advisors, expression handler, or ordering. If they retain it, it remains the application-owned method-security configuration and the platform branch backs off.

Consumers with a custom `SecurityFilterChain` or `SecurityWebFilterChain` that want platform method authorization must continue to configure bearer authentication in their own chain. This is existing chain ownership, not a new starter property or annotation requirement.

## Required release evidence

- imports-order and condition-report tests for non-web, Servlet, Reactive, missing-class, and both-API selected-type contexts;
- MVC integration coverage for every required annotation, existing role/scope authorities, default/custom chain behavior, disabled security, and 200/401/403 responses;
- equivalent WebFlux coverage for publisher-returning pre/post authorization, reactive-context boundaries, custom chain behavior, disabled security, and 200/401/403 responses;
- legacy MVC/WebFlux ProblemDetail security tests with manual fixture enablement removed;
- focused `./gradlew` unit/context, MVC, and WebFlux scenarios from the stable quickstart, followed by complete `./gradlew check` and thin-starter/module-boundary verification.

## Documentation follow-up

The public `README.md` and the stable documents in
`specs/001-shared-platform-starter/` (`contracts/security.md`, `quickstart.md`,
and `compatibility-review.md`) now publish this contract. No SemVer exception,
deprecation window, or consumer migration is needed.
