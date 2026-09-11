## Why

The scaffolding change built the native image on **GraalVM community 21 / JDK 21**
with **native-build-tools 0.10.4**. That toolchain predates the unified
reachability-metadata format (introduced in GraalVM for JDK 23), so the tracing
agent emitted five separate `*-config.json` files, which we then wired in with
explicit `-H:*ConfigurationResources` build args. GraalVM 25 is the current LTS
(JDK 25, Sept 2025), the per-file format is deprecated (full migration lands at
JDK 25), and native-build-tools has moved to the 1.1.x line. Moving now keeps us
on a supported LTS, adopts the future-proof single-file metadata format, and
picks up GraalVM 25 native-image improvements (e.g. WP-SCCP → smaller binaries).
Low urgency: the current setup works and the deprecated format is still accepted,
so this is a deliberate, low-risk maintenance bump — not a fix.

## What Changes

- Bump the project toolchain **JDK 21 → 25**: `buildSrc` `jvmToolchain(21)` → `25`,
  and the JVM image base `eclipse-temurin:21-*` → `25-*` (`Dockerfile.jvm`).
- Bump the native builder image `ghcr.io/graalvm/native-image-community:21` → `:25`
  (`Dockerfile.native`).
- Bump **native-build-tools 0.10.4 → 1.1.12** in the version catalog.
- **Forced toolchain cascade** (discovered during implementation — see design
  Decision 6): moving to JDK 25 mandates **Gradle 8.11.1 → 9.7.1** (8.11.1 cannot run
  on JDK 25, and both Dockerfiles + CI run Gradle inside JDK-25-only environments) and
  **Kotlin 2.1.0 → 2.2.21** (2.1.0 cannot run on JDK 25). The Gradle 9 bump in turn
  forces **Ktor 3.0.3 → 3.5.2** (Ktor 3.0.3's bundled Shadow plugin breaks on Gradle 9).
  Bytecode target is **JVM 24, not 25** — no released Kotlin can emit JVM 25 bytecode
  yet; the toolchain runs on GraalVM 25 while compiling to JVM 24.
- **Regenerate native reachability metadata** with the JDK 25 tracing agent so it
  emits a single unified `reachability-metadata.json`, replacing the five split
  `reflect-config.json` / `resource-config.json` / `jni-config.json` /
  `proxy-config.json` / `serialization-config.json` files under
  `META-INF/native-image/com.rewear/`.
- **Simplify `graalvmNative` buildArgs**: drop the explicit
  `-H:ReflectionConfigurationResources` / `ResourceConfigurationResources` /
  `SerializationConfigurationResources` / `JNIConfigurationResources` args (the
  unified file is auto-detected from `META-INF/native-image/**`). Re-evaluate the
  `--initialize-at-build-time` list on the newer GraalVM.
- Keep the hand-maintained Flyway-plugin metadata safety net, migrated to the
  unified format (or confirm it is no longer needed under GraalVM 25).
- Re-run the native build + smoke test to confirm `/health`, migrations, and the
  ADR-12/ADR-13 resize path still pass on the new toolchain.

No product/domain behavior changes; no runtime API changes. This is a build and
packaging maintenance change.

## Capabilities

### New Capabilities

<!-- None. This change introduces no new behavior or endpoints. -->

### Modified Capabilities

<!-- None at the spec level. `build-and-release-pipeline` requires "build a GraalVM
     native production image" and "smoke-test the native binary" — both are
     version-agnostic, so no requirement text changes. This is a toolchain bump
     that keeps existing requirements satisfied by newer versions. The delta lives
     in design.md (the how) and tasks.md (the steps). -->

## Impact

- **Build/config**: `api/buildSrc/src/main/kotlin/rewear.kotlin-common.gradle.kts`,
  `api/app/build.gradle.kts` (`graalvmNative` block), `api/gradle/libs.versions.toml`,
  possibly `api/gradle/wrapper/` (Gradle version).
- **Images**: `api/Dockerfile.jvm`, `api/Dockerfile.native`.
- **Metadata**: `api/app/src/main/resources/META-INF/native-image/com.rewear/**`
  (five files → one unified file; `extra/` safety net migrated or removed).
- **CI**: `.github/workflows/ci.yml` — bump `actions/setup-java` to JDK 25.
- **Docs**: README tech-stack/notes and `docs/ROADMAP.md` follow-up entry.
- **Verification**: native build + smoke test must pass; regenerating metadata
  requires a tracing-agent run under JDK 25 against the dev stack.
- **Risk**: GraalVM 25 may surface new metadata gaps or behavior changes; the
  documented fallbacks (blocking JDBC per ADR-13; the per-file metadata format
  still works) bound the blast radius. Reverting is a version pin.
