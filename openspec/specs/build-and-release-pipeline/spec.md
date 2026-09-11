# build-and-release-pipeline Specification

## Purpose
TBD - created by archiving change project-scaffolding. Update Purpose after archive.
## Requirements
### Requirement: JVM build and unit tests

CI SHALL build the project on the JVM and run its unit tests, providing fast
feedback before the slower native build. Test names SHALL follow the project's
given-when-then convention.

#### Scenario: Unit tests run on every push and PR

- **WHEN** a commit is pushed to main or a pull request is opened
- **THEN** CI compiles the project on the JVM and runs the unit test suite,
  failing the pipeline if any test fails

### Requirement: Dual image build — JVM dev and GraalVM native production

CI SHALL build two images of the API from the same commit: a JVM-based dev
image and a GraalVM native-image production image (native binary on a minimal
base). Both SHALL target `linux/amd64` only (ADR-10).

#### Scenario: Both images build from one commit

- **WHEN** the pipeline runs for a commit
- **THEN** it produces both the JVM dev image and the GraalVM native production
  image built from that same commit

#### Scenario: Native compilation failure fails the pipeline

- **WHEN** a change introduces code or a library that cannot be compiled to a
  GraalVM native image
- **THEN** the native image build fails and the pipeline fails, surfacing the
  native-only breakage on that commit rather than in a later change

### Requirement: Native image smoke test

CI SHALL run the built native production image and verify it works end-to-end
before publishing: the image boots, `GET /health` returns 200 with the git SHA,
and the ADR-13 image-resize path succeeds against a sample image.

#### Scenario: Native image passes the smoke test

- **WHEN** the native production image is started in CI and probed
- **THEN** `GET /health` returns 200 with the correct git SHA and the sample
  image is resized successfully, and only then does the pipeline proceed to
  publish

#### Scenario: Broken runtime wiring fails the smoke test

- **WHEN** the native image starts but its object graph or a runtime dependency
  is misconfigured such that `/health` or the resize path fails
- **THEN** the smoke test fails and the image is not published

### Requirement: Publish images to GHCR with SHA and latest tags

On push to main, after the smoke test passes, CI SHALL publish both images to
GHCR tagged with the immutable `sha-<short>` tag and the convenience `latest`
tag (ADR-10). The `rewear-api` package SHALL be public to avoid the GHCR
private-package storage limit.

#### Scenario: Images published with immutable SHA tag

- **WHEN** the smoke test passes for a commit on main
- **THEN** both images are pushed to GHCR tagged `sha-<short>` for that commit
  and also tagged `latest`

#### Scenario: Package is publicly pullable

- **WHEN** the `rewear-api` package is inspected on GHCR
- **THEN** it is public and can be pulled without authentication

#### Scenario: PR builds do not publish

- **WHEN** the pipeline runs for a pull request rather than a push to main
- **THEN** images are built and smoke-tested but not published to GHCR

