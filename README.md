# rewear

A community marketplace for second-hand clothes — upload your pieces, browse
what others offer, favorite what you love. Built as a small business run by a
single admin, starting as a proof of concept.

## Status

**Architecture phase — no code yet.** The system is fully designed and
documented; implementation starts with the scaffolding change on the
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
| Backend | Kotlin + Ktor modular monolith, GraalVM native image |
| Web apps (user + admin) | React + TypeScript |
| Mobile | Kotlin Multiplatform + Compose (Android first) |
| Database | PostgreSQL |
| Photo storage | Backblaze B2 (prod) / Garage (dev), S3 API |
| Auth | Zitadel (self-hosted OIDC) |
| Hosting | Hetzner VPS, Docker Compose, Caddy — k3s planned later |

Every component is a container speaking standard protocols (OIDC, SQL, S3,
SMTP), deployable to a cheap VPS, Kubernetes, or local servers unchanged.

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
