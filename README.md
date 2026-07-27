# Spendly — Expense Tracker MVP

Personal expense tracker built as a portfolio project to demonstrate enterprise full-stack patterns common in Austria/Germany: **Java 21 · Spring Boot · JWT · JPA/Hibernate · Flyway · PostgreSQL · Angular · Docker · GitHub Actions · Testcontainers**.

## Features

- JWT authentication with `USER` and `ADMIN` roles
- CRUD for expenses and categories (default categories seeded on register)
- Paginated expense list with filters (date range, category, amount, search)
- Monthly summary by category
- Admin endpoints to list users and all expenses
- OpenAPI / Swagger UI
- Entity graphs to avoid N+1 on expense + category loads
- Unit tests + Testcontainers integration tests
- Docker Compose full stack

## Architecture

```text
Angular SPA  --Bearer JWT-->  Spring Boot REST API  -->  PostgreSQL
                                      |
                               Flyway migrations
                               springdoc OpenAPI
```

```text
full_stack_java_project/
  backend/                 Spring Boot API
  frontend/                Angular SPA (+ nginx in Docker)
  docker-compose.yml
  .github/workflows/ci.yml
```

## Quick start (Docker)

```bash
docker compose up --build
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:4200 |
| Backend API | http://localhost:8081/api |
| Swagger UI | http://localhost:8081/swagger-ui.html |

> Host ports **5433** (Postgres) and **8081** (API) avoid clashes with other local Docker stacks that may already use 5432/8080. Inside Compose, services still talk on the default container ports.

### Demo accounts (seeded)

| Email | Password | Role |
|-------|----------|------|
| `admin@spendly.app` | `Admin123!` | ADMIN |
| `demo@spendly.app` | `Demo123!` | USER |

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
# if using Compose Postgres published on 5433:
# SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/spendly
mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm start
```

App: http://localhost:4200 (local API: `http://localhost:8080/api` or Compose API: `http://localhost:8081/api`)

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

## Screenshots

Add screenshots under `docs/screenshots/` after first local run (login, dashboard, expenses).

## License

MIT
