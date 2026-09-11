# api-foundation Specification

## Purpose
TBD - created by archiving change project-scaffolding. Update Purpose after archive.
## Requirements
### Requirement: Runnable API application

The `rewear-api` application SHALL be a runnable Kotlin + Ktor server that boots
on the CIO engine, reads all configuration from environment variables (ADR-9),
and requires no feature modules to serve infrastructure endpoints.

#### Scenario: Application boots with valid configuration

- **WHEN** the application starts with valid environment configuration (database
  connection and object-storage settings present)
- **THEN** the Ktor CIO server starts and begins accepting HTTP requests

#### Scenario: Application fails fast on missing required configuration

- **WHEN** the application starts with a required environment variable absent
- **THEN** startup aborts with a non-zero exit and an error naming the missing
  variable, rather than starting in a partially-configured state

### Requirement: Health endpoint reports build identity

The API SHALL expose an unauthenticated `GET /health` endpoint that reports
liveness and the build's git SHA (ADR-10).

#### Scenario: Health check succeeds

- **WHEN** a client sends `GET /health` against a running instance
- **THEN** the response status is 200 and the body includes the git SHA of the
  build the running instance was compiled from

#### Scenario: Git SHA reflects the built commit

- **WHEN** the application is built from a given commit and its `/health`
  endpoint is queried
- **THEN** the reported git SHA matches that commit's short SHA

### Requirement: Enforced module boundaries

The codebase SHALL be organised as Gradle subprojects realising the refined
ADR-4 decomposition: two domain modules — `users` (identity) and `catalog`
(containing `listings`, `categories`, and `favorites` as internal packages) —
plus `app` (composition root) and `common` (shared kernel). A domain module SHALL
expose its cross-module contract as `public` and keep its implementation
`internal`, so another module depending on it sees only the contract.
Cross-module collaboration SHALL occur as in-process calls through those `public`
interfaces (no HTTP, no serialization between modules). Package boundaries within
`catalog` (listings / categories / favorites) SHALL be enforced by Konsist tests,
which SHALL also enforce that other domains are referenced by ID only. Admin
capability SHALL NOT be a module; the `admin` role check belongs to shared auth
middleware.

#### Scenario: Module can call another module through its public contract

- **WHEN** `catalog` depends on `users` and calls it through a `public` interface
  (e.g. `UserQueries`)
- **THEN** the project compiles and the call resolves in-process

#### Scenario: Reaching into another module's internals fails to compile

- **WHEN** `catalog` attempts to reference a type marked `internal` in `users`
- **THEN** the build fails to compile because Kotlin `internal` visibility is
  scoped to the owning Gradle module

#### Scenario: Cyclic module dependency is rejected

- **WHEN** two modules are configured to depend on each other, forming a cycle
- **THEN** the Gradle build fails rather than producing a binary with a
  dependency cycle

#### Scenario: Intra-catalog package violation fails the Konsist test

- **WHEN** code in `catalog`'s `favorites` package reaches into the internals of
  the `listings` package (rather than through its published surface)
- **THEN** the Konsist architecture test fails in CI

### Requirement: Composition root wires the application

An `app` module SHALL be the sole composition root: the only module depending on
every domain module (`users`, `catalog`), responsible for booting the server,
running database migrations at startup, and aggregating the per-module Koin
definitions. It SHALL be the module compiled to the production native image.

#### Scenario: Application graph assembles at startup

- **WHEN** the `app` module boots
- **THEN** the Koin object graph is assembled with each domain module's `public`
  interface bound to its `internal` implementation, and a missing or ambiguous
  binding fails startup with a diagnostic naming the unsatisfied dependency

