## MODIFIED Requirements

### Requirement: Publish images to GHCR with SHA and latest tags

On push to main, after the smoke test passes, CI SHALL publish the single
`rewear-api` JVM image to GHCR tagged with the immutable `sha-<short>` tag and
the convenience `latest` tag (ADR-10). The `rewear-api` package SHALL be public
to avoid the GHCR private-package storage limit.

#### Scenario: Image published with immutable SHA tag

- **WHEN** the smoke test passes for a commit on main
- **THEN** the `rewear-api` image is pushed to GHCR tagged `sha-<short>` for that
  commit and also tagged `latest`

#### Scenario: Package is publicly pullable

- **WHEN** the `rewear-api` package is inspected on GHCR
- **THEN** it is public and can be pulled without authentication

#### Scenario: PR builds do not publish

- **WHEN** the pipeline runs for a pull request rather than a push to main
- **THEN** the image is built and smoke-tested but not published to GHCR

## ADDED Requirements

### Requirement: Single JVM production image

CI SHALL build a single JVM-based image of the API — a fat jar on a JRE base,
configured entirely via env vars (ADR-9) — as the production artifact. It SHALL
target `linux/amd64` (ADR-10).

#### Scenario: Image builds from a commit

- **WHEN** the pipeline runs for a commit
- **THEN** it produces the JVM `rewear-api` image built from that commit

### Requirement: Image smoke test

CI SHALL run the built JVM image and verify it works end-to-end before
publishing: the image boots, `GET /health` returns 200 with the git SHA,
database migrations apply, and the ADR-12 image-resize path succeeds against a
sample image.

#### Scenario: Image passes the smoke test

- **WHEN** the production image is started in CI and probed
- **THEN** `GET /health` returns 200 with the correct git SHA and the sample
  image is resized successfully, and only then does the pipeline proceed to
  publish

#### Scenario: Broken runtime wiring fails the smoke test

- **WHEN** the image starts but its object graph or a runtime dependency is
  misconfigured such that `/health` or the resize path fails
- **THEN** the smoke test fails and the image is not published

## REMOVED Requirements

### Requirement: Dual image build — JVM dev and GraalVM native production

**Reason:** ADR-13 reversed — the GraalVM native production target is dropped;
the API now ships as a single JVM image (see the "Single JVM production image"
requirement).

### Requirement: Native image smoke test

**Reason:** There is no native image to smoke-test; the end-to-end checks move
to the JVM image (see the "Image smoke test" requirement).
