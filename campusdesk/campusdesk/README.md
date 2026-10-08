# CampusDesk

IT incident management platform for **TechNova Solutions**. Employees report technology problems, an
administrator assigns a technician, and every ticket follows a controlled life cycle with comments and a
full history. Backend in **Spring Boot + PostgreSQL + JWT**, frontend in **HTML5, CSS3 and vanilla JavaScript**.

```
OPEN -> ASSIGNED -> IN_PROGRESS -> RESOLVED -> CLOSED
```

## Team
- Yeison Ferney Pallares Duque
- _Add the other members here, if any_

## Tech stack
| Layer | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.1 (Web MVC, Data JPA, Validation, Security) |
| Auth | Spring Security + JWT (HMAC256, `java-jwt`), BCrypt password hashing |
| Database | PostgreSQL (Hibernate / Spring Data JPA) |
| API docs | springdoc-openapi (Swagger UI) |
| Frontend | HTML5, CSS3, JavaScript (ES modules, `fetch` + `async/await`). No frameworks. Served by nginx |
| Deployment | Docker Compose (PostgreSQL 17, pgAdmin, backend, nginx) |
| Tests | JUnit 5, Mockito, Spring Boot Test (H2 only for tests) |
| Build | Gradle (wrapper included) |

## Project structure
```
campusdesk/
├── docker-compose.yml             PostgreSQL + pgAdmin + backend + frontend (nginx)
├── .env                           credentials for compose and for running from the IDE (ignored by Git)
├── .env.example                   template for .env
├── docker/nginx.conf              nginx settings for the frontend container
├── carpeta_scripts/               SQL run by PostgreSQL on its first start
│   ├── 01_schema.sql              tables + system roles
│   └── optional/sample_data.sql   optional demo users and tickets
├── backend/                       Spring Boot project (Gradle) + Dockerfile
│   └── src/main/java/com/technova/campusdesk/
│       ├── controller/            HTTP only: receives requests, delegates to services
│       ├── service/               business rules (life cycle, permissions, statistics)
│       ├── repository/            Spring Data JPA
│       ├── entity/                User, Role, Ticket, Comment, StatusHistory + enums
│       ├── dto/                   request/response records
│       ├── security/              JWT filter/util, SecurityConfig, UserPrincipal
│       ├── exception/             custom exceptions + GlobalExceptionHandler
│       └── config/                CORS, OpenAPI, properties, first-admin bootstrap
├── frontend/                      ONLY html, css and js (no build step)
│   ├── index.html, dashboard.html, tickets.html, ticket-detail.html, admin.html
│   ├── css/styles.css
│   └── js/                        config.js, api.js, session.js, ui.js, layout.js, pages/*
└── docs/                          architecture, test cases, evidence checklist
```

## Prerequisites
- Docker Desktop (Docker Compose v2)
- A modern browser

For development without Docker you also need JDK 21 (the compose database can still be used).

## Run everything with Docker
```bash
docker compose up -d --build
```
| Service | URL |
| --- | --- |
| **Frontend** | http://localhost:5500 |
| **Backend API** | http://localhost:8080 |
| **Swagger UI** | http://localhost:8080/swagger-ui.html (log in, press **Authorize**, paste the token) |
| pgAdmin | http://localhost:5050 (add a server: host `postgres`, port `5432`, credentials from `.env`) |
| PostgreSQL | `localhost:5433` from your machine |

The first build downloads dependencies and can take a few minutes. Follow the backend with `docker compose logs -f backend`.
Sign in with `ADMIN_EMAIL` / `ADMIN_PASSWORD` from `.env` (the first administrator is created on startup).

Useful commands:
```bash
docker compose down            # stop (data is kept in the postgres_data volume)
docker compose down -v         # stop and DELETE the database
docker compose up -d --build backend   # rebuild only the backend after changing Java code
```
The frontend folder is mounted into nginx, so edits to HTML/CSS/JS show up with a browser refresh.

### Configuration
All credentials live in `.env` (copy `.env.example` if it does not exist). It is ignored by Git, so no password,
JWT secret or database credential is committed.

