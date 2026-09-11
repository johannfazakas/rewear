## Context

rewear is fully designed (see `docs/ARCHITECTURE.md`) but has no code. This
change creates the runnable Ktor modular-monolith skeleton every later change
lands in, the local dev stack, and a CI pipeline that builds and smoke-tests the
GraalVM native production image on every commit. It also runs the ADR-13 spike
that de-risks photo resizing under native compilation before Listings depends on
it.

Binding constraints from the ADRs:

- **ADR-4** — one deployable, with *enforced* internal boundaries between the
  `listings`, `favorites`, `catalog`, and `admin/moderation` seams (plus a
  `users`/identity glue seam).
- **ADR-13** — production is a GraalVM native image, so every library choice
  must be native-friendly: kotlinx.serialization (not Jackson), Ktor CIO engine,
  no classpath-scanning DI, no Hibernate/JPA, Flyway for migrations. Dev and
  tests run on the JVM; CI compiles and smoke-tests the native binary.
- **ADR-9 / ADR-10** — every component is an OCI container configured by env
  vars, standard protocols only; images live on GHCR, tagged `sha-<short>`.
- **ADR-12** — photos are resized into fixed variants (thumbnail, detail) by the
  API at upload time using a JVM image library.

This change ships *infrastructure and seams*, not features — the modules are
empty boundaries at this stage.

## Goals / Non-Goals

**Goals:**

- A `rewear-api` Ktor app that boots, connects to Postgres, and serves a
  `/health` endpoint reporting the build's git SHA.
- The ADR-4 seams established as Gradle subprojects with compiler-enforced
  boundaries and a place for Koin to wire them together.
- A native-friendly library baseline (Exposed, Koin, CIO, kotlinx.serialization,
  Flyway) proven to compile and run as a GraalVM native image in CI.
- A `docker compose` dev stack (Postgres, Zitadel, Garage) so development works
  locally and offline.
- CI that builds the JVM dev image and the GraalVM native prod image, pushes
  both to GHCR, and smoke-tests the native binary on every push to main.
- A recorded outcome for the ADR-13 photo-resize spike (proceed server-side, or
  fall back to client-side).

**Non-Goals:**

- No product/domain behavior: no listings, favorites, categories, auth
  enforcement, or photo upload endpoints yet. Modules contain only wiring.
- No `rewear-proxy` image and no React apps — no SPAs exist yet; they arrive
  with the client changes.
- No production infrastructure (VPS, Caddy, B2, Brevo, real Zitadel config,
  deploy pipeline) — that is roadmap item 2, "Production environment".
- No scheduled backups (ADR-11 defers these to the k3s stage).
- No Zitadel JWT validation wired into routes yet — Zitadel runs in the dev
  stack so it is available, but the auth integration is a later change (ADR-8).

## Decisions

### D1: Module decomposition and boundary enforcement

**This decision revisits ADR-4's seam list** (see the ADR-4 revision note in
`docs/ARCHITECTURE.md`). The original four seams were finer than iteration-1
responsibilities justify.

Gradle subprojects:

- `common` — shared kernel: ID value types, error/result types. Deliberately
  thin; every module may depend on it.
- `users` — identity: the local `users` table, JIT provisioning from the JWT
  `sub`, profile, blocked flag. The domain **leaf** — depends on nothing in the
  domain. Exposes its contract (e.g. `UserQueries`, DTOs) as `public`;
  everything else is `internal`.
- `catalog` — the domain core: `listings`, `categories`, and `favorites` as
  internal packages. Depends on `users` + `common`.
- `app` — the sole composition root: depends on `users` and `catalog`, boots the
  Ktor CIO server, runs Flyway, and aggregates the Koin modules. Compiles to the
  native image.

**Boundary enforcement is two-layered:**

- *Between* Gradle modules — the build graph plus **Kotlin `internal`
  visibility**. Because `internal` is per-Gradle-module, `catalog` sees only
  `users`' `public` contract, never its internals; a cross-module cyclic
  dependency is rejected by Gradle. No separate `-api`/`-service` contract
  modules are needed — `internal` delivers the encapsulation the split would
  have, more simply.
