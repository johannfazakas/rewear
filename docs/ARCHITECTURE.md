# rewear — Architecture

Reference for all development. Any change is expected to conform to the
decisions below; a change that needs to contradict one should say so explicitly
and revisit the relevant decision record (each records *why*, so you can judge
whether the reason still holds).

Companion docs:

- [Roadmap](ROADMAP.md) — planned iterations and anticipated future changes
- [Domain & hosting setup guide](guides/domain-and-hosting-setup.md) — one-time manual steps

## 1. Product context

rewear is a community marketplace for second-hand clothes, run as a small
business by a single administrator. Niche is deliberately undecided (women's
dresses vs. children's wear) — the architecture is identical either way because
categories are admin-managed data, not code.

**Iteration 1 scope** — an authenticated community catalog with wishlists:

- Members authenticate (social login or email magic link)
- Members upload listings: photos, title, description, exactly one category,
  one size (fixed scale: XS, S, M, L, XL)
- Members browse and search listings
- Members favorite listings and view their favorites list
- Admin manages categories (flat list, CRUD)
- Admin searches and removes listings, blocks users

**Explicitly out of scope for iteration 1** (deferred, not rejected):

- Buying/selling, payments, anything transactional (also pending legal review)
- Member-to-member contact (mechanism undecided: reveal contact info vs. relay
  vs. in-app chat — an open question, revisit when the feature is scheduled)
- iOS client, user web app may lag Android (validate with Android + admin first)

## 2. C4 Level 1 — System context

```
   ┌──────────────┐          ┌──────────────┐
   │  Community   │          │    Admin     │
   │    Member    │          │  (business   │
   │              │          │    owner)    │
   │ • uploads    │          │ • manages    │
   │   listings   │          │   categories │
   │ • browses /  │          │ • removes    │
   │   searches   │          │   listings   │
   │ • favorites  │          │ • blocks     │
   └──────┬───────┘          │   users      │
          │                  └──────┬───────┘
          │ web + Android           │ admin web app
          ▼                         ▼
   ┌═══════════════════════════════════════┐
   ║            REWEAR (system)            ║
   └═══════════╤═══════════╤═══════════════┘
               │           │
       ┌───────┘           └────────┐
       ▼                            ▼
   ┌──────────────┐          ┌──────────────┐
   │   Zitadel    │──SMTP──▶ │    Brevo     │
   │  (identity,  │          │ (email       │
   │  self-hosted)│          │  delivery)   │
   └──────────────┘          └──────────────┘

   Out of scope: payment providers (no transactions in POC)
```

Both user roles are humans in one community; a member and the admin differ only
in role claims on their identity. Email exists solely for auth magic links and
is sent by Zitadel, not by application code — iteration 1 has **zero** email
integration of its own.

## 3. C4 Level 2 — Containers

```
  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
  │  User Web   │   │   Android   │   │  Admin Web  │
  │ React + TS  │   │ KMP+Compose │   │ React + TS  │
  └──────┬──────┘   └──────┬──────┘   └──────┬──────┘
         │                 │                 │        login (OIDC)
         │                 │                 │      ┌──────────────┐
         ▼                 ▼                 ▼      │   Zitadel    │
  ┌─────────────────────────────────────────────┐  │  container   │
  │              Caddy (reverse proxy,          │─▶│              │
  │              automatic HTTPS)               │  │ social login │
  └──────────────────────┬──────────────────────┘  │ magic links  │
                         ▼                         └──────┬───────┘
  ┌─────────────────────────────────────────────┐         │
  │        rewear API — Kotlin + Ktor           │◀────────┘
  │            modular monolith                 │   JWT validation
  │                                             │   (JWKS)
  │  ┌────────┐ ┌─────────┐ ┌──────┐ ┌───────┐  │
  │  │listings│ │favorites│ │ROLES │ │catalog│  │   modules =
  │  │        │ │         │ │admin/│ │(cate- │  │   internal
  │  │        │ │         │ │moder.│ │gories)│  │   boundaries,
  │  └────────┘ └─────────┘ └──────┘ └───────┘  │   one deployable
  └──────┬───────────────────────────┬──────────┘
         ▼                           ▼
  ┌─────────────┐        ┌───────────────────────┐
  │  Postgres   │        │ Object storage (S3 API)│
  │  container  │        │ prod: Backblaze B2     │──▶ photos served to
  │             │        │ dev:  Garage container │    clients directly
  └─────────────┘        └───────────────────────┘    (CDN URL, not via API)
```

| Container | Technology | Notes |
|---|---|---|
| User web app | React + TypeScript | static files, served by Caddy |
| Admin web app | React + TypeScript | separate app, static files, served by Caddy |
| Mobile app | Kotlin Multiplatform + Compose | Android first; structure for later iOS |
| API | Kotlin + Ktor, single deployable | all clients, admin behind role checks |
| Identity | Zitadel container | OIDC; social + magic link; sends auth emails via Brevo SMTP |
| Database | Postgres container | listings, favorites, categories, moderation state |
| Object storage | S3 API — B2 (prod) / Garage (dev) | photos; public read via CDN-fronted URL |
| Reverse proxy | Caddy container | routes subdomains, automatic Let's Encrypt TLS |

Subdomain layout: `app.` (user web) · `admin.` (admin web) · `api.` (Ktor) ·
`auth.` (Zitadel) · `images.` (Cloudflare-proxied CNAME → B2 public bucket).

C4 Level 3 is intentionally not documented up front: components emerge from
incremental changes. The module map new capabilities should slot into is the
refined decomposition recorded in ADR-4's 2026-09 revision — `users` and
`catalog` domain modules, plus `app` and `common` — not the finer seam list
sketched in the diagram above.

## 4. Decision records

Format: decision → why → rejected alternatives. Status is *accepted* unless
noted. Dates are 2026-09 (initial architecture session).

### ADR-1: No transactions in the POC

Interaction between members is favorites only; no buying, no payments, no
member-to-member contact. **Why:** removes payments, escrow, disputes, payouts,
and most legal surface; the open legal questions around selling are the
business owner's to resolve before any transactional feature is specced.
**Rejected:** classifieds-style "contact the seller" — deferred (mechanism
undecided); full checkout — far too heavy for validation.

### ADR-2: Fixed sizing, admin-managed flat categories

Sizes are a hardcoded one-dimensional scale (XS–XL). Categories are a flat,
admin-editable list; every listing has exactly one. **Why:** kills the
configurable-taxonomy complexity while keeping the niche decision (dresses vs.
kids' wear) open — pivoting is a data change, not a code change. **Rejected:**
admin-configurable attribute/size schemes (flexibility the POC may never use);
fully hardcoded categories (would couple the niche decision to code).

### ADR-3: API-first backend

One REST API serves every client from day one; no server-rendered pages.
**Why:** three clients (user web, Android, admin web) share the same backend;
Android forces a real API anyway. Admin endpoints live in the same API behind
role checks. **Rejected:** separate admin backend (overkill); server-rendered
web with a bolted-on mobile API (two paths to maintain).

### ADR-4: Kotlin + Ktor modular monolith

Single deployable with enforced internal module boundaries (listings,
favorites, catalog, admin/moderation). **Why:** microservices at this scale are
pure ceremony; modules keep seams clean so future changes map onto
them, and extraction stays possible if ever justified. Kotlin matches the
team's JVM background and the mobile stack; Ktor over Spring for a lighter,
more modern playground. **Rejected:** microservices (ceremony); Spring Boot
(fine, but heavier and less to learn).

**Revised 2026-09 (project-scaffolding change):** the original seam list
(listings, favorites, catalog, admin/moderation, + users glue) proved finer than
iteration-1 responsibilities justify. Refined decomposition: two domain Gradle
modules — **`users`** (identity: local users, JIT provisioning, profile, blocked
flag; the dependency leaf) and **`catalog`** (the domain core: listings,
categories, and favorites as internal packages) — plus **`app`** (composition
root) and **`common`** (shared kernel). *Categories* fold into `catalog` (a
lookup that exists only to classify listings, not a standalone module).
*Admin/moderation* is **not** a module — admin operations are role-guarded
endpoints inside the owning module, and the `admin` role check is shared auth
middleware; a moderation module appears only if it grows real domain (audit,
reports). *Favorites* is a package inside `catalog`, promotable later if it earns
it. Boundaries are enforced by the Gradle build graph plus Kotlin `internal`
visibility (no separate `-api`/`-service` contract modules needed), with
**Konsist** policing package boundaries within `catalog` and cross-cutting rules.
Cross-module data is referenced by plain ID (no cross-module foreign keys); each
module owns its tables. The "enforced boundaries" principle stands unchanged;
only the seam count and enforcement mechanism are refined.

### ADR-5: Client technologies

React + TypeScript for both web apps (existing familiarity, mature ecosystem);
Kotlin Multiplatform + Compose for mobile, Android shipped first with iOS kept
cheap to add later. **Rejected:** Compose-for-web for the user web app (still
maturing, weak SEO/web UX); native-Android-only (would close the iOS door KMP
keeps open).

### ADR-6: Postgres for everything queryable

Listings, favorites, categories, moderation state, and search (Postgres
full-text/ILIKE) all live in one Postgres. **Why:** at tens-to-hundreds of
users, one boring database is the right amount of database. **Rejected:**
Elasticsearch (search scale we don't have); Redis (a favorites table is a
table).

### ADR-7: Photos in S3-API object storage — Backblaze B2 in prod, Garage in dev

Application code targets only the S3 API; endpoint/credentials are env vars.
Production uses Backblaze B2, EU region (first 10GB free; $6/TB/mo after —
2.5× cheaper than R2; egress free up to 3× stored volume/month). Public
serving goes through a Cloudflare-proxied CNAME (`images.` → B2 friendly-URL
host): the Backblaze–Cloudflare partnership makes that egress free and adds
CDN caching. Local development uses a Garage container (lightweight Rust S3
store, community-governed) so dev works offline.
Clients fetch photos directly by URL — image traffic never transits the API.
**Why:** photos are the dataset that is painful to lose and big to back up;
off-VPS object storage makes them survive VPS death for free. B2 over the
near-tie alternatives for consistency with the owner's other projects (one
storage vendor to operate) and cheaper storage at scale; the dev/prod hybrid
proves the portability rule continuously. **Rejected:** photos in Postgres
(bloat); self-hosted object store in production (photos die with the VPS);
**MinIO** for dev — original choice, dropped 2026-09: its community edition
was discontinued and the repository archived in Feb 2026, leaving only frozen
images; alternatives evaluated then: RustFS (Apache 2.0, drop-in, but only a
year old), SeaweedFS and LocalStack (both overkill for a dev endpoint) —
Garage won on maturity + non-profit governance; provider-proprietary storage
APIs (breaks portability); **Cloudflare R2** — evaluated 2026-09, near-tie: wins
on one-click CDN custom domain and unconditional free egress, lost on vendor
consistency and $15/TB storage; revisit if the Cloudflare-proxy-over-B2
serving setup proves fragile; **Hetzner Object Storage** — evaluated 2026-09:
same-network locality to the VPS and free internal traffic, but no free tier
(~€6.5/mo base), no CDN, and least mature (GA 2025, reliability incidents in
2026); becomes interesting only in an all-Hetzner/EU-single-vendor
consolidation with large storage volumes.

### ADR-8: Auth delegated to self-hosted Zitadel (OIDC)

Zitadel provides social login and built-in email magic links; the API only
validates JWTs against Zitadel's JWKS and reads role claims (member vs. admin).
Auth emails go out via Brevo SMTP, configured in Zitadel. **Why:** building
multi-client social + passwordless auth is weeks of security-sensitive work;
OIDC keeps the IdP swappable; Zitadel over Keycloak for first-class magic
links and a much smaller footprint on a 4GB VPS. **Rejected:** hand-rolled
auth (risk, time); Firebase/managed IdPs (cannot self-host — violates
portability); Keycloak (heavier, magic links need extensions — remains the
fallback if Zitadel disappoints); Ory Kratos (bring-your-own login UI).

Mechanism decisions (2026-09): clients are public OIDC clients using
Authorization Code + PKCE against Zitadel's hosted login page — the API is
not involved in login. Zitadel issues **JWT access tokens** (not opaque) with
short lifetimes (~1h) plus refresh tokens; the API validates offline against
cached JWKS — no Zitadel call per request, so Zitadel downtime does not take
the API down. Authorization splits by altitude: coarse roles (`member`,
`admin`) are Zitadel project roles read from a token claim; fine-grained
ownership ("own listing", "own favorite") is application logic in Postgres.
The API JIT-provisions a local `users` row (keyed by token `sub`, holds
profile + `blocked` status) on first authenticated request; credentials never
touch the app. Blocking a user = deactivate in Zitadel via its management API
(the one API→Zitadel call path, using a service account) + set the local
`blocked` flag; accepted caveat: an already-issued token keeps read access
until expiry (≤1h), writes are denied immediately via the local flag.

### ADR-9: Containers-first portability rule

Every runtime component is an OCI container configured via env vars (12-factor)
and talking standard protocols only: OIDC, SQL, S3 API, SMTP. No
provider-proprietary services in the critical path. **Why:** the explicit
requirement that the same components can deploy to a cheap VPS, Kubernetes, or
local servers. This rule is what makes every other infra choice swappable.

### ADR-10: Hosting — single Hetzner VPS with Docker Compose; staged path to k3s

Production is one Hetzner CX22 (2 vCPU / 4GB / ~€4/mo) running the full stack
via Docker Compose behind Caddy (automatic Let's Encrypt TLS). Deploys: GitHub
Actions builds images to GHCR, then pull + `compose up` on the VPS. Adopting
k3s on the same VPS is an anticipated future change (see ROADMAP) — same images,
manifests replace the compose file; those manifests then port to any
Kubernetes or local servers. That future cluster may be shared with other pet
projects to amortize cost; rewear assumes only a connection string, and the
agreed pattern there is one shared Postgres instance (CloudNativePG operator,
backups to B2) with a database + role per project — never schema-per-project
— Zitadel's database included.

Registry & versioning (2026-09): images live on GHCR. Two first-party images,
always built together from the same commit: `rewear-api` (JVM image — fat jar on
a Temurin JRE; see ADR-13, which superseded the original native binary) and
`rewear-proxy` (Caddy with both React apps' static builds baked in, so deploy
and rollback are atomic across web + API). Third-party images (Postgres,
Zitadel, Garage) are pinned to exact upstream versions in compose, upgraded
deliberately. Every push to main tags both images `sha-<short>` (immutable)
plus `latest` (convenience only); production compose pins the SHA tag via
env var — deploy = bump tag + `compose up`, rollback = previous tag. No
SemVer (no external consumers). `linux/amd64` only (matches the x86 VPS; the
JVM image is arch-portable but there is no second target to build for). The
API's `/health` reports its git SHA. GHCR private-package
storage is limited (500MB free) — make packages public or add a prune
workflow; decide at repo creation. **Why:** cheapest real hosting; Compose maximizes
iteration-1 momentum while the container rule keeps the k8s door fully open;
k3s-from-day-one was the biggest risk of stalling the product for playground
reasons. Zitadel-not-Keycloak is what makes 4GB sufficient (~1.5GB used);
resizing to 8GB is one click if k3s lands. **Rejected:** k3s day one (momentum
risk); PaaS like Fly/Railway (dearer, weak portability, no local-server
story); full managed k8s (cost and ceremony).

### ADR-11: Backups over replication

Durability = `pg_dump` shipped to B2 (photos are already durable in B2).
Scheduled backups are deliberately deferred during the compose stage: only a
documented manual `pg_dump`-to-B2 command exists, run before risky operations
(upgrades, migrations). Accepted risk while this holds: VPS death loses
catalog data (listings metadata, favorites, categories) since the last manual
dump. Automation arrives with the k3s migration — a native CronJob, then
CloudNativePG WAL archiving (ADR-10); the automated job must ping a
dead-man's switch (e.g. healthchecks.io) so silent failure gets noticed, and
a restore must be tested once when it lands. Named trigger: if real users
accumulate before k3s does, pull scheduled backups forward (a supercronic
backup container in the compose stack — do not use host cron; the schedule
must live in git). **Why deferred:** POC-stage data is low-stakes and the
compose stage is meant to maximize product momentum.
**Why:** replication/HA buys availability this scale doesn't need at real
complexity cost. **Rejected:** streaming replication, managed Postgres (cost,
and portability rule makes them easy to adopt later if ever warranted).

### ADR-12: Pre-derived image variants, generated at upload

The API resizes each uploaded photo into fixed variants (at minimum: thumbnail
for list/grid views, detail for the listing page) and stores all variants in
object storage; list endpoints return thumbnail URLs and clients paginate and
lazy-load, so a browse grid costs ~1MB, not hundreds. Originals are never
served in lists. Exact sizes, formats, and page sizes are decided in the
listings change, not here. **Why:** phone photos are 2–5MB; serving them in
grids is unusable on mobile and burns B2 egress; upload-time resizing in the
API (JVM image library) adds zero infrastructure. **Rejected:** on-the-fly
resize service (imgproxy — a new container to run for scale we don't have;
the extraction seam exists if ever needed); Cloudflare image resizing (paid,
proprietary — violates ADR-9); serving originals (unusable).

### ADR-13: GraalVM native image as the API's production target

**Superseded 2026-10-17 (`bump-jdk25-drop-native` change).** The API now ships
as a **plain JVM image** (fat jar on a Temurin JRE, heap capped with
`-XX:MaxRAMPercentage`), not a GraalVM native image — i.e. the "plain JVM with a
tuned small heap" alternative this ADR originally rejected. **Why reversed:** at
POC scale the native path's ongoing cost outweighed its memory saving. Bringing
the toolchain to GraalVM 25 made that cost concrete — a hand-maintained AWT/Java2D
**JNI** reachability net the tracing agent cannot derive (the resize path crashed
at `Toolkit.loadLibraries` until it was hand-written), a ~5-minute `nativeCompile`
on every CI push, and library constraints kept solely for native compatibility.
The memory trade is accepted: ~300–500MB RSS vs. ~50–150MB native, on a ~4GB VPS
where Zitadel is the main tenant. **Named trigger to revisit:** if the VPS gets
memory-tight (more tenants, or the k3s migration), either tune the heap harder or
reconsider native then — when the toolchain is more mature and the metadata story
is simpler. Consequence for ADR-13's revision below: the **R2DBC→blocking-JDBC
fallback is no longer forced by native-image metadata** (there is none); R2DBC
stays on its own merits, and a future move to JDBC is now a free-standing
decision, not a native-driven one. The native-friendly *library* choices
(kotlinx.serialization, Ktor CIO, no classpath-scanning DI, no Hibernate/JPA,
Flyway) are kept — they remain sensible on the JVM and nothing depends on
reversing them. The JDK-25 toolchain cascade (Gradle 9.7.1, Kotlin 2.2.21, Ktor
3.5.2) is unrelated to native and was retained.

_Original decision, retained for history:_

The Ktor API compiles to a GraalVM native image for production (~50–150MB
RSS vs. 300–500MB on the JVM — real headroom on a shared 4GB VPS; ~50ms
startup). Consequences, binding from scaffolding onward: library choices must
be native-friendly — kotlinx.serialization (not Jackson), Ktor CIO engine,
manual DI or Koin (no classpath scanning), JDBC with a thin layer chosen with
native compatibility as a criterion (no Hibernate/JPA), Flyway. Development
and tests run on the JVM; CI builds the native binary and runs smoke tests
against it on every commit so native-only breakage surfaces early. Known
risk, to be validated by an early spike: AWT/ImageIO-based image resizing
(ADR-12) is the weakest area of native image support — if the spike fails,
fall back to client-side resizing before upload. **Why:** lowest-memory
option for the modular monolith without changing the architecture; peak-JIT
throughput loss is irrelevant at this scale. **Rejected:** plain JVM with a
tuned small heap (works, ~2–3× the footprint); trimming memory by splitting
services (opposite of ADR-4).

**Revised 2026-09 (project-scaffolding change):** the persistence path uses
**R2DBC** (Exposed R2DBC module over `r2dbc-postgresql` + `r2dbc-pool`), not
blocking JDBC, for the runtime request path. This is a deliberate departure from
the "JDBC with a thin layer" consequence above, taken as a learning experiment
with the reactive stack; it is explicitly **not** driven by a scale need (ADR-6
traffic does not require non-blocking DB). Consequences: R2DBC brings Netty +
Reactor, which work under GraalVM native images but require hand-supplied
reachability metadata (no AOT framework here) — the CI native build validates
that continuously. **Flyway stays JDBC-only**, running migrations over a plain
pgJDBC connection at startup only; pgJDBC therefore remains a dependency but
never runs on the request path. The original blocking **JDBC + HikariCP +
`Dispatchers.IO`** stack is the documented fallback if the R2DBC native-image
metadata proves intractable — reverting is cheap under ADR-9. All other ADR-13
library constraints (kotlinx.serialization, CIO, no classpath-scanning DI, no
Hibernate/JPA, Flyway) stand unchanged.

## 5. Open questions

- **Niche:** women's dresses vs. children's wear — business decision, does not
  block development (ADR-2).
- **Member contact mechanism:** reveal contact info vs. email relay vs. in-app
  chat — decide when the feature is scheduled; choice affects what profile
  data is collected (ADR-1).
- **Domain name:** the brand; blocks deployment, not development.
