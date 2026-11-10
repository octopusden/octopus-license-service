## Purpose

How the service turns a component version into its license target, or into an exclusion, from the components registry alone. This change adds one fact to the target: whether the component is explicit, which names its builds.

## MODIFIED Requirements

### Requirement: The license target is read from the components registry only

The service SHALL build the license target of a component version from the components registry and from no other system. The target SHALL hold the component version, the versions its artifacts may carry (the requested version, then the registry's build version when it differs), the build system, the labels, whether the component's distribution is explicit, the declared artifacts and the Maven artifact patterns.

#### Scenario: Build version differs from the requested version

- **WHEN** the registry gives build version `1.4.12-0034` for requested version `1.4.12`
- **THEN** the target's artifact versions are `1.4.12` then `1.4.12-0034`

#### Scenario: Build version equals the requested version

- **WHEN** the registry gives build version `1.4.12` for requested version `1.4.12`
- **THEN** the target's artifact versions are `1.4.12` only

#### Scenario: Explicit component

- **WHEN** the registry gives `distribution.explicit: true`
- **THEN** the target is explicit

#### Scenario: Implicit component

- **WHEN** the registry gives `distribution.explicit: false`
- **THEN** the target is not explicit
