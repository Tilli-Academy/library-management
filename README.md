# PageTurn — Library Management System

A full-stack library app: members browse the catalogue and track the books they borrowed; librarians
manage books, lend and receive copies, and manage member accounts. Installable on phones as an app (PWA).

The first screen asks **"I'm a librarian" or "I'm a member"**. Members can sign up themselves; librarian
accounts can only sign in. They are created by the head librarian, so nobody can make themselves a librarian.

## Features

**Members**
- Sign up (name, phone number, email, password) / sign in
- Search the catalogue by title, author, genre or ISBN; filter to available books
- Open any book for its **details page**: cover picture, about the book, about the author, availability
- See if a book is available, and if all copies are out, **when it is expected back** ("Expected back in 4 days")
- "My books": current loans with due dates and overdue warnings, borrowing history, and their phone number
- See **Coming soon** books (announced, not in stock yet), optionally with an expected arrival date
- **Request a book** the library doesn't have, and **like** other members' requests. When a request reaches
  `PAGETURN_LIKES_TO_STOCK` likes (default 5), it is approved automatically and the book appears in the
  catalogue as "coming soon"

**Librarians** (everything members can do, plus)
- Dashboard: titles, copies, books on loan, overdue loans, members
- Add, edit and delete books (ISBN check-digit validation, multiple copies per title), with a **cover picture**
  (JPG/PNG up to 3 MB), **about the book** and **about the author**
- Lend a copy to a member, mark loans returned; lists of active / overdue / returned loans (who borrowed, who returned)
- Add books as **Coming soon** (with an optional arrival date); click **Copies arrived** to put them in stock
- See all requests sorted by likes (with who asked), and **Get it** or **Decline** a request by hand
- Overdue books on the dashboard with the member's phone: a **Call** button, and **Mark as called** so every
  librarian can see who was already contacted
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
cd backend && ./mvnw verify          # 63 tests, starts a throw-away PostgreSQL in Docker
cd frontend && npm run lint && npm test && npm run build
```

## REST API (summary)

All endpoints are under `/api/v1`. Changes (`POST`/`PUT`/`PATCH`/`DELETE`) need the `X-XSRF-TOKEN` header.

| Who | Method | Endpoint | Purpose |
|---|---|---|---|
| Anyone | GET | `/auth/csrf` | Get the CSRF cookie |
| Anyone | POST | `/auth/register`, `/auth/login`, `/auth/logout` | Account and session |
| Signed in | GET / PATCH | `/auth/me` | Current user / update your phone number |
| Signed in | GET | `/books?q=&available=&comingSoon=&page=&size=` | Search the catalogue |
| Signed in | GET | `/books/{id}` | One book |
| Signed in | GET | `/books/{id}/cover` | Cover picture (JPEG) |
| Librarian | PUT / DELETE | `/admin/books/{id}/cover` | Upload (multipart field `file`) / remove a cover |
| Signed in | GET | `/me/loans?returned=` | Your own loans |
| Librarian | POST / PUT / DELETE | `/admin/books`, `/admin/books/{id}` | Manage books |
| Librarian | GET / POST | `/admin/loans?status=`, `/admin/loans` | List / lend |
| Librarian | POST | `/admin/loans/{id}/return` | Mark returned |
| Librarian | POST | `/admin/loans/{id}/reminded` | Record that the member was called |
| Librarian | GET | `/admin/stats` | Dashboard numbers |
| Signed in | GET / POST | `/suggestions?status=`, `/suggestions` | List / create book requests |
| Signed in | POST / DELETE | `/suggestions/{id}/like` | Like / un-like a request |
| Librarian | POST | `/admin/suggestions/{id}/approve`, `/decline` | Decide a request by hand |
| Librarian | GET / PATCH | `/admin/users`, `/admin/users/{id}` | Members, roles, blocking |

Errors are returned as JSON "problem details": `{"status":409,"title":"Conflict","detail":"..."}`.

## Credits

- Welcome-page photo: *Strahov Theological Hall, Prague* by Jorge Royan,
  [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/), via
  [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Strahov_Theological_Hall,_Prague_-_7565.jpg).
  Resized and darkened for this app; the adapted image is shared under the same licence.
- Font: [Cormorant Garamond](https://fonts.google.com/specimen/Cormorant+Garamond) (SIL Open Font License), bundled via Fontsource.
