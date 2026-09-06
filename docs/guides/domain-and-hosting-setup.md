# Domain & Hosting Setup Guide

Manual, one-time steps to get the rewear production environment reachable on the
internet. Everything here is done in browser dashboards and a terminal — no
application code involved. Expect ~1–2 hours total, spread over a day (DNS and
email verification involve waiting).

**End state:**

- A domain (e.g. `rewear.example`) with subdomains pointing at your VPS
- A Hetzner VPS running Docker, reachable via SSH
- HTTPS working automatically via Caddy + Let's Encrypt
- Backblaze B2 buckets for listing photos and backups, photos served via
  Cloudflare CDN
- An SMTP account so Zitadel can send magic-link emails that don't land in spam

Placeholders used below — replace with real values:

| Placeholder | Meaning |
|---|---|
| `rewear.example` | your purchased domain |
| `203.0.113.10` | your VPS public IP |

---

## 1. Buy the domain

Recommended: **Cloudflare Registrar** (sells at wholesale cost, ~€10/year, no
renewal markup) — Cloudflare's DNS + CDN proxy is also what serves the B2
photos with free egress (step 5), so the account is needed anyway.
Alternatives if the name is unavailable there: Porkbun, Namecheap (but then
put the domain on Cloudflare DNS regardless, for the image CDN).

1. Create a Cloudflare account (or log in).
2. Registrar → Register a domain → buy `rewear.example`.
3. The domain automatically uses Cloudflare DNS. Done.

> The name is the brand — this is your wife's call. Everything below works with
> any name; you can even do steps 2–3 before buying and add DNS later.

## 2. Create the Hetzner VPS

1. Create an account at <https://www.hetzner.com/cloud> (identity verification
   can take a few hours on first signup).
2. Create a project, then **Add Server**:
   - Location: Falkenstein or Helsinki (EU)
   - Image: **Ubuntu 24.04 LTS**
   - Type: shared vCPU, **CX22** (2 vCPU / 4GB RAM / 40GB disk, ~€4/mo)
   - Networking: enable public IPv4 + IPv6
   - **SSH key**: paste your public key (`cat ~/.ssh/id_ed25519.pub`; generate
     with `ssh-keygen -t ed25519` if you have none). Do not use password auth.
3. Note the server's public IPv4 → this is `203.0.113.10` below.
4. In Hetzner console → Firewalls → create one and attach it to the server:
   - Allow inbound TCP 22 (SSH), 80 (HTTP), 443 (HTTPS). Nothing else.

First login and basic prep:

```sh
ssh root@203.0.113.10
apt update && apt upgrade -y
# install Docker (includes compose plugin)
curl -fsSL https://get.docker.com | sh
# unattended security updates
apt install -y unattended-upgrades
reboot
```

## 3. Point DNS at the VPS

In Cloudflare → your domain → DNS → add **A records** (and optionally matching
AAAA records for the VPS IPv6 address):

| Type | Name | Content | Proxy status |
|---|---|---|---|
| A | `app` | `203.0.113.10` | **DNS only (grey cloud)** |
| A | `api` | `203.0.113.10` | DNS only |
| A | `auth` | `203.0.113.10` | DNS only |
| A | `admin` | `203.0.113.10` | DNS only |
| A | `@` (apex) | `203.0.113.10` | DNS only |

> **Why "DNS only"?** Caddy on the VPS obtains Let's Encrypt certificates by
> answering challenges directly. Cloudflare's orange-cloud proxy intercepts
> traffic and breaks that flow. Grey cloud = plain DNS, simplest correct setup.
> (Enabling the proxy later for caching/DDoS protection is possible with
> Cloudflare origin certificates, but is an optimization — skip it for now.)
> The one exception is the `images` record added in step 5, which must be
> **proxied (orange cloud)** — it points at Backblaze, not the VPS, and
> Cloudflare provides its TLS and caching.

Verify propagation (minutes, occasionally longer):

```sh
dig +short app.rewear.example   # should print 203.0.113.10
```

## 4. HTTPS — nothing to do (that's the point of Caddy)

