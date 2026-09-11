# rewear — Roadmap

Anticipated changes, in rough order. Each item is a candidate for one
incremental change; scope and design are decided when the change starts, not
here. Decisions already made about *how* to build these live in
[ARCHITECTURE.md](ARCHITECTURE.md) — this file only records *what* is
anticipated and why it is sequenced this way.

## Iteration 0 — foundations

1. **Project scaffolding** — ✅ **done** (see [`api/`](../api/), `docker-compose.yml`,
   `.github/workflows/ci.yml`). Ktor modular-monolith skeleton with the refined
   ADR-4 seams (`common` / `users` / `catalog` / `app`; listings, categories,
   favorites are packages in `catalog`), native-friendly library baseline per
   ADR-13 (Ktor CIO, kotlinx.serialization, Koin, Exposed R2DBC, Flyway), local
   `docker compose` (Postgres, Zitadel, Garage), `/health` reporting the git SHA,
   and CI building both a JVM dev image and the GraalVM native production image to
   GHCR with a smoke test against the native binary. **The ADR-13 image-resize
   spike is confirmed under the native image** (server-side resizing works; no
   client-side fallback needed). The native image boots in ~2 s at ~35 MB RSS.
   Follow-ups surfaced during the native build: reachability metadata is generated
   via the GraalVM tracing agent and validated by the CI native build each commit
   (documented fallback if R2DBC/Netty ever proves intractable is blocking JDBC +
   HikariCP, ADR-13 revision); Flyway can't scan classpath dirs in a native image,
   so native runs migrations from filesystem-baked SQL (D9's later move of
   migrations to a pre-deploy step will retire this).
2. **Production environment** — VPS compose stack behind Caddy, deploy
   pipeline, and a documented manual `pg_dump`-to-B2 command (scheduled
   backups are deferred to the k3s change per ADR-11). Depends on the manual
   steps in the [setup guide](guides/domain-and-hosting-setup.md) (domain,
   VPS, B2, Brevo).

## Iteration 1 — authenticated community catalog with wishlists

3. **Auth integration** — Zitadel wired up (social login + email magic link),
   JWT validation and member/admin role claims in the API, JIT provisioning
   of the local `users` table, and the service-account path used later by
   user blocking. Mechanism decisions are recorded in ADR-8.
4. **Listings** — upload with photos (variants generated at upload, ADR-12),
   title, description, one category, one size (XS–XL); browse and search
   with pagination.
5. **Favorites** — favorite/unfavorite a listing, view own favorites list.
6. **Admin capabilities** — category CRUD; search and remove listings; block
   users.
7. **Clients** — Android app (Kotlin Multiplatform + Compose), user web app
   and admin web app (React + TypeScript). Android and admin web lead; the
   user web app may follow later within the iteration.

## Later — deferred by explicit decision

- **Bump to GraalVM 25 LTS** — move the API toolchain from GraalVM 21 / JDK 21 to
  GraalVM 25 (JDK 25) and native-build-tools 0.10.4 → 1.1.x, adopt the unified
  `reachability-metadata.json` format (regenerated via the JDK 25 tracing agent,
  replacing the five split `*-config.json` files), and drop the explicit
  `-H:*ConfigurationResources` build args. Low-urgency maintenance follow-up to
  project scaffolding — the deprecated per-file format still works — so it is
  scheduled deliberately, not forced. See the `bump-to-graalvm-25` OpenSpec change.

- **CI path filters — build images only for actual code changes** — the
  `.github/workflows/ci.yml` pipeline currently runs on every push to `main`,
  including docs/`openspec/**`-only commits, so a one-line README or spec change
  triggers the full ~5-minute GraalVM native build, smoke test, and GHCR
  republish. Add `paths-ignore:` (or path filters) so non-code changes skip the
  image pipeline (and ideally run only the JVM tests, or nothing). Small tooling
  optimization; do it opportunistically alongside the next `ci.yml` edit.

- **Member contact mechanism** — how buyers reach sellers. Mechanism
  undecided (reveal contact info vs. email relay vs. in-app chat); the choice
  affects what profile data is collected. Revisit ADR-1 when scheduled.
- **Transactions / payments** — blocked on the owner's legal review; brings
  payments, disputes, and compliance scope. Revisit ADR-1.
- **iOS client** — kept cheap by the Kotlin Multiplatform choice (ADR-5).
- **k3s migration** — same images, manifests replace compose; possibly a
  shared cluster with other pet projects, with one shared Postgres instance
  (CloudNativePG, database + role per project, backups to B2). This change
  also brings the first *scheduled* backups (CronJob, then CNPG WAL
  archiving, dead-man's-switch monitored, restore tested once). See ADR-10
  and ADR-11.
- **Image serving polish** — Cloudflare transform rule to hide the B2 path
  prefix, cache rules for longer edge caching (noted in the setup guide).
- **Public/SEO-facing catalog** — the SPAs served as static files from the
  proxy image assume no SEO needs; if the catalog should ever be indexable
  by search engines, that means server-side rendering (e.g. Next.js) and a
  dedicated runtime container for the user web app. Revisit ADR-10's
  registry/serving model then.

## Decision triggers to watch

Recorded in the ADRs, listed here so they are not forgotten:

- Real users accumulate before the k3s migration lands → pull scheduled
  backups forward into the compose stack (ADR-11).
- Photo storage passes tens of GB, or Cloudflare account concentration
  becomes a concern → re-evaluate storage vendor split (ADR-7).
- Cloudflare-proxy-over-B2 serving proves fragile → reconsider R2 (ADR-7).
- Zitadel disappoints → Keycloak is the named fallback (ADR-8).
- 4GB VPS gets tight (likely only with k3s) → one-click resize to 8GB
  (ADR-10).
