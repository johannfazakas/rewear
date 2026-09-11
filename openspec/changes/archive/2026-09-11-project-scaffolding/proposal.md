## Why

The architecture is fully designed but no code exists yet. Every later change
on the roadmap ("Auth integration", "Listings", ...) needs a runnable system to
land in. This change creates that system: an empty-but-runnable Ktor modular
monolith with its module seams, a local dev stack, and a CI pipeline that builds
and smoke-tests the GraalVM native production image on every commit — so
native-only breakage surfaces from day one rather than deep into feature work.

It also resolves the single biggest open technical risk before any feature
depends on it: ADR-13 flags AWT/ImageIO-based image resizing (ADR-12) as the
weakest area of GraalVM native-image support. This change includes a spike to
prove it works under native compilation; if it fails, the fallback (client-side
resizing) is chosen here, before the Listings change is built on the assumption.

## What Changes

- **New Ktor modular-monolith skeleton** (`rewear-api`) with the refined module
  decomposition (revises ADR-4's seam list): two domain Gradle modules —
  `users` (identity leaf) and `catalog` (listings, categories, favorites as
  internal packages) — plus `app` (composition root) and `common` (shared
  kernel). Boundaries enforced by the Gradle build graph + Kotlin `internal`
  visibility, with Konsist policing package boundaries within `catalog`. Admin
  is auth middleware, not a module. Modules are empty boundaries at this stage;
  capabilities land in later changes.
- **Native-friendly library baseline** per ADR-13: kotlinx.serialization (not
  Jackson), Ktor CIO engine, manual DI or Koin (no classpath scanning), a thin
  JDBC layer chosen for native compatibility (no Hibernate/JPA), and Flyway for
  migrations. Development and tests run on the JVM.
- **`/health` endpoint** reporting the build's git SHA (ADR-10).
- **Local dev stack** via `docker compose`: Postgres, Zitadel, and Garage
  (S3-API object storage for dev), so development works offline.
- **CI pipeline**: builds both the JVM dev image and the GraalVM native
  production image, pushes both to GHCR tagged `sha-<short>` (plus `latest`),
  and runs smoke tests against the native binary on every push to main
  (ADR-10, ADR-13). Includes deciding the GHCR private-package storage approach
  (public packages vs. a prune workflow) at repo creation.
- **ADR-13 native image-resize spike**: prove AWT/ImageIO resizing produces the
  fixed variants (ADR-12) under the native image. On failure, record the
  client-side-resizing fallback decision (revisit ADR-12/ADR-13).
- **Documentation**: keep README status and ROADMAP updated as scaffolding
  lands; record the spike outcome.

No product/domain behavior changes — this change ships infrastructure and seams,
not features.

## Capabilities

### New Capabilities

- `api-foundation`: The runnable API skeleton — application bootstrap, the ADR-4
  module seam structure, the native-friendly library baseline, and the
  `/health` endpoint (reports git SHA). Behavior that later modules extend.
- `local-dev-environment`: The `docker compose` dev stack (Postgres, Zitadel,
  Garage) and how the API is configured against it via env vars, so a developer
  can run the system locally and offline.
- `build-and-release-pipeline`: CI that builds the JVM dev image and the GraalVM
  native production image, publishes both to GHCR with the agreed tagging
  scheme, and smoke-tests the native binary on every commit.
- `native-image-resize-spike`: The ADR-13 validation that AWT/ImageIO image
  resizing works under native compilation, and the recorded decision (proceed
  with server-side resizing, or fall back to client-side).

### Modified Capabilities

<!-- None — no specs exist yet; this is the first change. -->

## Impact

- **New repository code**: the `rewear-api` Ktor project (Gradle build, module
  package structure, Flyway baseline, health route), a `docker-compose.yml` dev
  stack, and GitHub Actions workflows.
- **New infrastructure/accounts touched**: GHCR (image registry + package
  visibility decision). No production VPS, B2, or Brevo yet — those arrive with
  the "Production environment" change (roadmap item 2).
- **`rewear-proxy` image** (Caddy + baked SPA builds) is **out of scope** here:
  no SPAs exist yet. It arrives with the client changes; this change only
  establishes `rewear-api` and its pipeline.
- **Dependencies**: introduces the pinned third-party dev images (Postgres,
  Zitadel, Garage) and the JVM/GraalVM toolchain in CI.
- **ADRs exercised**: ADR-4, ADR-9, ADR-10, ADR-12, ADR-13 (spike may revisit
  ADR-12/13). No ADR is contradicted.
- **Docs**: README status section and ROADMAP updated on completion.
