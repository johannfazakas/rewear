## Context

`project-scaffolding` stood the API up with a **GraalVM native image** as the
production target (ADR-13), for its memory footprint on a shared 4GB VPS. The
toolchain was then bumped to **GraalVM 25 / JDK 25** on this branch (commit
`2b1ec32`, never merged to `main`). That bump bundled two separable things:

1. A **JDK-25 toolchain cascade** — `jvmToolchain(25)`, Gradle 9.7.1, Kotlin
   2.2.21, Ktor 3.5.2, Temurin 25 for the JVM image. None of it is
   native-specific; it runs on a plain JVM.
2. The **native-image machinery** — a GraalVM builder image, a `graalvmNative`
   Gradle block, two auto-detected `reachability-metadata.json` files (one
   agent-owned, one a hand-maintained AWT/Java2D **JNI** net the tracing agent
   cannot derive), and a native-only Flyway filesystem-migrations workaround.

Implementing (2) made the native path's ongoing cost concrete: a
non-agent-derivable JNI net (the resize path crashed at `Toolkit.loadLibraries`
until it was hand-written), a ~5-minute `nativeCompile` on every push, and
library constraints kept purely for native compatibility. At POC scale that
friction is not worth the memory saving — so this change keeps (1) and removes
(2), reverting the production target to a JVM image. This revisits **ADR-13**.

Relevant decision records: ADR-13 (native image — being reversed here), ADR-10
(hosting / registry; `rewear-api` image), ADR-9 (containers-first, env-driven
config), ADR-12 (photo resize path, still exercised by the smoke test).

## Goals / Non-Goals

**Goals:**
- Keep the JDK-25 toolchain cascade already landed on this branch.
- Remove the GraalVM native production target entirely: builder image, Gradle
  plugin + `graalvmNative` block, both reachability-metadata files, and the
  native-only Flyway workaround.
- Make a single tuned JVM image the production artifact, used for dev and prod.
- Keep CI's end-to-end smoke test (now against the JVM image) and single-image
  publish to GHCR.
- Revisit ADR-13 and align ADR-10, README, and ROADMAP with reality.

**Non-Goals:**
- No product, domain, or runtime API behaviour changes.
- **No move off R2DBC.** ADR-13's revision named blocking JDBC + HikariCP as the
  fallback *"if the R2DBC native-image metadata proves intractable"* — that
  trigger is now void (no native image), but R2DBC runs fine on the JVM.
  Switching persistence is a separate ADR decision, explicitly out of scope; the
  ADR-13 revision only records that the option is now open on its own merits.
- No JDK/Gradle/Kotlin/Ktor version changes beyond what is already on the branch.
- No k3s / deployment-topology changes; prod stays Docker Compose (ADR-10).

## Decisions

**1. Keep the JDK-25 toolchain; drop only native.** The toolchain cascade
(`jvmToolchain(25)`, Gradle 9.7.1, Kotlin 2.2.21, Ktor 3.5.2, JVM-24 bytecode
target, Temurin 25) is independent of native-image and is a maintenance win on
its own. `buildSrc/.../rewear.kotlin-common.gradle.kts` is therefore left
untouched — the `JvmTarget.JVM_24` cap stays (no released Kotlin emits JVM 25
bytecode yet; it tracks the ROADMAP "lift 24 → 25" follow-up, unrelated to
native). *Alternative considered:* also revert the toolchain to the
pre-branch GraalVM-21/JDK-21 state — rejected; that throws away unrelated upside
and leaves us on an unsupported JDK.

**2. Reverse ADR-13: JVM with a tuned heap is the production target.** ADR-13
explicitly listed "plain JVM with a tuned small heap (works, ~2–3× the
footprint)" as the rejected-then alternative; we are now accepting it. The
driver is developer-friction and CI cost at POC scale, not a technical failure
of native-image — so the ADR is marked *superseded*, with the memory trade and a
named trigger to reconsider (see Risks). *Alternative considered:* keep native
but cut only the friction (CI path filters, native-only-on-release) — a real
option, but it keeps the JNI-net maintenance burden and the library constraints;
the owner chose the simpler mental model.

