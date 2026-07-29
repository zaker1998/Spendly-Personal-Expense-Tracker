# Spendly — Expense Tracker MVP

Personal expense tracker built as a portfolio project to demonstrate enterprise full-stack patterns common in Austria/Germany: **Java 21 · Spring Boot · JWT · JPA/Hibernate · Flyway · PostgreSQL · Angular · Docker · GitHub Actions · Testcontainers**.

[![CI](https://github.com/zaker1998/Spendly-Personal-Expense-Tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/zaker1998/Spendly-Personal-Expense-Tracker/actions/workflows/ci.yml)

## Features

- JWT authentication with `USER` and `ADMIN` roles
- CRUD for expenses and categories (default categories seeded on register)
- Paginated expense list with filters (date range, category, amount, search)
- Monthly summary by category
- Admin endpoints to list users and all expenses
- OpenAPI / Swagger UI
- Entity graphs to avoid N+1 on expense + category loads
- Unit tests + Testcontainers integration tests
- Docker Compose full stack + single-image cloud deploy

## Architecture

```text
Angular SPA  --Bearer JWT-->  Spring Boot REST API  -->  PostgreSQL
                                      |
                               Flyway migrations
                               springdoc OpenAPI
```

```text
backend/                 Spring Boot API (Java 21)
frontend/                Angular SPA
Dockerfile               Production image (SPA + API together)
render.yaml              Render Blueprint
docker-compose.yml       Local multi-container stack
.github/workflows/ci.yml
```

## Quick start (Docker Compose — local)

```bash
docker compose up --build
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:4200 |
| Backend API | http://localhost:8081/api |
| Swagger UI | http://localhost:8081/swagger-ui.html |

> Host ports **5433** (Postgres) and **8081** (API) avoid clashes with other local Docker stacks.

### Demo accounts (seeded)

| Email | Password | Role |
|-------|----------|------|
| `admin@spendly.app` | `Admin123!` | ADMIN |
| `demo@spendly.app` | `Demo123!` | USER |

## Live deploy (Render)

One web service runs **Angular + Spring Boot** from the root `Dockerfile`. Postgres is provisioned by the Blueprint.

1. Push this repo to GitHub (already done).
2. Open [Render Blueprints](https://dashboard.render.com/blueprints) → **New Blueprint Instance**.
3. Connect `zaker1998/Spendly-Personal-Expense-Tracker` and apply `render.yaml`.
4. Wait for the first deploy (free tier can take several minutes).
5. Open the service URL — same demo logins as above.
6. Optional: set the GitHub repo **Website** field to that URL (Settings → General → Website).

Swagger on deploy: `https://<your-service>.onrender.com/swagger-ui.html`

> Free Render services spin down after idle time; the first request after sleep can take ~30–60s.

### Production image locally

```bash
docker build -t spendly .
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/spendly \
  -e SPRING_DATASOURCE_USERNAME=spendly \
  -e SPRING_DATASOURCE_PASSWORD=spendly \
  -e JWT_SECRET=spendly-dev-secret-key-change-me-in-production-32chars-min \
  spendly
```

## Screenshots

![Login](docs/screenshots/login.png)

![Dashboard](docs/screenshots/dashboard.png)

![Expenses](docs/screenshots/expenses.png)

![Swagger](docs/screenshots/swagger.png)

## Local development

### Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 20+ / npm
- Docker (for Postgres and Testcontainers)

### Backend

```bash
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"   # macOS Homebrew
docker compose up postgres -d
cd backend
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/spendly mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm start
```

App: http://localhost:4200 (dev API: `http://localhost:8080/api`)

## API overview

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/register` | Register + JWT |
| POST | `/api/auth/login` | Login + JWT |
| GET/POST/PUT/DELETE | `/api/categories` | Category CRUD |
| GET/POST/PUT/DELETE | `/api/expenses` | Expense CRUD + filters |
| GET | `/api/summary/monthly` | Totals by category |
| GET | `/api/admin/users` | Admin: list users |
| GET | `/api/admin/expenses` | Admin: all expenses |

## Tests

```bash
cd backend
mvn test
```

Includes:
- `JwtServiceTest` / `CategoryServiceTest` (unit)
- `ExpenseApiIntegrationTest` (Testcontainers + MockMvc)

CI runs on every push: Maven tests → Angular build → Docker image builds.

## CV / LinkedIn (copy-paste)

**Short (CV project line):**

> Spendly — Full-stack expense tracker (Java 21, Spring Boot, Angular, PostgreSQL). JWT roles, pagination/filters, Flyway, Testcontainers, Docker, GitHub Actions.  
> https://github.com/zaker1998/Spendly-Personal-Expense-Tracker

**Longer (LinkedIn Featured / About):**

> Built Spendly, a production-style personal expense tracker aimed at enterprise Java stacks used in Austria/Germany. Spring Boot REST API with JWT auth (USER/ADMIN), JPA (no N+1), Flyway migrations, OpenAPI, and Testcontainers integration tests. Angular frontend with dashboard, filters, and category management. Fully containerized with Docker Compose and CI on GitHub Actions; deployable as a single Docker image on Render.

## License

MIT
