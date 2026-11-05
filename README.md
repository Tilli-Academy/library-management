# PageTurn — Library Management System

A full-stack library management app built with **React + TypeScript** and **Java Spring Boot REST API**.

## Features
- Add and browse books
- Search by title or author
- Filter all / available / issued books
- Issue and return books
- Delete books (issued books are protected)
- Dashboard statistics
- Responsive UI, loading states, validation and error messages
- H2 file database: data persists after restarting the backend

## Tech stack
- Frontend: React 18, TypeScript, Vite, Lucide icons, custom responsive CSS
- Backend: Java 17, Spring Boot, Spring Web, Spring Data JPA, Bean Validation
- Database: H2 (file-based)

## Run locally

### 1. Start the backend
Install JDK 17+ and Maven, then:

```bash
cd backend
mvn spring-boot:run
```

API runs at `http://localhost:8080`. H2 console: `http://localhost:8080/h2-console`  
JDBC URL: `jdbc:h2:file:./data/librarydb` | User: `sa` | Password: *(blank)*

### 2. Start the frontend
In a second terminal, install Node.js 20+ then:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

## REST API
| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/books` | List all books |
| POST | `/api/books` | Add a book (`{ "title": "...", "author": "..." }`) |
| PATCH | `/api/books/{id}/issue` | Issue an available book |
| PATCH | `/api/books/{id}/return` | Return an issued book |
| DELETE | `/api/books/{id}` | Delete an available book |

## Suggested GitHub polish
1. Add screenshots/GIF of the app to this README.
2. Add unit and integration tests for the service/API.
3. Add Docker Compose for frontend + backend.
4. Add authentication and role-based access for librarian/member.
5. Deploy the API and UI, then add live demo links.

## Important
This is a portfolio starter, not a production library system. For production, add authentication, audit history, pagination, structured exception handling, tests, and a managed database such as PostgreSQL.
