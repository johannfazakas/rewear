# rewear

A community marketplace for second-hand clothes — upload your pieces, browse
what others offer, favorite what you love. Built as a small business run by a
single admin, starting as a proof of concept.

## Status

**Scaffolding landed.** The runnable Ktor modular-monolith skeleton exists
under [`api/`](api/): the `common` / `users` / `catalog` / `app` module seams
with compiler- and Konsist-enforced boundaries, a `GET /health` endpoint
reporting the build's git SHA, R2DBC + per-module Flyway migrations, a local
`docker compose` dev stack (Postgres, Zitadel, Garage), Dockerfiles for the JVM
dev image and the GraalVM native production image, and a CI pipeline that builds
both images and smoke-tests the native binary. The ADR-13 photo-resize spike is
**confirmed under the native image** (boots ~2 s, ~35 MB RSS), so server-side
resizing is the path forward. No product features yet — the modules are empty
boundaries. Next up: the "Production environment" change on the
[roadmap](docs/ROADMAP.md).

## What it will do (iteration 1)

- Members sign in (social login or email magic link) and upload listings:
  photos, title, description, category, size
- Browse, search, favorite; view your favorites list
- Admin manages categories, removes listings, blocks users
- No payments or member-to-member contact yet — deliberately deferred

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Kotlin + Ktor (CIO), modular monolith, GraalVM native image |
| Build | Gradle (Kotlin DSL), `buildSrc` convention plugins, version catalog |
| DI / serialization | Koin (no classpath scanning), kotlinx.serialization |
| Persistence | Exposed R2DBC (`r2dbc-postgresql` + pool) on the request path; Flyway (pgJDBC) for migrations, per-module history tables |
| Arch enforcement | Kotlin `internal` visibility + Konsist package/layer rules |
| Web apps (user + admin) | React + TypeScript |
| Mobile | Kotlin Multiplatform + Compose (Android first) |
| Database | PostgreSQL |
| Photo storage | Backblaze B2 (prod) / Garage (dev), S3 API |
| Auth | Zitadel (self-hosted OIDC) |
| Registry / CI | GHCR (`sha-<short>` + `latest`, public package), GitHub Actions |
| Hosting | Hetzner VPS, Docker Compose, Caddy — k3s planned later |

Every component is a container speaking standard protocols (OIDC, SQL, S3,
SMTP), deployable to a cheap VPS, Kubernetes, or local servers unchanged.

## Running locally

Prerequisites: Docker, and a JDK 25 (the API uses the Gradle wrapper).

```bash
# 1. Start the dev stack (Postgres, Zitadel, Garage) — third-party images pinned.
docker compose up -d

# 2. Provision the Garage bucket + dev S3 key (one-time; safe to re-run).
dev/garage-init.sh

# 3. Configure the API and run it against the stack.
cp .env.example .env         # dev values already filled in
set -a && . ./.env && set +a
cd api && ./gradlew :app:run

# 4. Verify.
curl localhost:8080/health              # {"status":"UP","gitSha":"dev"}
curl localhost:8080/internal/resize-check
```

The API reads all configuration from environment variables (ADR-9), so the same
build runs against the dev stack or production backends by changing env only.

### Images

```bash
# JVM dev image
docker build -f api/Dockerfile.jvm  --build-arg GIT_SHA=$(git rev-parse --short HEAD) -t rewear-api:jvm api
# GraalVM native production image
docker build -f api/Dockerfile.native --build-arg GIT_SHA=$(git rev-parse --short HEAD) -t rewear-api:native api
```

CI builds both on every push/PR, smoke-tests the native binary (`/health` +
resize path), and on `main` publishes them to GHCR tagged `sha-<short>` and
`latest`. The `rewear-api` GHCR package is made **public** once (repo → Packages →
Package settings → Change visibility) so pulls need no auth and the 500 MB
private-storage cap does not apply (ADR-10).

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — C4 diagrams and all decision records
  with rationale
- [Roadmap](docs/ROADMAP.md) — planned changes, deferred features, decision
  triggers
- [Domain & hosting setup](docs/guides/domain-and-hosting-setup.md) — one-time
  manual infrastructure steps

## Development workflow

Work happens as incremental, spec-driven changes managed with
[OpenSpec](https://github.com/Fission-AI/OpenSpec) (see `openspec/`). Each
change must conform to the architecture decision records or explicitly revisit
them.
