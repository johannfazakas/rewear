# Tasks — project-scaffolding

Ordered by dependency. Test names follow given-when-then; no explanatory
comments in code. `git add` new/removed files as they are created.

## 1. Build foundation

- [ ] 1.1 Initialise the Gradle (Kotlin DSL) multi-project build: root
  `settings.gradle.kts` declaring subprojects `common`, `users`, `catalog`,
  `app`; Gradle wrapper committed
- [ ] 1.2 Add `buildSrc` with convention plugins centralising the Kotlin JVM
  toolchain, common compiler options, and shared test dependencies so each
  subproject's `build.gradle.kts` stays minimal
- [ ] 1.3 Pin dependency versions in a version catalog (`gradle/libs.versions.toml`):
  Ktor CIO, kotlinx.serialization, Koin, Exposed (R2DBC), `r2dbc-postgresql`,
  `r2dbc-pool`, pgJDBC, Flyway, Thumbnailator, Konsist, JUnit 5, Testcontainers
- [ ] 1.4 Create the `common` module: ID value types, error/result types, shared
  kernel; keep it dependency-light

## 2. Module skeletons and boundaries

- [ ] 2.1 Create the `users` module: `public` contract surface (e.g.
  `UserQueries` interface + DTOs) with implementation types marked `internal`;
  its own Koin module; depends on `common`
- [ ] 2.2 Create the `catalog` module with internal packages `listings`,
  `categories`, `favorites`; `public` surface minimal, implementation `internal`;
  its own Koin module; depends on `common` and `users`
- [ ] 2.3 Add a Konsist architecture test module/source set enforcing: packages
  within `catalog` do not touch each other's internals; other domains are
  referenced by ID only; agreed naming/layering rules
- [ ] 2.4 Write a boundary test proving a cross-module cyclic dependency and an
  `internal`-visibility violation fail the build (given-when-then)

## 3. App composition root

- [ ] 3.1 Create the `app` module depending on `users` and `catalog`; Ktor CIO
  server bootstrap; install ContentNegotiation with kotlinx.serialization
- [ ] 3.2 Load all configuration from environment variables (DB, S3/Garage,
  Zitadel issuer/JWKS); fail fast with a message naming any missing required var
- [ ] 3.3 Wire Koin at startup aggregating each module's Koin module; a missing/
  ambiguous binding fails startup with a diagnostic
- [ ] 3.4 Implement `GET /health` (unauthenticated) returning 200 with the build
  git SHA; inject the SHA at build time as a build arg / env var
- [ ] 3.5 Test (given-when-then): given a running app, when GET /health, then 200
  and body contains the git SHA

## 4. Persistence and migrations

- [ ] 4.1 Configure the R2DBC connection pool (`r2dbc-postgresql` + `r2dbc-pool`)
  and Exposed R2DBC; expose a suspend query helper for the request path
- [ ] 4.2 Give `users` and `catalog` each their own Flyway migration location and
  a distinct history table (`flyway_schema_history_users`, `..._catalog`); add a
  no-op/baseline migration per module to prove the pipeline
- [ ] 4.3 Orchestrate migrations in `app`: at startup run Flyway once per module
  over a plain pgJDBC connection opened for migration only, then closed
- [ ] 4.4 Integration test (Testcontainers Postgres, given-when-then): given the
  app boots against an empty DB, when startup completes, then both per-module
  history tables exist and a trivial R2DBC round-trip query succeeds

## 5. Image-resize spike (ADR-13)

- [ ] 5.1 Add a minimal resize routine (Thumbnailator/ImageIO) that decodes a
  sample photo and produces the fixed variants (thumbnail + detail), re-encoding
  each; include a sample image test asset
- [ ] 5.2 JVM unit test (given-when-then): given the sample photo, when resized,
  then variants are valid images of the expected dimensions
- [ ] 5.3 Expose the spike so the native smoke test (task 8.x) can invoke it
  against the sample image

## 6. Local dev stack

- [ ] 6.1 Write `docker-compose.yml` with version-pinned Postgres, Zitadel, and
  Garage; healthchecks; no cloud credentials required
- [ ] 6.2 Provide example env config wiring the app to the dev stack (DB, Garage
  S3 endpoint/bucket/credentials, Zitadel issuer/JWKS)
- [ ] 6.3 Document the run steps (compose up, env, start app) in the README
- [ ] 6.4 Verify end-to-end locally: stack up, app boots, migrations apply,
  `/health` returns 200, Garage S3 endpoint reachable

## 7. Containerisation

- [ ] 7.1 JVM dev image: Dockerfile building the `app` fat/dev artifact on a JRE
  base, config via env vars
- [ ] 7.2 GraalVM native image: configure the native-image build (Gradle
  native-build-tools plugin), package the native binary on a minimal base,
  `linux/amd64`
- [ ] 7.3 Supply GraalVM reachability metadata for R2DBC/Netty/Reactor, Exposed,
  the JDBC driver, and Koin; use the tracing agent where needed
- [ ] 7.4 Confirm the native image boots locally against the dev stack and serves
  `/health`

## 8. CI/CD pipeline

- [ ] 8.1 GitHub Actions workflow: on push to main and PRs, JVM build + unit
  tests (fast feedback)
- [ ] 8.2 Build both images from the same commit; native build failure fails the
  pipeline
- [ ] 8.3 Native smoke test: run the prod image, assert `GET /health` returns 200
  with the correct git SHA, and the resize path succeeds against the sample image
- [ ] 8.4 On push to main only, after the smoke test passes, push both images to
  GHCR tagged `sha-<short>` and `latest`; PR builds do not publish
- [ ] 8.5 Make the `rewear-api` GHCR package public (avoids the private-package
  storage limit)
- [ ] 8.6 Run the Konsist architecture tests in CI

## 9. Documentation and change closeout

- [ ] 9.1 Update `README.md` status section (scaffolding landed; how to run
  locally) and the tech-stack notes
- [ ] 9.2 Record the resize-spike outcome (server-side confirmed, or client-side
  fallback with ADR-12/ADR-13 revisited) in the design doc / README
- [ ] 9.3 Update `docs/ROADMAP.md`: mark Iteration 0 item 1 done; note any
  follow-ups surfaced
- [ ] 9.4 Verify `openspec validate project-scaffolding` passes and the change is
  ready to archive