- *Within* `catalog` — **Konsist** tests enforce that the `listings`,
  `categories`, and `favorites` packages do not reach into each other's
  internals, plus cross-cutting rules (naming, layering, "reference other
  domains by ID only").

Cross-module collaboration is ordinary in-process Kotlin calls through `public`
interfaces, wired by Koin (D3) — no HTTP or serialization between modules.
*Admin/moderation is not a module*: admin operations are role-guarded endpoints
inside the owning module; the `admin` role check is shared auth middleware
(lands with the auth change, out of scope here). *Favorites* is a package inside
`catalog`, promotable to its own module later if it earns it.

Gradle convention plugins in `buildSrc` centralize Kotlin/dependency setup so
each subproject's `build.gradle.kts` stays a few lines.

**Why:** two coarse modules match iteration-1 cohesion — `users` is a clean
leaf worth a compile-enforced boundary, while listings/categories/favorites are
one tightly-cross-referencing domain that finer splits would burden with
ceremony. `internal` + Konsist gives compile-time safety where it matters and a
test-based net for intra-`catalog` structure. **Rejected:** the original
four-seam `-api`/`-service` layout (ceremony for this scale; `internal` already
encapsulates); fully single-module + Konsist only (loses the compile-time
guarantee on the `users` leaf — the one boundary most worth hard-enforcing);
a standalone `admin` module (it owns no data).

### D2: Persistence — Exposed (R2DBC) for the request path, Flyway (JDBC) for migrations

**This decision revisits ADR-13**, which specifies blocking JDBC. See the ADR-13
revision note in `docs/ARCHITECTURE.md`.

Runtime data access uses JetBrains **Exposed** via its **R2DBC** module (SQL
DSL, not the DAO/entity layer that would edge toward JPA semantics), over
`r2dbc-postgresql` with `r2dbc-pool` for pooling. Query paths are non-blocking
end-to-end with Ktor's coroutines — no blocking JDBC on request threads and no
`Dispatchers.IO` offloading needed for the request path.

Schema is owned by **Flyway**, which is JDBC-only. Migrations run at startup over
a **plain pgJDBC connection** opened for that purpose and closed once complete
(ownership and orchestration detailed in D9). So pgJDBC remains a dependency but
executes only at boot, never on the request path; the runtime path is R2DBC
exclusively. Exposed maps queries only and never generates or mutates schema.

**Why:** chosen deliberately as a learning experiment with the reactive stack on
this playground — the value is exercising R2DBC and its GraalVM metadata story
first-hand, not a scale need (rewear's tens-to-hundreds of users, ADR-6, do not
require non-blocking DB access). The container/portability rules (ADR-9) make
reverting to blocking JDBC cheap if it hits a wall, so the risk is bounded.
**Rejected / fallback:** blocking **JDBC + HikariCP + `Dispatchers.IO`** — the
mature, lower-setup, more native-friendly path and the original ADR-13 choice;
it is the documented fallback if R2DBC's native-image metadata proves
intractable. Also rejected: plain JDBC + hand-rolled mappers; jOOQ (codegen,
heavier); Exposed DAO/entities (reflection-heavier, drifts toward ORM).

**Native consequence:** R2DBC pulls in Netty + Reactor. All three are known to
work under GraalVM native images (Spring/Quarkus ship it), but without an
AOT framework we supply the reachability metadata ourselves — via the GraalVM
reachability-metadata repository plus the tracing agent where needed. This is
the main native-image unknown in this change (alongside the D5 image-resize
spike); D6 compiling and smoke-testing the native image every commit is exactly
what surfaces missing metadata immediately. `exposed-r2dbc` is also comparatively
new, so its maturity is part of what this experiment probes.

### D3: Dependency injection — Koin

Modules are wired with **Koin**. Each domain module (`users`, `catalog`) exposes
a Koin module declaring its bindings (its `public` interface → its `internal`
implementation); `app` aggregates them at startup. No classpath scanning is used
(ADR-13).

**Why:** Koin gives clean, explicit wiring with a natural per-module structure
that matches D1, no reflection-based classpath scanning, and is a worthwhile
thing to exercise on this playground. **Rejected:** manual constructor DI in a
composition root — the most native-safe and zero-magic option, viable at this
scale, but chosen against in favor of Koin's ergonomics as the module count
grows. **Native consequence:** Koin's startup wiring is validated by the same
native smoke test as everything else.

### D4: HTTP + serialization — Ktor CIO engine, kotlinx.serialization

Ktor server on the **CIO** engine (coroutine-based, no Netty/servlet stack) with
**kotlinx.serialization** for JSON via the ContentNegotiation plugin. Both are
mandated by ADR-13 and set here as the baseline for all future routes.

### D5: Photo resizing — Thumbnailator/ImageIO, validated by the ADR-13 spike

Server-side resizing (ADR-12) is prototyped with a small routine that reads a
sample photo and produces the fixed variants using `javax.imageio` (via
Thumbnailator for ergonomics). The spike's job is not the final resize API —
it is to answer one question: **does AWT/ImageIO-based resizing work inside the
GraalVM native image?**

Spike method: a minimal code path exercising ImageIO decode → resize → encode,
invoked by the native smoke test in CI against a real sample image, asserting the
output variant is a valid image of the expected dimensions. Success → server-
side resizing is confirmed and the real resize logic is built in the Listings
change. Failure (missing native config that cannot be reasonably supplied, or
`java.awt.headless`/font/JNI breakage) → record the fallback: clients resize
before upload, and revisit ADR-12/ADR-13.

**Why in scaffolding:** ADR-13 names this the weakest area of native-image
support; proving it now prevents Listings from being built on a false premise.

**Spike outcome (recorded during implementation): server-side resizing is
CONFIRMED under the GraalVM native image.** The resize path was verified both on
the JVM (unit test + running app) and in the compiled native production image,
producing the fixed variants from the 1200×900 sample (thumbnail 150×113, detail
800×600) via `GET /internal/resize-check`. The native image runs headless
(`java.awt.headless=true`); making AWT/ImageIO work under native required:
(1) generating reachability metadata with the GraalVM tracing agent (it captures
the ImageIO SPI service files and reflection), (2) **shipping the JDK AWT shared
libraries** (`libawt.so`, `libawt_headless.so`, `libjavajpeg.so`, `liblcms.so`,
`libfontmanager.so`) next to the native binary — GraalVM emits them beside the
executable and ImageIO loads them relative to it, and (3) `libfreetype6` +
`fontconfig` in the runtime base image. The native image boots in ~2 s at ~35 MB
RSS. Server-side resizing is therefore the confirmed approach for the Listings
change; the client-side fallback is not needed. The CI native smoke test guards
against regressions on every commit.

### D6: CI — build both images, push to GHCR, smoke-test the native binary

GitHub Actions on push to main:

1. JVM build + unit tests (fast feedback).
2. Build the **JVM dev image** (`rewear-api` dev flavor) for local/dev use.
3. Build the **GraalVM native image** and package the **prod image** (native
   binary on a minimal base).
4. **Native smoke test**: run the prod image, hit `/health` (expect 200 + git
   SHA), and exercise the D5 resize path against a sample image.
5. Push both images to GHCR tagged `sha-<short>` (immutable) plus `latest`
   (convenience), `linux/amd64` only (ADR-10).

`/health` reports the git SHA (ADR-10) — injected at build time as an env var /
build arg and surfaced by the endpoint.

**GHCR package visibility:** GHCR's free private-package storage is limited
(500MB). Decision: **make the `rewear-api` package public** (this is an
open-source-friendly project and public packages have no storage cap), avoiding
a prune workflow. Recorded here per ADR-10's "decide at repo creation" note.

**Why:** builds the native binary every commit so native-only breakage (D2/D3/D5
being the likely culprits) surfaces immediately, and establishes the exact
tagging/registry mechanics ADR-10 specifies so the later "Production
environment" change only has to add the deploy target.

### D7: Local dev stack — docker compose (Postgres, Zitadel, Garage)

A `docker-compose.yml` brings up pinned Postgres, Zitadel, and Garage
containers. The API (run from the IDE/JVM against this stack, or as the JVM dev
image) is configured entirely by env vars (ADR-9): JDBC URL/credentials, S3
endpoint/credentials/bucket (Garage), and Zitadel issuer/JWKS URL. Zitadel is
present so it is available for the later auth change; this change does not wire
JWT validation into routes.

**Why:** ADR-9's env-var/standard-protocol rule means the same API image runs
against dev (Garage) or prod (B2) with no code change; the dev/prod storage
hybrid (ADR-7) proves the portability rule continuously.

### D8: Testing — JUnit 5 + Kotlin test, given-when-then names

Unit and integration tests run on the JVM (JUnit 5). Integration tests that need
Postgres/S3 use Testcontainers where useful. Test names follow the project's
given-when-then convention. The native smoke test (D6) is separate and runs
against the compiled binary in CI.

### D9: Cross-module data ownership and migrations

Each module owns its own tables (`users` owns `users`; `catalog` owns
`listings`, `categories`, `favorites`). A module references data owned by
another module by **plain ID column only** — e.g. `favorites.listing_id` and
`favorites.user_id`, `listings.owner_id`, `listings.category_id` are plain
indexed columns, **not cross-module foreign keys**. Foreign keys *within* a
module's own tables are normal.

A module never reads another module's tables (no cross-module SQL JOIN); to get
another module's data it calls that module's `public` interface in-process
(e.g. `catalog` calls `users`' `UserQueries`). Referential integrity across
modules is application logic: existence is checked via the owning module's
interface before a write, reads tolerate orphans (a favorite whose listing was
removed is filtered out when the listing lookup returns nothing), and cleanup on
delete happens via a call or domain event — none of which is implemented in this
change (no features yet), but the pattern is fixed here.

Migrations: each module **owns its Flyway SQL migration files** (under its own
resources path) and has its **own Flyway history table** (e.g.
`flyway_schema_history_users`, `flyway_schema_history_catalog`), so each module's
migration versioning is fully independent — no shared version sequence to
coordinate. `app` **orchestrates** the runs (it does not own the scripts): at
startup it runs Flyway once per module, each pointed at that module's location
and history table, over the plain pgJDBC connection (D2). Because D9 forbids
cross-module foreign keys, the modules' schemas are independent, so per-module
histories are safe and migration order across modules does not matter.

When the app later runs as multiple replicas (k3s era, ADR-10), migrations
should move out of app startup into a pre-deploy step (Job / init container) so
replicas don't race and the app process needn't hold DDL privileges at runtime.
Startup migration is correct for the single-replica compose stack now.

**Why:** keeps the ADR-4 boundaries real below the code layer — modules can
evolve their own schema without a cross-module migration breaking a neighbor,
and the seams stay extractable. **Rejected:** cross-module FK constraints (real
integrity and cascades for free, but couples module schemas so they can no
longer evolve independently — the coupling ADR-4 avoids).

## Risks / Trade-offs

- **R2DBC stack (Netty + Reactor + `r2dbc-postgresql`) needs GraalVM
  reachability metadata, supplied by hand without an AOT framework** → CI builds
  the native image every commit (D6), so missing metadata fails fast, in this
  change, with a clear signal — not deep in a feature change. If it proves
  intractable, the documented fallback is blocking JDBC + HikariCP +
  `Dispatchers.IO` (an ADR-13 re-revisit back to the original choice).
- **`exposed-r2dbc` is comparatively new** → confined to the request path;
  Flyway/pgJDBC (mature) still owns schema at boot, bounding the blast radius.
- **ImageIO may not work under native (the ADR-13 risk)** → D5 spike is
  explicitly designed to catch this now; a documented client-side fallback
  exists, so a spike failure is a planned branch, not a blocker.
- **Multi-module adds Gradle scaffolding up front** → `buildSrc` convention
  plugins absorb most of it; the cost buys compiler-enforced ADR-4 boundaries
  that are painful to retrofit later.
- **Native image build is slow (minutes) and memory-hungry in CI** → keep it on
  the main-branch/PR path with caching; JVM unit tests give fast local feedback,
  native build is the gate not the inner loop.
- **Koin wiring errors are runtime, not compile-time** → the native smoke test
  boots the full graph, so a broken binding fails CI immediately.
- **Public GHCR package exposes image contents** → acceptable: no secrets are
  baked into images (all config is env vars per ADR-9); avoids the 500MB private
  cap and a prune workflow.

## Migration Plan

Greenfield — no existing system, data, or consumers, so no runtime migration or
rollback strategy applies to this change. Deploy of the resulting images to a
real environment is out of scope (roadmap item 2). "Rollback" during development
is ordinary git revert; images are immutable per-SHA so any commit's artifacts
are reproducible.

On completion, update `README.md` (status section → "scaffolding landed") and
`docs/ROADMAP.md` (mark Iteration 0 item 1, note the spike outcome).

## Open Questions

- **Spike outcome** — server-side vs. client-side resizing is resolved *by
  running this change*, not before it; the specs/tasks must accommodate either
  result.
- **Exact Postgres / Zitadel / Garage image versions** to pin — chosen during
  implementation against current stable releases.
- **Number of native-image builds** — every push vs. PRs only vs. main-only —
  tuned once real CI runtimes are observed; default is push-to-main + PRs.
- **`common` module contents** — kept minimal; grows only as concrete shared
  needs appear rather than being designed up front.
- **Konsist rule set for `catalog`** — the exact intra-`catalog` package rules
  (and any cross-cutting naming/layering rules) are refined during
  implementation; the baseline is "packages do not touch each other's
  internals" and "reference other domains by ID only".
- **When favorites earns its own module** — left as a judgment call for a later
  change (e.g. if member-interaction features cluster there); a package boundary
  now keeps that promotion cheap.
