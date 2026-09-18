# Spendly

Personal expense tracker with Spring Boot + Angular.

![Spendly dashboard](docs/screenshots/dashboard.png)

**Live:** https://d26ecks03gnq4n.cloudfront.net

**Stack:** Java 21, Spring Boot, JWT, JPA/Hibernate, Flyway, PostgreSQL, Angular, Docker, GitHub Actions, Testcontainers, Caffeine, Groq AI, Terraform (AWS S3 + CloudFront)

[![CI](https://github.com/zaker1998/Spendly-Personal-Expense-Tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/zaker1998/Spendly-Personal-Expense-Tracker/actions/workflows/ci.yml)

> The SPA is served from CloudFront, so the app itself loads immediately. The API
> behind it is on Render's free tier and is kept awake by a scheduled ping; if it
> has been asleep, the first data request can still take a moment.

## Features

- Register / login, roles: `USER` and `ADMIN`
- **Sessions that can actually end** — a 15-minute access token plus a rotating refresh token in an `httpOnly` cookie; logout and password change revoke it server-side
- **Per-account lockout** after repeated failed logins, on top of the per-IP rate limit
- Expenses & categories CRUD, with **optimistic locking** — a stale edit gets a 409 instead of silently overwriting someone else's change
- **AI category suggestion** — an LLM (with keyword-heuristic fallback) suggests the right category from the expense description
- Filters + pagination on expense list
- Monthly dashboard with month picker + category chart, **cached with Caffeine**
- Budgets with progress / over-budget
- CSV export (streamed, and neutralised against spreadsheet formula injection)
- **Rate limiting** on auth + AI endpoints (per-IP fixed window, HTTP 429 + `Retry-After`)
- Consistent JSON errors: structured 400s for bad input, 401/403 bodies, no leaking 500s — each carrying the request id that is also in the logs and the `X-Request-Id` header
- **Accessible UI** — labelled controls, skip link, WCAG AA contrast; zero axe-core violations in **both light and dark themes**, checked in CI
- Dark theme that follows the OS setting
- Admin UI (users + all expenses), paged
- Swagger UI
- Prometheus metrics at `/actuator/prometheus` (admin only), including cache hit/miss counters; liveness/readiness probes
- Unit tests + integration tests against real PostgreSQL via Testcontainers
- `docker compose` for local run

## Architecture

```mermaid
flowchart LR
    BROWSER[Browser]

    subgraph Edge["CloudFront (Terraform)"]
        CF{{"one origin<br/>two behaviours"}}
    end

    S3[(S3<br/>Angular 19 bundle)]

    subgraph Backend["Spring Boot 3.4 (Java 21)"]
        SEC[JWT Security Filter]
        API[REST Controllers]
        SVC[Services]
        CACHE[(Caffeine Cache<br/>monthly summaries)]
        AI[AI Category Client]
        JPA[JPA / Hibernate]
    end

    DB[(PostgreSQL<br/>Flyway migrations)]
    LLM[Groq API]

    BROWSER -->|HTTPS| CF
    CF -->|"/* (cached at the edge)"| S3
    CF -->|"/api/* (Bearer token, uncached)"| SEC --> API --> SVC
    SVC --> CACHE
    SVC --> JPA --> DB
    SVC --> AI -->|optional| LLM
```

### Design decisions

Longer write-ups of the trickier calls — including four bugs I found and fixed
in my own code — are in [docs/ENGINEERING_NOTES.md](docs/ENGINEERING_NOTES.md).

- **Short JWT plus a revocable refresh token** — a signed JWT cannot be withdrawn, so its lifetime *is* the damage window if it leaks; it lasts 15 minutes. The session lives in a refresh token that is random, stored only as a SHA-256 hash, rotated on every use and sent as an `httpOnly`, `SameSite=Lax` cookie scoped to `/api/auth` — script in the page cannot read it, and a replayed copy is rejected. Logout and password change revoke it on the server. The SPA renews transparently: parallel 401s share one refresh, and the route guard renews before deciding the user is signed out.
- **Flyway with `ddl-auto: validate`** — the schema is owned by versioned SQL migrations, never by Hibernate auto-DDL; production and tests run identical schemas.
- **AI suggestions are validated server-side** — the LLM is asked to pick from the user's own category names, and its answer is checked against the database before being returned. A hallucinated category can never reach the client. On any provider failure (timeout, rate limit, missing key) the endpoint degrades to a keyword heuristic instead of erroring.
- **Caffeine instead of Redis for caching** — the app runs as a single instance, so an in-process cache gives the same latency win without extra infrastructure. Writes evict exactly the affected user+month entry, not the whole cache; a 10-minute TTL bounds staleness.
- **Cache eviction happens after commit** — the evict is raised as a domain event and consumed with `@TransactionalEventListener(AFTER_COMMIT)`. Evicting inline during the write left a window where a concurrent read could repopulate the cache from uncommitted state and then serve stale totals for the rest of the TTL; it also meant a rolled-back write still dropped a valid entry.
- **Single currency, enforced end to end** — amounts are summed in the monthly summary and in budget progress, so mixed currencies would produce a total that looks right and isn't. Rather than half-build multi-currency, the API rejects the concept: the server sets the code, a CHECK constraint backs it, and one constant (`AppCurrency`) is the only place to change when FX rates are actually modelled.
- **Testcontainers over H2 for integration tests** — tests run against the same PostgreSQL version as production, so dialect-specific behaviour (e.g. in filtered queries) is actually covered.
- **In-memory rate limiting** — login/register and the AI endpoint are the two abuse targets (credential brute-force, external API quota). A per-IP fixed window in process memory is enough for a single instance — same reasoning as Caffeine over Redis. `X-Forwarded-For` is only trusted when the deployment declares a proxy in front (`RATE_LIMIT_BEHIND_PROXY`), because otherwise the header is client-supplied and rotating it would hand out an unlimited number of login attempts.
- **Accessibility is measured, not asserted** — an axe-core suite runs against the real app in CI across every page and both admin tabs, so an unlabelled input or a contrast regression fails the build. It is a floor rather than a certificate: automated rules catch roughly a third of WCAG issues, and the app has not been tested with a screen reader. Details, including the two places where adding ARIA would have made things worse, are in the engineering notes.
- **The CSV export streams** — it reads 500 rows at a time and writes straight to the socket, deliberately without a surrounding transaction so a slow download cannot hold a pooled connection open. Chunks are fetched by seeking past the last `id` rather than by `OFFSET`: an offset walk re-reads everything before the offset on every chunk, and paging through `Page` added a `COUNT(*)` to each one.
- **Optimistic locking with the version on the wire** — Hibernate's `@Version` only covers one transaction. Two tabs that each load and then save are two transactions, so the client also sends back the version it loaded and the API refuses a stale one with 409.
- **Indexes follow the hot paths** — every authenticated request loads the user by email case-insensitively, so there is a `lower(email)` index for it; the description search uses a `pg_trgm` index, since `LIKE '%term%'` cannot use a btree.
- **Static assets are served from a CDN, not from the API host** — the SPA used to be behind the same free-tier instance as the API, so a cold start meant a blank page for up to a minute. On CloudFront the app renders from an edge cache immediately and only the first data call pays the wake-up. `/api/*` is a second behaviour on the same distribution, so the browser sees one origin, there is no preflight in front of the login request, and the API host is not baked into the bundle. The whole thing is Terraform ([`infra/`](infra/)).
- **Every list endpoint is paged** — including the admin views. An admin screen that returns every expense in the system is the one query whose cost grows without bound.

## Run locally

```bash
docker compose up --build
```

| | URL |
|--|--|
| App | http://localhost:4200 |
| API | http://localhost:8081/api |
| Swagger | http://localhost:8081/swagger-ui.html |

Postgres is on host port **5433**, API on **8081** (so they don't clash with other local containers).

### Demo users

Seeded only when `SEED_DEMO_DATA=true`, which `docker compose` and the public
demo both set on purpose. It is **off by default** so no real deployment ever
comes up with a known admin password.

The demo account gets three rolling months of plausible Vienna spending — about
125 expenses across rent and Betriebskosten, a Wiener Linien pass, groceries,
Kaffeehaus visits, ÖBB trips and the odd Heuriger — with monthly budgets set a
little above what actually gets spent, so most bars sit in the 70–95% band and a
couple go over. Amounts come from a fixed random seed, so every deploy produces
the same figures and the screenshots above stay true.

**The demo user's data is rebuilt on every startup.** Visitors add test rows, and
without a reset what the next person opens is junk piled around stale expenses;
rebuilding also rolls the three-month window forward so the dashboard always has
a current month. The admin account is left alone.

| Email | Password | Role |
|-------|----------|------|
| `demo@spendly.app` | `Demo123!` | USER |
| `admin@spendly.app` | `Admin123!` | ADMIN |

### AI suggestions (optional, Groq)

Uses Groq’s free API by default. Get a key at [console.groq.com](https://console.groq.com/keys).

```bash
AI_API_KEY=gsk_... docker compose up --build
```

On Render: set only `AI_API_KEY` to your Groq key. Base URL and model already default in the app config.

Without a key, *Suggest category* still works via the keyword heuristic.

| Env var | Default | Purpose |
|---------|---------|---------|
| `AI_API_KEY` | *(empty — heuristic only)* | Groq API key |
| `AI_MODEL` | `openai/gpt-oss-20b` | Optional override |
| `AI_REASONING_EFFORT` | `low` | Sent only when non-blank; reasoning models bill thinking tokens as output |
| `AI_SUGGESTIONS_ENABLED` | `true` | Kill switch |

### Rate limiting

| Env var | Default | Purpose |
|---------|---------|---------|
| `RATE_LIMIT_ENABLED` | `true` | Kill switch |
| `RATE_LIMIT_AUTH_PER_MINUTE` | `10` | Per IP, `/api/auth/**`. `docker compose` raises it to 100: behind its nginx every request shares one IP |
| `RATE_LIMIT_AI_PER_MINUTE` | `30` | Per IP, AI suggestions |
| `RATE_LIMIT_BEHIND_PROXY` | `false` | Read the client IP from `X-Forwarded-For`. Only enable where a proxy you control rewrites it |
| `LOGIN_LOCKOUT_ENABLED` | `true` | Per-account lockout after failed logins |
| `LOGIN_MAX_ATTEMPTS` | `8` | Failures before the account is locked |
| `LOGIN_LOCKOUT_MINUTES` | `15` | How long the lock lasts |

### Other environment variables

| Env var | Default | Purpose |
|---------|---------|---------|
| `JWT_SECRET` | dev value | Must be ≥ 32 bytes; the app refuses to start otherwise |
| `JWT_EXPIRATION_MS` | `900000` | Access token lifetime (15 min) |
| `JWT_ISSUER` | `spendly` | `iss` claim, required on every incoming token |
| `REFRESH_EXPIRATION_MS` | `2592000000` | Refresh token lifetime (30 days) |
| `REFRESH_COOKIE_SECURE` | `true` | `http://localhost` counts as secure, so this can stay on locally |
| `REFRESH_COOKIE_SAME_SITE` | `Lax` | Enough for every setup here: `:4200 → :8080` is cross-origin but same-site |
| `DB_POOL_MAX` / `DB_POOL_MIN_IDLE` | `8` / `0` | Hikari pool, sized for the free Neon tier; no idle minimum so Neon can scale to zero |
| `VIRTUAL_THREADS_ENABLED` | `true` | Java 21 virtual threads for request handling |
| `SEED_DEMO_DATA` | `false` | Create the demo/admin accounts above |
| `EXPORT_MAX_ROWS` | `50000` | Backstop on the streamed CSV export |
| `DATABASE_URL` | — | `postgres://user:pass@host/db` style URL. Split into the `SPRING_DATASOURCE_*` vars by `docker/entrypoint.sh`; set them directly instead if you prefer |
| `SPRING_DATASOURCE_URL` | local Postgres | JDBC URL. Takes precedence over `DATABASE_URL` |
| `PORT` | — | Mapped to `SERVER_PORT`; set automatically by most PaaS hosts |

## Deployment

Three pieces, each on the thing it is actually good at:

| | Where | Provisioned by |
|--|--|--|
| Angular bundle | AWS S3 behind CloudFront | Terraform (`infra/`) |
| Spring Boot API | Render, Docker | `render.yaml` |
| PostgreSQL | Neon | — |

### Frontend — S3 + CloudFront, in Terraform

`infra/terraform` builds a private S3 bucket, a CloudFront distribution in front
of it with Origin Access Control, a security-headers policy, and an IAM role that
GitHub Actions assumes over OIDC — so CI deploys with a short-lived token and
there is no AWS access key anywhere in the repo.

The distribution has two behaviours: `/*` serves the bundle from the edge cache,
`/api/*` proxies to Render uncached. That keeps the app one origin from the
browser's point of view, which is why `environment.prod.ts` can still just say
`apiUrl: '/api'`.

Angular's client-side routes are resolved by a CloudFront Function on the static
behaviour rather than by a distribution-wide 403/404 error mapping — the usual
recipe would rewrite the API's own 403s and 404s into `200` HTML. The reasoning
is in [`infra/terraform/functions/spa-router.js`](infra/terraform/functions/spa-router.js).

Deploys are `.github/workflows/deploy-frontend.yml`: build, `s3 sync --delete`,
a metadata pass that marks the content-hashed bundles `immutable` for a year
while `index.html` stays `no-cache`, then one CloudFront invalidation. Setup and
the outputs to copy are in [`infra/README.md`](infra/README.md).

The root `Dockerfile` still builds the all-in-one image (SPA + API), which is
what `render.yaml` deploys and what makes the project runnable without an AWS
account at all.

### Database — Neon rather than Render

Splitting the database off the host is deliberate. Render's free Postgres
expires after a fixed window and is then deleted — acceptable for a scratch
project, not for a link on a CV. Neon's free tier does not expire, so the
database outlives the hosting choice and moving the app elsewhere is a change of
one environment variable.

`render.yaml` is a blueprint for the whole service. `DATABASE_URL` and
`AI_API_KEY` are marked `sync: false`, so they are set in the dashboard and
never committed.

```bash
# Neon connection strings are already in the shape entrypoint.sh expects,
# including the required ?sslmode=require.
DATABASE_URL=postgresql://user:pass@ep-xxx.eu-central-1.aws.neon.tech/neondb?sslmode=require
```

Flyway builds the schema on first boot, so a fresh, empty database needs no
setup step. With `SEED_DEMO_DATA=true` the demo accounts are created on the same
startup.

> **Cold start.** On the free tier the API sleeps after ~15 minutes idle and a
> measured wake-up takes about 52s. Serving the SPA from CloudFront means that no
> longer shows as a blank page — the app renders from the edge in well under a
> second and only the first data call waits.
>
> That first call still matters: CloudFront's origin read timeout caps at 60s,
> which leaves little room above a 52s wake-up, so a sleeping instance risks a 504
> rather than a slow success. `.github/workflows/keep-warm.yml` pings
> `/actuator/health/liveness` on a schedule to avoid it. Point it at liveness,
> not `/actuator/health`: that one runs a database check, and anything that
> touches the database while nobody is using the app keeps the Neon compute
> awake and spends the free tier's 100 CU-hours. Neon itself wakes in under a
> second on the first real query, so it needs no warming. Set the
> `KEEPWARM_URL` repository variable to enable it; forks skip the job.

## Screenshots

**Expenses** — filtering, pagination, CSV export, and the category suggestion.
The LLM is asked to choose from your own category names and its answer is checked
against the database before it is returned, so the label reads *AI suggests* only
when a real model picked a category you actually own. Without a key, or on any
provider failure, the same endpoint answers from the keyword heuristic and the
label drops to *Suggested*.

![Expenses](docs/screenshots/expenses.png)

**Admin** — role-gated view of every user and expense in the system, paged.

![Admin](docs/screenshots/admin.png)

<details>
<summary>More screenshots</summary>

**Budgets** — monthly limits with progress and over-budget state.

![Budgets](docs/screenshots/budgets.png)

**Swagger UI** — the full API surface.

![Swagger](docs/screenshots/swagger.png)

</details>

## Project layout

```
backend/     Spring Boot API
frontend/    Angular app
infra/       Terraform for the S3 + CloudFront frontend
docs/        engineering notes, screenshots
Dockerfile   all-in-one image (SPA + API)
render.yaml  Render blueprint for the API
```

## Dev without full Compose

Needs JDK 21 and Node 22. Maven comes from the committed wrapper (`./mvnw`), so
no local Maven install is required.

```bash
# DB
docker compose up postgres -d

# API
cd backend
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/spendly SEED_DEMO_DATA=true ./mvnw spring-boot:run

# UI
cd frontend
npm install
npm start
```

Frontend expects the API at `http://localhost:8080/api` in dev.

## API

| Method | Path | Notes |
|--------|------|--------|
| POST | `/api/auth/register` | |
| POST | `/api/auth/login` | sets the refresh cookie; 429 while the account is locked |
| POST | `/api/auth/refresh` | cookie → new access token; rotates the cookie |
| POST | `/api/auth/logout` | revokes the refresh token |
| POST | `/api/auth/change-password` | signed in; ends every other session |
| GET/POST/PUT/DELETE | `/api/categories` | |
| GET/POST/PUT/DELETE | `/api/expenses` | query params for filters |
| POST | `/api/expenses/suggest-category` | AI / heuristic category suggestion |
| GET | `/api/expenses/export` | CSV download |
| GET/POST/PUT/DELETE | `/api/budgets` | monthly limits |
| GET | `/api/summary/monthly` | cached (Caffeine, 10 min TTL) |
| GET | `/api/admin/users` | ADMIN, paged |
| GET | `/api/admin/expenses` | ADMIN, paged |

Everything returning a collection of unknown size is paged (`page`, `size`, `sort`). `size` is capped at 100, and `sort` only accepts the fields each endpoint lists — anything else is a 400 that names the field.

## Tests

```bash
cd backend && ./mvnw verify      # 84 tests + a JaCoCo coverage floor (80% lines, 55% branches)
cd frontend && npm run test:ci   # 83 tests, headless Chrome + coverage
cd frontend && npm run lint      # angular-eslint, incl. OnPush and control-flow rules
cd frontend && npm run format:check

docker compose up -d --build     # the browser suite drives the real app
cd frontend && npm run test:e2e  # 22 tests, Playwright + axe-core, light and dark
```

All of it runs on every push (`.github/workflows/ci.yml`), along with
`terraform fmt`/`validate` for `infra/`. `.github/workflows/security.yml` adds
CodeQL for Java and TypeScript, `npm audit` and a Trivy scan of the deployed
image, weekly as well as on push; Dependabot watches Maven, npm, Actions and the
base images.

**Backend** — unit tests (Mockito) cover the services, the AI suggestion fallback
logic, the rate limiter and the JWT secret guard. Integration tests
(Testcontainers + MockMvc) exercise the full HTTP → database path against real
PostgreSQL: 401 for anonymous requests, 403 when a regular user reaches an admin
endpoint, 400 (not 500) for invalid query params, cross-user isolation (user B
cannot read user A's expense), the summary cache returning fresh totals straight
after a write, and a client-supplied currency being ignored. Beyond that:
ownership is checked on **every** endpoint that takes an id, not just expenses;
the session lifecycle (httpOnly cookie, rotation, replay rejected, logout and
password change revoking server-side); the per-account lockout; the perimeter
(metrics closed, probes open, unknown `/api` paths answering JSON 404, security
headers); validation bounds; optimistic locking; and a category rename
refreshing the cached summary. All classes share one Postgres container that is
started once for the JVM — see the comment in `AbstractIntegrationTest` for why
`@Container` on a shared base class breaks cached Spring contexts.

**Frontend** — every page is specced against `HttpTestingController`: create
versus update (and the version sent back), deleting the last row stepping back a
page, stale requests being cancelled when a newer one starts, errors reaching the
notification area. The core layer covers session restore and expiry, the
interceptor's refresh-and-replay (one refresh for many 401s, no recursion on the
refresh call itself), the async guards and the error formatter.

**Browser** — Playwright against the stack `docker compose` brings up. A
session suite checks what only a real browser can: that the refresh cookie is
`httpOnly`, that a lost access token is renewed on reload, and that a copy of
the cookie is dead after logout.
Besides accessibility it pins the browser to `Europe/Vienna` and asserts the
expense list renders the dates the API actually returned: `spentOn` is a
`LocalDate`, and formatting it in a fixed timezone shifted every date a day
earlier for anyone east of UTC while looking correct on a UTC CI runner.

**Accessibility** — axe-core runs over every page and both admin tabs, once per theme, against the
stack that `docker compose` brings up, failing on any WCAG 2.1 A/AA violation. It
also asserts the two things a rules engine can't infer: that the skip link is the
first tab stop and actually moves focus into `<main>`, and that every form control
on the busiest page has an accessible name. This took the app from 24 automated
violations to 0 — see [docs/ENGINEERING_NOTES.md](docs/ENGINEERING_NOTES.md) for
what that number does and doesn't mean.

Line coverage is ~85% on the backend and ~88% on the frontend.

## License

MIT
