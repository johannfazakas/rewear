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
   favorites are packages in `catalog`), library baseline (Ktor CIO,
   kotlinx.serialization, Koin, Exposed R2DBC, Flyway), local `docker compose`
   (Postgres, Zitadel, Garage), `/health` reporting the git SHA, and CI building
   the `rewear-api` JVM image to GHCR with an end-to-end smoke test. **The ADR-12
   image-resize path works server-side** (no client-side fallback needed).
   (Scaffolding originally shipped a GraalVM native production image; it was
   dropped in favour of a plain JVM image before reaching `main` — see ADR-13 and
   the `bump-jdk25-drop-native` change.)
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

- **Lift the Kotlin JVM bytecode target 24 → 25** — the toolchain is on JDK 25 but
  caps bytecode at **JVM 24**, because no released Kotlin exposes `JvmTarget.JVM_25`
  (2.2.21 maxes at 24). Once a Kotlin release adds JVM 25, bump `jvmTarget` (and the
  aligned Java `source/targetCompatibility`) from 24 to 25. Purely cosmetic
  alignment — the JDK 25 runtime consumes JVM 24 bytecode fine — so low urgency.

  _Toolchain delivered by the `bump-jdk25-drop-native` change (JDK 25, Gradle 9.7.1,
  Kotlin 2.2.21, Ktor 3.5.2; bytecode still JVM 24). That same change dropped the
  GraalVM native image for a plain JVM production image — see ADR-13._

- **CI path filters — build the image only for actual code changes** — the
  `.github/workflows/ci.yml` pipeline runs on every push to `main`, including
  docs/`openspec/**`-only commits, so a one-line README or spec change still
  triggers an image build, smoke test, and GHCR republish. Add `paths-ignore:`
  (or path filters) so non-code changes skip the image pipeline (and ideally run
  only the JVM tests, or nothing). Lower urgency now that dropping the GraalVM
  native image (ADR-13) removed the ~5-minute `nativeCompile` — the JVM image
  build is much cheaper — but still worth doing opportunistically alongside the
  next `ci.yml` edit.

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