Caddy runs as a container in the production compose stack and automatically
obtains and renews Let's Encrypt certificates for every domain in its config.
The only prerequisites are the ones already met: DNS pointing at the VPS and
ports 80/443 open. There is no certbot, no cron job, no renewal to remember.

When the stack is deployed for the first time, verify:

```sh
curl -I https://api.rewear.example/health
# expect HTTP/2 200 with a valid certificate
```

If it fails: check DNS resolves to the VPS (`dig`), ports 80/443 are open in
the Hetzner firewall, and the record is grey-cloud, in that order.

## 5. Backblaze B2 buckets (production photos + backups)

1. Create an account at <https://www.backblaze.com/sign-up/cloud-storage> and
   pick the **EU (Amsterdam)** region. First 10GB of storage are free.
2. Create two buckets:
   - `rewear-photos` — files **public** (listing photos are public content)
   - `rewear-backups` — files **private** (nightly Postgres dumps)
3. API credentials for the backend: Application Keys → create one key scoped
   to `rewear-photos` (read/write) and a separate one scoped to
   `rewear-backups` for the backup job. For each, save the **keyID**,
   **applicationKey**, and the bucket's **S3 endpoint** shown on the bucket
   page (looks like `s3.eu-central-003.backblazeb2.com`) — these become env
   vars for the API and backup containers.
4. CDN serving via Cloudflare (free B2→Cloudflare egress, global caching):
   - Note the friendly-URL host on the bucket page (looks like
     `f003.backblazeb2.com` — the number matches your S3 endpoint).
   - Cloudflare DNS → add **CNAME** `images` → `f003.backblazeb2.com` with
     proxy **enabled (orange cloud)** — the one record where orange cloud is
     correct, because Cloudflare must terminate TLS and cache here.
   - Photos are then reachable at
     `https://images.rewear.example/file/rewear-photos/<key>` — this is the
     URL shape the API hands to clients.
   - Optional polish, can wait: a Cloudflare Transform Rule can rewrite
     `/<key>` → `/file/rewear-photos/<key>` to hide the prefix, and a Cache
     Rule can force longer edge caching for this hostname.

## 6. SMTP for magic-link emails (Brevo)

Zitadel sends login emails; without a proper sender they go to spam.

1. Create a free account at <https://www.brevo.com> (300 emails/day free —
   ample).
2. Senders & Domains → add `rewear.example` → Brevo shows DNS records
   (DKIM, SPF/TXT, verification) → add each in Cloudflare DNS → verify.
3. SMTP & API → note the SMTP host, port, login, and key — these go into
   Zitadel's SMTP configuration.

## 7. Ongoing operational notes

- **Backups:** during the compose stage, backups are manual by decision
  (ADR-11) — run the documented `pg_dump`-to-B2 command before upgrades or
  migrations, and occasionally on principle. Scheduled, monitored backups
  arrive with the k3s migration. Whenever you take a dump, remember: a backup
  that has never been restored is a hope, not a backup — test a restore at
  least once.
- **VPS resize:** if 4GB RAM gets tight (e.g. after adding k3s), Hetzner
  resizes to CX32 (8GB) with a shutdown + one click; disk grows too if you
  allow it (one-way).
- **Costs:** VPS ~€4/mo + domain ~€10/yr. B2 (10GB free, egress free up to 3×
  stored volume and free via Cloudflare anyway), Brevo, and GitHub Container
  Registry stay at €0 on free tiers at this scale.

## Checklist

- [ ] Domain purchased, on Cloudflare DNS
- [ ] VPS created (CX22, Ubuntu LTS, SSH key only), firewall allows 22/80/443
- [ ] Docker installed on VPS
- [ ] A records for `app`, `api`, `auth`, `admin`, apex → VPS IP, grey cloud
- [ ] `dig` resolves all subdomains to the VPS
- [ ] B2 buckets `rewear-photos` (public) + `rewear-backups` (private), scoped application keys saved
- [ ] CNAME `images` → B2 friendly-URL host, **orange cloud**; a photo URL loads over HTTPS
- [ ] Brevo account, domain verified (DKIM/SPF records added), SMTP credentials saved
- [ ] After first deploy: `https://api.../health` serves with valid certificate
- [ ] After first deploy: manual `pg_dump` to B2 run once, and a restore from it tested once
