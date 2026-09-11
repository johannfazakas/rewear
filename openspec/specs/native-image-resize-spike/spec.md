# native-image-resize-spike Specification

## Purpose
TBD - created by archiving change project-scaffolding. Update Purpose after archive.
## Requirements
### Requirement: Photo resizing validated under native compilation

The change SHALL include a minimal image-resize code path that decodes a photo,
produces the ADR-12 fixed variants (at least a thumbnail and a detail variant),
and re-encodes them, exercising the AWT/ImageIO stack ADR-13 flags as the
weakest area of native-image support. This path SHALL be invoked by the native
smoke test (see build-and-release-pipeline) against a real sample image so that
its behaviour under GraalVM native compilation is verified on every commit.

This is a spike, not the production resize API: it establishes only whether
server-side resizing is viable natively. The full resize logic is built in the
Listings change.

#### Scenario: Resize path produces valid variants under the native image

- **WHEN** the native production image runs the resize path against a sample
  photo
- **THEN** it produces the expected fixed variants, each a valid image of the
  expected dimensions

#### Scenario: Resize path also works on the JVM

- **WHEN** the resize path is exercised by a JVM unit test against the sample
  photo
- **THEN** it produces the same valid variants, confirming the logic itself is
  correct independent of the native/JVM distinction

### Requirement: Recorded outcome and fallback decision

The spike SHALL produce a recorded outcome documenting whether server-side
resizing works under the native image. On success, server-side resizing is
confirmed for the Listings change. On failure that cannot be resolved with
reasonable native configuration, the recorded decision SHALL be to fall back to
client-side resizing before upload, revisiting ADR-12/ADR-13. The `README.md`
status and `docs/ROADMAP.md` SHALL be updated with the outcome.

#### Scenario: Success is recorded and unblocks server-side resizing

- **WHEN** the resize path passes the native smoke test
- **THEN** the outcome is documented as "server-side resizing confirmed" and the
  Listings change may assume the API resizes photos

#### Scenario: Failure records the client-side fallback

- **WHEN** the resize path cannot be made to work under the native image with
  reasonable configuration
- **THEN** the recorded decision is to resize client-side before upload, ADR-12
  and ADR-13 are revisited, and the roadmap is updated accordingly

