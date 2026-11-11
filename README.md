# PageTurn — Library Management System

A full-stack library app: members browse the catalogue and track the books they borrowed; librarians
manage books, lend and receive copies, and manage member accounts. Installable on phones as an app (PWA).

## Features

**Members**
- Sign up / sign in
- Search the catalogue by title, author, genre or ISBN; filter to available books
- "My books": current loans with due dates and overdue warnings, plus borrowing history

**Librarians** (everything members can do, plus)
- Dashboard: titles, copies, books on loan, overdue loans, members
- Add, edit and delete books (ISBN check-digit validation, multiple copies per title)
- Lend a copy to a member, mark loans returned; lists of active / overdue / returned loans
- Promote members to librarian, block and unblock accounts

**Lending rules:** 14-day loans, at most 5 books per member, no new loans while a member has overdue books,
one copy of the same title per member. Rules are enforced by the server and the database.

## Tech stack

| Part | Technology |
|---|---|
| Frontend | React 19, TypeScript, Vite 8, React Router 7, TanStack Query, React Hook Form + Zod, Tailwind CSS 4, vite-plugin-pwa |
| Backend | Java 21, Spring Boot 4.1, Spring Security 7, Spring Data JPA, Bean Validation |
| Database | PostgreSQL 16, Flyway migrations |
| Tests | JUnit 5 + Testcontainers (real PostgreSQL), Vitest + Testing Library |
| Delivery | Docker Compose, nginx, GitHub Actions CI, Dependabot |

## Security

See [SECURITY.md](SECURITY.md) for the full list of protections and the audit results. In short: sign-in sessions
live in an HttpOnly + SameSite=Strict cookie, every change requires a CSRF token, roles are checked on the server
for every request, passwords are hashed with BCrypt, sign-in is rate-limited, inputs are validated on both sides,
strict security headers (including a Content-Security-Policy) are sent, and no secrets live in the code.

## Run it locally

You need Docker, JDK 21 and Node.js 20.19+ (or 22+).

```bash
# 1. Create your private settings file and fill in the blanks (instructions are inside).
cp .env.example .env
#    PAGETURN_DB_PASSWORD   -> openssl rand -hex 24
#    PAGETURN_JWT_SECRET    -> openssl rand -hex 48
#    PAGETURN_ADMIN_EMAIL / PAGETURN_ADMIN_PASSWORD -> your first librarian account

# 2. Start PostgreSQL
docker compose up -d

# 3. Start the backend (http://127.0.0.1:8097)
cd backend && ./mvnw spring-boot:run

# 4. In a second terminal, start the frontend (http://localhost:5177)
cd frontend && npm install && npm run dev
```

Sign in with the librarian email and password from `.env`. New sign-ups are always members.

### Everything in containers

```bash
docker compose --profile app up -d --build     # open http://localhost:8098
```

For a real deployment, put HTTPS in front (a reverse proxy or your host's load balancer) and set
`PAGETURN_PUBLIC_ORIGIN` in `.env` to the exact public address, e.g. `https://library.example.com`.

## Tests

```bash
cd backend && ./mvnw verify          # 48 tests, starts a throw-away PostgreSQL in Docker
cd frontend && npm run lint && npm test && npm run build
```

## REST API (summary)

All endpoints are under `/api/v1`. Changes (`POST`/`PUT`/`PATCH`/`DELETE`) need the `X-XSRF-TOKEN` header.

| Who | Method | Endpoint | Purpose |
|---|---|---|---|
| Anyone | GET | `/auth/csrf` | Get the CSRF cookie |
| Anyone | POST | `/auth/register`, `/auth/login`, `/auth/logout` | Account and session |
| Signed in | GET | `/auth/me` | Current user |
| Signed in | GET | `/books?q=&available=&page=&size=` | Search the catalogue |
| Signed in | GET | `/books/{id}` | One book |
| Signed in | GET | `/me/loans?returned=` | Your own loans |
| Librarian | POST / PUT / DELETE | `/admin/books`, `/admin/books/{id}` | Manage books |
| Librarian | GET / POST | `/admin/loans?status=`, `/admin/loans` | List / lend |
| Librarian | POST | `/admin/loans/{id}/return` | Mark returned |
| Librarian | GET | `/admin/stats` | Dashboard numbers |
| Librarian | GET / PATCH | `/admin/users`, `/admin/users/{id}` | Members, roles, blocking |

Errors are returned as JSON "problem details": `{"status":409,"title":"Conflict","detail":"..."}`.
