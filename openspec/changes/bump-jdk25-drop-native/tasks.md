## 1. Reshape change artifacts and revisit ADRs

- [x] 1.1 Rename the change `bump-to-graalvm-25` → `bump-jdk25-drop-native` (folder + branch)
- [x] 1.2 Rewrite `proposal.md`, `design.md`, and the `build-and-release-pipeline` delta spec for the "keep JDK 25, drop native" intent
- [x] 1.3 Revisit **ADR-13** in `docs/ARCHITECTURE.md`: mark the native target *superseded*, record the reversal rationale (POC-scale friction + CI cost > memory saving), the JVM-with-tuned-heap target, the named memory trigger, and that the R2DBC→JDBC option is now free-standing
- [x] 1.4 Align **ADR-10**: `rewear-api` is a JVM image, not a native binary; relax the "native images are per-arch" note (still `linux/amd64` for the x86 VPS)

## 2. Remove native build configuration (`api/`)

- [x] 2.1 `app/build.gradle.kts`: delete the `graalvmNative { }` block and the `alias(libs.plugins.graalvm.native)` plugin
- [x] 2.2 `gradle/libs.versions.toml`: remove the `native-build-tools` version and the `graalvm-native` plugin entry
- [x] 2.3 Delete `api/Dockerfile.native` (`git rm`)
- [x] 2.4 `git rm` the reachability metadata under `app/src/main/resources/META-INF/native-image/com.rewear/` (both `app/` and `awt/` files)

## 3. Promote the JVM image to production

- [x] 3.1 `git mv api/Dockerfile.jvm api/Dockerfile`; update its header comment (no longer "dev image of two")
- [x] 3.2 Add `-XX:MaxRAMPercentage=75` to the entrypoint so the heap respects the container memory limit
- [x] 3.3 Confirm no other reference to `Dockerfile.jvm` / `Dockerfile.native` remains (CI, README, compose, scripts)

## 4. Simplify migration loading

- [x] 4.1 The native-only `COPY …/migrations` + `MIGRATIONS_LOCATION_PREFIX` lived in `Dockerfile.native` (now deleted); the production `Dockerfile` scans `classpath:db/migration` with no override
- [x] 4.2 Env-driven seam in `Migrations.kt` left intact (ADR-9); confirmed it defaults to `classpath:db/migration`

## 5. Update CI (`.github/workflows/ci.yml`)

- [x] 5.1 Remove the "Build GraalVM native production image" step
- [x] 5.2 Repoint the smoke test at the JVM image (boot, `/health` git SHA, migrations, `/internal/resize-check`)
- [x] 5.3 Build and publish a single `rewear-api` image tagged `sha-<short>` + `latest` on main; drop the separate `jvm-sha-*` tag
- [x] 5.4 Keep the PR-does-not-publish guard

## 6. Docs

- [x] 6.1 `README.md`: status section, tech-stack table (Backend row → JVM/JDK 25), and the "Image" section (one image, no native build)
- [x] 6.2 `docs/ROADMAP.md`: updated the Iteration-0 scaffolding note (JVM image), the "lift JVM target" note, and the CI path-filter follow-up (now lower urgency)

## 7. Verify

- [x] 7.1 `./gradlew --no-daemon build -x test` passes on JDK 25 (Temurin 25.0.3): compile + `jar` + `shadowJar` fat jar, no `nativeCompile` task. The 7 non-Docker tests pass (`HealthTest`, `ImageResizeSpikeTest`, `ResizeCheckRouteTest`, two Konsist boundary tests)
- [ ] 7.2 **Deferred to CI** — the Testcontainers `MigrationsIntegrationTest` and the end-to-end image smoke test both need Docker, unavailable in this sandbox. CI runs both on push/PR
- [x] 7.3 `openspec validate bump-jdk25-drop-native --type change` passes