**3. One JVM image for dev and prod.** Rename `Dockerfile.jvm` → `Dockerfile`
(it is no longer "the dev one of two") and add `-XX:MaxRAMPercentage=75` to the
entrypoint so the heap respects the container's memory limit on the 4GB VPS.
Using the same image in dev and prod improves parity and satisfies ADR-10's
"one first-party `rewear-api` image" shape. *Alternative considered:* keep a
separate prod Dockerfile — rejected; there is now only one build path, so a
second file is pure duplication.

**4. Delete both reachability-metadata files.** With no native image, neither
the agent-owned `com.rewear/app/reachability-metadata.json` nor the
hand-maintained `com.rewear/awt/` JNI net has any consumer. Removing them is the
point of the change — it retires the maintenance tax that motivated it.

**5. Drop the native-only Flyway filesystem workaround; scan the classpath.**
The Dockerfile's `COPY …/db/migration …/migrations` lines plus
`MIGRATIONS_LOCATION_PREFIX=filesystem:/app/migrations` existed solely because
native images cannot scan classpath directories ("unsupported protocol:
resource"). On the JVM, `Migrations.kt` already defaults to
`classpath:db/migration`, so the production image simply stops setting the env
var and drops the `COPY` lines. The env-driven seam in `Migrations.kt` stays
(harmless, and env-driven config is ADR-9) — only the native-specific *use* of
it goes away.

**6. CI: single image, JVM smoke test, single publish.** Remove the "Build
GraalVM native production image" step and repoint the smoke test at the JVM
image (it still verifies boot, `/health` git SHA, migrations, and the ADR-12/13
resize path — the checks that matter, regardless of image kind). Publish one
`rewear-api` image tagged `sha-<short>` + `latest` on `main`. This removes the
~5-min `nativeCompile` from every push — the CI win motivating the change. The
previously separate `jvm-sha-*` tag collapses into the canonical `sha-*` tag.

**7. Keep R2DBC (record, don't act).** See Non-Goals. The ADR-13 revision's
JDBC-fallback trigger is retired; R2DBC stays on its own merits. The ADR note
records that a future move to JDBC is now a free-standing option, not forced by
native compatibility.

## Risks / Trade-offs

- **~2–3× memory footprint on the VPS** (~300–500MB RSS vs. ~50–150MB native) →
  Accepted at POC scale; ADR-10's CX22 has ~4GB with Zitadel the main tenant.
  **Named trigger:** if the VPS gets memory-tight (likely only with more tenants
  or k3s), revisit — either tune the heap harder or reconsider native then, when
  the toolchain is mature and the metadata story is simpler.
- **Slower cold start** (JVM boot vs. native ~50ms) → Irrelevant for a
  long-running server behind Caddy; only matters for scale-to-zero, which this
  deployment does not do.
- **No native image means native-only regressions can't be caught early** →
  There is nothing to catch: the native target is gone. If native is ever
  reintroduced, the CI native build returns with it.
- **Rollback is cheap** → Revert this change's commit to restore the native
  build; the toolchain bump is orthogonal and stays either way.

## Migration Plan

1. Reshape the OpenSpec change artifacts + delta spec (this change), and revisit
   ADR-13 / ADR-10 in `docs/ARCHITECTURE.md`.
2. Remove native build config: `graalvmNative` block + `graalvm.native` plugin
   (`app/build.gradle.kts`), `native-build-tools` + `graalvm-native`
   (`libs.versions.toml`), delete `Dockerfile.native`, `git rm` the metadata.
3. Promote the JVM image: rename `Dockerfile.jvm` → `Dockerfile`, add the heap
   cap, drop the `COPY …/migrations` lines and the `MIGRATIONS_LOCATION_PREFIX`
   env.
4. Update CI: drop the native build step; smoke-test the JVM image; publish one
   image.
5. Update `README.md` (status, tech-stack table, images section) and
   `docs/ROADMAP.md`.
6. Verify: `./gradlew build` on JDK 25; build the JVM image and run the
   CI-equivalent smoke test (`/health` git SHA, migrations, resize path).

**Rollback:** revert this change's commit — restores `Dockerfile.native`, the
`graalvmNative` config, and the metadata. The JDK-25 toolchain is unaffected.

## Open Questions

- None. R2DBC-vs-JDBC is deliberately deferred to a future standalone decision
  (Decision 7 / Non-Goals), not an open question for this change.
