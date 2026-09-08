## ADDED Requirements

### Requirement: Local dev stack via docker compose

The repository SHALL provide a `docker compose` stack that starts the backing
services needed to run the API locally: Postgres, Zitadel, and Garage
(S3-API object storage). Third-party images SHALL be pinned to exact upstream
versions (ADR-10). The stack SHALL run without any cloud credentials so that
development works offline.

#### Scenario: Stack starts locally

- **WHEN** a developer runs the documented compose command on a machine with
  Docker and no cloud credentials
- **THEN** Postgres, Zitadel, and Garage containers start and reach a healthy
  state

#### Scenario: Third-party images are version-pinned

- **WHEN** the compose file is inspected
- **THEN** every third-party image references an exact version tag, not a
  floating tag such as `latest`

### Requirement: API configured against the dev stack by environment variables

The API SHALL connect to the dev stack using only environment variables
(ADR-9) — database connection settings, S3 endpoint/credentials/bucket for
Garage, and the Zitadel issuer/JWKS location — with no code change required to
retarget the same build at production backends (e.g. Backblaze B2).

#### Scenario: API connects to the dev database and object storage

- **WHEN** the API is started with environment variables pointing at the running
  dev stack
- **THEN** it establishes a database connection, applies schema migrations, and
  can reach the Garage S3 endpoint

#### Scenario: Same build retargets storage without code change

- **WHEN** the S3 endpoint and credential environment variables are changed from
  the Garage dev values to different S3-API values
- **THEN** the same application build uses the new target with no recompilation

### Requirement: Dev stack provisions identity without wiring auth into routes

The dev stack SHALL include Zitadel so it is available for the later auth
integration change, while this change SHALL NOT enforce JWT validation or role
checks on any route.

#### Scenario: Zitadel is available but routes are unauthenticated

- **WHEN** the dev stack is running and a request is made to an API route in
  this change
- **THEN** Zitadel is reachable in the stack, and the request is served without
  requiring a JWT because auth enforcement is not part of this change
