## 1. Toolchain version bumps (forced cascade, `api/gradle/libs.versions.toml` + wrapper)

- [x] 1.1 `native-build-tools` `0.10.4` → `1.1.12`
- [x] 1.2 Gradle wrapper `8.11.1` → `9.7.1` — **mandatory**: Gradle 8.11.1 cannot run on JDK 25 (bundled Kotlin DSL `JavaVersion.parse` throws `IllegalArgumentException: 25`), and both Dockerfiles + CI run Gradle inside JDK-25-only environments
- [x] 1.3 Kotlin `2.1.0` → `2.2.21` — **mandatory**: 2.1.0 cannot run on JDK 25; 2.2.20+ adds JDK 25 support
- [x] 1.4 Ktor `3.0.3` → `3.5.2` — forced by Gradle 9: Ktor 3.0.3's bundled Shadow plugin references the removed `mainClassName` property (`startShadowScripts` fails); 3.5.x uses the maintained `com.gradleup.shadow`
- [x] 1.5 Confirm resolution: `./gradlew build` resolves native-build-tools 1.1.12 on Gradle 9.7.1 with Kotlin 2.2.21 (resolves design Open Q1 & Q3)

## 2. Bump JDK 21 → 25

- [x] 2.1 In `buildSrc/src/main/kotlin/rewear.kotlin-common.gradle.kts`: `jvmToolchain(21)` → `25`; `JvmTarget.JVM_21` → `JVM_24` (JVM_25 does not exist in any released Kotlin — max is JVM_24); add `java { source/targetCompatibility = VERSION_24 }` so Kotlin's JVM-target validation passes (toolchain runs on 25, bytecode targets 24)
- [x] 2.2 In `api/Dockerfile.jvm`, bump `eclipse-temurin:21-jdk` → `25-jdk` and `eclipse-temurin:21-jre` → `25-jre`
- [x] 2.3 In `api/Dockerfile.native`, bump `ghcr.io/graalvm/native-image-community:21` → `:25`
- [x] 2.4 In `.github/workflows/ci.yml`, bump `actions/setup-java` Java version to `25`
- [x] 2.5 `./gradlew build` on GraalVM CE 25 (local, via sdkman `25-graalce`): compile + assemble + all 7 tests pass, 0 skipped — incl. the Testcontainers `MigrationsIntegrationTest` and the ADR-12/13 `ImageResizeSpikeTest`

## 3. Regenerate reachability metadata (unified format)

- [x] 3.1 Dev stack for the agent run: used Testcontainers (self-managed Postgres) driven by the test suite, rather than the full `docker compose` stack — the tests exercise the same paths the agent needs to trace
- [x] 3.2 Ran the GraalVM 25 tracing agent via native-build-tools `./gradlew -Pagent test`, exercising `/health`, Flyway migrations, and the ADR-12/13 resize — produced a single unified `reachability-metadata.json` (717 reflection + 61 resource entries)
- [x] 3.3 Replaced the five split files under `META-INF/native-image/com.rewear/app/` with the unified `reachability-metadata.json`; `git rm`'d the old files
- [x] 3.4 Flyway `extra/` safety net: **removed** — the agent run now exercises migrations, and a static check confirmed every real class from `extra/reflect-config.json` is covered by the unified file (the one uncovered entry was a corrupt concatenated class name). Runtime-confirmed by the native smoke test
- [x] 3.5 **AWT/Java2D JNI net** (discovered via the resize smoke crash at `Toolkit.loadLibraries`): the JVM agent cannot capture native-image JNI metadata, so the 54 `jni-config.json` entries are carried over — converted to the unified schema — into a hand-maintained `com.rewear/awt/reachability-metadata.json` (auto-detected, never overwritten by regen). See design Decision 8
- [x] 3.6 Added `ResizeCheckRouteTest` (mirrors `HealthTest`) so the agent traces `/internal/resize-check` serialization — its `ResizeCheckResponse` serializer was silently missing because no test exercised that endpoint. Re-added the `System$LoggerFinder` resource the agent doesn't trace

## 4. Simplify the graalvmNative build config

- [x] 4.1 Removed the four `-H:*ConfigurationResources` `buildArgs.add(...)` lines and the explaining comment (unified metadata now auto-detected from `META-INF/native-image/**`)
- [x] 4.2 `--initialize-at-build-time` list: kept unchanged as the starting point; native-image generation reached the C-compile stage without complaint about it (no adjustment required)
- [x] 4.3 Retained `--no-fallback`, `-Djava.awt.headless=true`, and `-H:+ReportExceptionStackTraces`

## 5. Verify native build + smoke test

- [x] 5.1 Native image built on GraalVM CE 25 via `Dockerfile.native` (the production path on Linux). NB: local macOS `:app:nativeCompile` is blocked by a beta `MacOSX27.0.sdk`/clang bug ("unknown architecture") unrelated to this change; the Docker/Linux build is the authoritative path
- [x] 5.2 Native smoke test (CI-equivalent: Postgres + image on a docker network) passed — `GET /health` → 200 with git SHA, Flyway migrations applied on boot, `GET /internal/resize-check` → 200 `{"ok":true, variants:[thumbnail 150×113, detail 800×600]}`
- [x] 5.3 Confirmed the build defines no `-H:*ConfigurationResources` flags; both unified files are loaded via `META-INF/native-image/**` auto-detection (satisfies the spec's unified-metadata requirement)
- [x] 5.4 Flyway `extra/` net removed and confirmed by the green migration smoke test; the AWT JNI net (3.5) was the actually-required hand-maintained supplement

## 6. Docs and follow-up

- [x] 6.1 Update `README.md` tech-stack table / notes to reflect JDK 25 / GraalVM 25 and native-build-tools 1.1.x (prerequisites line; tech-stack table carries no JDK version)
- [x] 6.2 Updated `docs/ROADMAP.md`: recorded the delivered bump and replaced the deferred item with the real follow-up (lift JVM target 24 → 25 when Kotlin supports it)
- [x] 6.3 `openspec validate bump-to-graalvm-25 --type change` passes
