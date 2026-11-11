# Security

How PageTurn protects its users, and the results of the security review done during the v2 upgrade
(November 2026). Found a problem? Please open a private security advisory on GitHub instead of a public issue.

## Protections

| Area | What the app does |
|---|---|
| Secrets | No secrets in code or git. All come from environment variables / a git-ignored `.env`. The backend refuses to start without a strong `PAGETURN_JWT_SECRET`. The full git history was scanned with gitleaks (no leaks), and CI scans every push. |
| Sign-in | Session token in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie (JavaScript can't read it). Tokens are signed (HS256), expire after 8 hours, and are revoked on sign-out or when an account is blocked. |
| Passwords | BCrypt (cost 12) with a salt per password. 12–72 characters. Same error and similar timing for "wrong email" and "wrong password". |
| Personal data | Phone numbers are only visible to the member themselves and to librarians, never to other members. Members can only change their own number. Call links contain digits only. |
| Book requests | One like per member per request (enforced by the database). Likes are counted under a row lock, so simultaneous likes can't be lost or add a book twice. At most 5 open requests per member. Members don't see who requested what; librarians do. |
| Access control | Deny by default. Every request is checked on the server; role and "blocked" status are read from the database on every request. Librarian endpoints are checked twice (URL rule + method rule). Members can only ever see their own loans. |
| First admin | Created only from `PAGETURN_ADMIN_EMAIL` / `PAGETURN_ADMIN_PASSWORD`. Sign-up always creates members; there is no "make me admin" endpoint. Librarians can't change themselves, and two librarians can't remove each other at the same moment. |
| CSRF | Every change needs the `X-XSRF-TOKEN` header matching the `XSRF-TOKEN` cookie, plus SameSite=Strict cookies and an Origin allow-list. |
| XSS | React escapes everything it shows; no `innerHTML`. Strict Content-Security-Policy (no inline or third-party scripts). The API only returns JSON. |
| Input | Validated in the browser (Zod) and again on the server (Bean Validation) with length limits; invisible control characters are refused; ISBN check digits verified. The database has CHECK / UNIQUE / FOREIGN KEY rules too. |
| SQL injection | Only parameterised queries (Spring Data JPA); search wildcards are escaped. |
| Rate limiting | Per IP: 10 sign-in/sign-up attempts and 300 API requests per minute. Per email: 5 failed sign-ins per 15 minutes. Attempts are counted before the password check, so parallel guesses count too. |
| Request size | Bodies over 64 KB are refused before parsing. |
| Headers | CSP, HSTS, X-Frame-Options DENY, nosniff, Referrer-Policy no-referrer, Permissions-Policy, COOP. |
| CORS | Exact origins only (wildcards are refused at startup). |
| Errors & debug | No stack traces or internal messages sent to clients; SQL logging off; no H2 console; only `/actuator/health` exposed (no details); unknown paths denied; no source maps. |
| Containers | Both run as non-root users. The database and the backend aren't reachable from outside; only nginx is published, on 127.0.0.1. nginx blocks hidden files (`.env`, `.git`). |
| Offline app (PWA) | Only the app's own files are cached; API responses (personal data) are never stored offline. Cached data is cleared when the user changes. |
| Dependencies | Latest Spring Boot / React / Vite releases. `npm audit --omit=dev`: 0 vulnerabilities. Dependabot opens weekly update PRs. |

## Audit results

Each finding was reproduced, fixed, and covered by an automated test where possible.

| # | Severity | Finding | Status |
|---|---|---|---|
| 1 | High | `http.oauth2ResourceServer()` silently turned CSRF protection off for cookie-based sessions | Fixed: custom token filter. Tests check that changes without the token are refused. |
| 2 | Medium | Percent-encoded URLs (e.g. `/%61pi/v1/auth/login`) skipped the per-IP rate limit | Fixed: filters use the decoded path, and nginx forwards a normalised path. Regression test. |
| 3 | Medium | Behind a front proxy, all users shared one rate-limit bucket | Fixed: nginx takes the real client IP only from a configured trusted proxy (`PAGETURN_TRUSTED_PROXY_CIDR`). |
| 4 | Medium | Docker deployment refused sign-in: the browser's Origin wasn't on the CORS allow-list | Fixed: `PAGETURN_PUBLIC_ORIGIN` |
| 5 | Low | `X-Forwarded-Host` from clients was passed through, so the Origin check could be bypassed by non-browser clients | Fixed: nginx clears forwarded host/port headers |
| 6 | Low | HSTS missing in the Docker setup | Fixed: added in nginx |
| 7 | Low | Two librarians could demote or block each other at the same moment, leaving none | Fixed: row locks plus a re-check. Concurrency test. |
| 8 | Low | Parallel guesses could get past the 5-failure sign-in limit | Fixed: atomic reservation before the password check. Concurrency test. |
| 9 | Info | Sign-out could look successful even when the server request failed | Fixed: the user stays signed in and is warned |
| 10 | Info | Admin email already registered as a member caused a silent skip | Fixed: startup warning, never auto-promoted |

### Accepted risks

- **Sign-up reveals whether an email already has an account** (409 "already exists"). This is a usability trade-off and is rate limited. Hiding it would need email verification.
- **Rate-limit counters are in memory**, per server and reset on restart. That's fine for a single server; use Redis or the load balancer if you run several.
- **Build-time-only npm advisories** (6, low/moderate, in `vite-plugin-pwa` → `workbox-build`): they run only on the developer's machine while building, on our own config, and never ship to users. Dependabot will pick up fixes.
- **HTTPS must be provided by the hosting** (a reverse proxy or load balancer). The app sets Secure cookies and HSTS, which only fully take effect over HTTPS.
