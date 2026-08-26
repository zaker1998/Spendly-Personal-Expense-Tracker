# Spendly

Personal expense tracker with Spring Boot + Angular.

![Spendly dashboard](docs/screenshots/dashboard.png)

**Live:** https://spendly-33ek.onrender.com

**Stack:** Java 21, Spring Boot, JWT, JPA/Hibernate, Flyway, PostgreSQL, Angular, Docker, GitHub Actions, Testcontainers, Caffeine, Groq AI

[![CI](https://github.com/zaker1998/Spendly-Personal-Expense-Tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/zaker1998/Spendly-Personal-Expense-Tracker/actions/workflows/ci.yml)

> The live demo runs on Render's free tier — the first request after idle can take up to a minute while the instance wakes up.

## Features

- Register / login (JWT), roles: `USER` and `ADMIN`
- Expenses & categories CRUD
- **AI category suggestion** — an LLM (with keyword-heuristic fallback) suggests the right category from the expense description
- Filters + pagination on expense list
- Monthly dashboard with month picker + category chart, **cached with Caffeine**
- Budgets with progress / over-budget
- CSV export
- **Rate limiting** on auth + AI endpoints (per-IP fixed window, HTTP 429 + `Retry-After`)
- Consistent JSON errors: structured 400s for bad input, 401/403 bodies, no leaking 500s
- Admin UI (users + all expenses), paged
- Swagger UI
- Prometheus metrics at `/actuator/prometheus`, including cache hit/miss counters
- Unit tests + integration tests against real PostgreSQL via Testcontainers
- `docker compose` for local run

## Architecture

```mermaid
flowchart LR
    subgraph Client
        NG[Angular 19 SPA]
    end

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

    NG -->|"HTTPS /api (Bearer token)"| SEC --> API --> SVC
    SVC --> CACHE
    SVC --> JPA --> DB
    SVC --> AI -->|optional| LLM
```

### Design decisions

Longer write-ups of the trickier calls — including four bugs I found and fixed
in my own code — are in [docs/ENGINEERING_NOTES.md](docs/ENGINEERING_NOTES.md).

- **JWT (stateless) over sessions** — the API stays horizontally scalable and the SPA keeps a single token; short expiry + HTTPS mitigate token theft.
- **Flyway with `ddl-auto: validate`** — the schema is owned by versioned SQL migrations, never by Hibernate auto-DDL; production and tests run identical schemas.
- **AI suggestions are validated server-side** — the LLM is asked to pick from the user's own category names, and its answer is checked against the database before being returned. A hallucinated category can never reach the client. On any provider failure (timeout, rate limit, missing key) the endpoint degrades to a keyword heuristic instead of erroring.
- **Caffeine instead of Redis for caching** — the app runs as a single instance, so an in-process cache gives the same latency win without extra infrastructure. Writes evict exactly the affected user+month entry, not the whole cache; a 10-minute TTL bounds staleness.
- **Cache eviction happens after commit** — the evict is raised as a domain event and consumed with `@TransactionalEventListener(AFTER_COMMIT)`. Evicting inline during the write left a window where a concurrent read could repopulate the cache from uncommitted state and then serve stale totals for the rest of the TTL; it also meant a rolled-back write still dropped a valid entry.
- **Single currency, enforced end to end** — amounts are summed in the monthly summary and in budget progress, so mixed currencies would produce a total that looks right and isn't. Rather than half-build multi-currency, the API rejects the concept: the server sets the code, a CHECK constraint backs it, and one constant (`AppCurrency`) is the only place to change when FX rates are actually modelled.
- **Testcontainers over H2 for integration tests** — tests run against the same PostgreSQL version as production, so dialect-specific behaviour (e.g. in filtered queries) is actually covered.
- **In-memory rate limiting** — login/register and the AI endpoint are the two abuse targets (credential brute-force, external API quota). A per-IP fixed window in process memory is enough for a single instance — same reasoning as Caffeine over Redis. `X-Forwarded-For` is only trusted when the deployment declares a proxy in front (`RATE_LIMIT_BEHIND_PROXY`), because otherwise the header is client-supplied and rotating it would hand out an unlimited number of login attempts.
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
Render demo both set on purpose. It is **off by default** so no real deployment
ever comes up with a known admin password.

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
| `AI_MODEL` | `llama-3.1-8b-instant` | Optional override |
| `AI_SUGGESTIONS_ENABLED` | `true` | Kill switch |

### Rate limiting

| Env var | Default | Purpose |
|---------|---------|---------|
| `RATE_LIMIT_ENABLED` | `true` | Kill switch |
| `RATE_LIMIT_AUTH_PER_MINUTE` | `10` | Per IP, `/api/auth/**` |
| `RATE_LIMIT_AI_PER_MINUTE` | `30` | Per IP, AI suggestions |
| `RATE_LIMIT_BEHIND_PROXY` | `false` | Read the client IP from `X-Forwarded-For`. Only enable where a proxy you control rewrites it |

### Other environment variables

| Env var | Default | Purpose |
|---------|---------|---------|
| `JWT_SECRET` | dev value | Must be ≥ 32 bytes; the app refuses to start otherwise |
| `SEED_DEMO_DATA` | `false` | Create the demo/admin accounts above |
| `DATABASE_URL` | — | `postgres://user:pass@host/db` style URL. Split into the `SPRING_DATASOURCE_*` vars by `docker/entrypoint.sh`; set them directly instead if you prefer |
| `SPRING_DATASOURCE_URL` | local Postgres | JDBC URL. Takes precedence over `DATABASE_URL` |
| `PORT` | — | Mapped to `SERVER_PORT`; set automatically by most PaaS hosts |

## Deployment

The demo runs as a single container (SPA + API, see the root `Dockerfile`) on
Render's free tier, against a **Neon** Postgres instance.

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

> Cold start: on the free tier the instance sleeps after ~15 minutes idle and
> takes up to a minute to wake. `/actuator/health` includes a database check, so
> a scheduled ping against it keeps both the instance and the Neon compute warm.

## Screenshots

**Expenses** — filtering, pagination, CSV export, and the category suggestion.
The LLM is asked to choose from your own category names; when no key is
configured (as in this shot) the endpoint degrades to the keyword heuristic
rather than failing.

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
Dockerfile   optional all-in-one image (SPA + API)
render.yaml  optional Render blueprint
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
| POST | `/api/auth/login` | |
| GET/POST/PUT/DELETE | `/api/categories` | |
| GET/POST/PUT/DELETE | `/api/expenses` | query params for filters |
| POST | `/api/expenses/suggest-category` | AI / heuristic category suggestion |
| GET | `/api/expenses/export` | CSV download |
| GET/POST/PUT/DELETE | `/api/budgets` | monthly limits |
| GET | `/api/summary/monthly` | cached (Caffeine, 10 min TTL) |
| GET | `/api/admin/users` | ADMIN, paged |
| GET | `/api/admin/expenses` | ADMIN, paged |

Everything returning a collection of unknown size is paged (`page`, `size`, `sort`).

## Tests

```bash
cd backend && ./mvnw test        # 28 tests, JaCoCo report in target/site/jacoco
cd frontend && npm run test:ci   # 24 tests, headless Chrome + coverage
```

Both suites run on every push (`.github/workflows/ci.yml`).

**Backend** — unit tests (Mockito) cover the services, the AI suggestion fallback
logic, the rate limiter and the JWT secret guard. Integration tests
(Testcontainers + MockMvc) exercise the full HTTP → database path against real
PostgreSQL: 401 for anonymous requests, 403 when a regular user reaches an admin
endpoint, 400 (not 500) for invalid query params, cross-user isolation (user B
cannot read user A's expense), the summary cache returning fresh totals straight
after a write, and a client-supplied currency being ignored. All classes share
one container via `AbstractIntegrationTest`.

**Frontend** — the core layer is specced: session persistence and restore,
the interceptor's token attachment and 401-logout rule (and the login request it
must *not* log out), the three route guards, and API parameter building.

Line coverage is ~77% on the backend and ~70% on the frontend core.

## License

MIT