| Variable | Meaning |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | PostgreSQL database and user |
| `PGADMIN_EMAIL`, `PGADMIN_PASSWORD` | pgAdmin login |
| `JWT_SECRET` | Signing key, **32+ characters** (`openssl rand -base64 48`) |
| `JWT_EXPIRATION` | Token lifetime, for example `8h` or `30m` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_FULL_NAME` | First administrator (password: 8+ chars, upper, lower, number, symbol) |
| `CORS_ALLOWED_ORIGINS` | Frontend origins allowed by CORS (default `http://localhost:5500,http://127.0.0.1:5500`) |

If you change the PostgreSQL credentials after the first start, run `docker compose down -v` once so the database is recreated.

### Optional demo data
After the backend has started once (it creates the first administrator):
```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/optional/sample_data.sql'
```
Creates two technicians, two users and six tickets in different states (demo password `Demo-Pass-123!`, development only).

### Running the backend from the IDE instead
Start only the database (`docker compose up -d postgres`) and run `CampusDeskApplication`. The app reads the
root `.env` (found from the project root or from `backend/`) and connects to `localhost:5433`.
In IntelliJ set the working directory to the project root or `backend`. Then serve the frontend with
`docker compose up -d frontend` or any static server on port 5500.

## Roles
| Role | What it can do |
| --- | --- |
| **ADMIN** | See all tickets, list technicians, assign/reassign, list users, create users with any role, create custom roles, view global statistics and any history |
| **TECHNICIAN** | See tickets assigned to them, move them to `IN_PROGRESS` and `RESOLVED`, comment, view their history |
| **USER** | Register, create requests, see only their own, edit while open and unassigned, comment on active tickets, close a resolved ticket |
| **Custom** | Created by an administrator in *Administration > Roles* by combining permissions from the catalog |

Public registration **always** creates a `USER`. Administrators and technicians are provisioned securely: the
first admin comes from `application-dev.properties`, and every other privileged account is created by an
administrator in *Administration > Users*.

Permissions are enforced in the backend (`@PreAuthorize` + ownership/state rules in the services). The UI only
hides what you cannot do.

## Main API endpoints
Full contract in Swagger. Base: `/api`.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/auth/register`, `/auth/login` | Register (USER) / log in (JWT) |
| GET | `/auth/me` | Current user and permissions |
| GET | `/users/technicians` | Assignable technicians |
| GET | `/tickets` | Authorized tickets, filters `status`, `priority`, `category` |
| GET/POST | `/tickets`, `/tickets/{id}` | List, detail, create |
| PUT | `/tickets/{id}` | Edit (requester, open and unassigned) |
| PATCH | `/tickets/{id}/assign` | Assign / reassign (admin) |
| PATCH | `/tickets/{id}/status` | Change status |
| GET/POST | `/tickets/{id}/comments` | Comments |
| GET | `/tickets/{id}/history` | Status history |
| GET | `/reports/summary` | Indicators, counted in PostgreSQL |
| GET/POST/PATCH | `/users`, `/users/{id}` | User administration |
| GET/POST/PUT/DELETE | `/roles`, `/roles/{name}` | Role administration |
| GET | `/roles/permissions` | Permission catalog |

Errors share one JSON shape and never include tokens, passwords or stack traces. HTTP codes used:
200, 201, 204, 400, 401, 403, 404, 409, 500.

## Tests
```bash
cd backend
./gradlew test
```
Unit tests cover the life-cycle rules and authorization (Mockito), JWT creation/validation, and a smoke test
that boots the full context against an in-memory H2 database. Mandatory scenarios CP-01 to CP-16 are listed in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#mandatory-test-cases).

## Demonstration flow
1. Register `user@...` and create a ticket (`OPEN`).
2. Log in as ADMIN, assign a technician (`ASSIGNED`).
3. Log in as the technician: *Start working* (`IN_PROGRESS`), comment, *Mark as resolved* (`RESOLVED`).
4. Log in as the requester: *Confirm and close* (`CLOSED`). Try commenting: it is rejected.
5. Show history, dashboard and Swagger.

## Evidence
Place screenshots in [docs/evidence](docs/evidence): login, ticket management, technician assignment,
status changes, history and comments, statistics dashboard, Swagger.

## Not included
Optional challenges 1 (pagination), 2 (advanced statistics) and 5 (extra UX) are not implemented. History also
records reassignments (partial challenge 3), and the automated tests cover part of challenge 4.
