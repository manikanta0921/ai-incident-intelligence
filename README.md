# AI Incident Intelligence — Backend (Phase 1–4)

A production-style incident-management backend (the foundation of an AI-powered incident
intelligence platform). Companies use it to track tickets like
*"Payment deducted but order failed"*. In later phases, AI will classify incidents, suggest
severity, summarize them, find similar incidents and recommend resolutions — the foundation
for that is built here.

**Scope of this repository (Phases 1–4 only):** secure REST APIs + PostgreSQL + Spring
Security with JWT. No AI/RAG, Redis, Kafka, Docker-deployed services, microservices or
frontend yet — by design.

---

## Tech stack

| Layer      | Technology |
|------------|------------|
| Language   | Java 21 |
| Framework  | Spring Boot 3.5.x (Web, Data JPA, Security, Validation, Actuator) |
| Database   | PostgreSQL 16 + Hibernate |
| Security   | Spring Security 6, JWT (jjwt 0.12), BCrypt |
| Docs       | springdoc-openapi (Swagger UI) |
| Build      | Maven |
| Tests      | JUnit 5, Mockito, AssertJ |

---

## Getting started

### 1. Start PostgreSQL (Docker)

```bash
docker compose up -d
```

Or point the app at any existing PostgreSQL instance (see `.env.example`).

### 2. Configure environment variables

```bash
cp .env.example .env
# edit .env: DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, ...
```

`JWT_SECRET` must be at least 32 characters. Generate one:
`openssl rand -base64 64`. Never commit the real `.env`.

### 3. Run

```bash
mvn spring-boot:run
```

- API base: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

### 4. Test

```bash
mvn test
```

---

## Package structure

```
com.example.incident
├── config        # SecurityConfig, CorsConfig, OpenApiConfig, properties
├── controller    # Thin REST endpoints (no business logic)
├── dto
│   ├── auth      # RegisterRequest, LoginRequest, AuthResponse, RefreshTokenRequest
│   ├── common    # PagedResponse
│   ├── incident  # IncidentRequest/Update/StatusUpdate/Response
│   └── user      # UserResponse, RoleUpdateRequest
├── entity        # Incident, User, RefreshToken + enums (never exposed via API)
├── exception     # GlobalExceptionHandler, ErrorResponse, custom exceptions
├── job           # RefreshTokenCleanupJob (scheduled)
├── repository    # Spring Data JPA interfaces
├── security      # JwtService, JwtAuthenticationFilter, UserDetails, 401/403 handlers
├── service       # Interfaces + Specifications; business logic in service.impl
└── util          # SecurityUtils
```

Layering rule: `Controller → Service → Repository → PostgreSQL`. DTOs cross the API
boundary; JPA entities never leave the service layer.

---

## Phase 1 — Project setup (what each dependency does)

- **spring-boot-starter-web** — REST controllers, embedded Tomcat, Jackson (JSON).
- **spring-boot-starter-data-jpa** — Spring Data repositories + Hibernate ORM.
- **postgresql** — JDBC driver (runtime only).
- **spring-boot-starter-security** — filter chain, BCrypt, method security.
- **spring-boot-starter-validation** — `@NotBlank`, `@Size`, `@Email`, `@Valid`.
- **jjwt-api / jjwt-impl / jjwt-jackson** — JWT creation and verification (impl + jackson needed at runtime).
- **springdoc-openapi-starter-webmvc-ui** — Swagger UI + OpenAPI 3 docs.
- **lombok** — removes boilerplate (`@Getter`, `@Builder`); compile-time only.
- **spring-boot-starter-test / spring-security-test** — JUnit 5, Mockito, AssertJ.

Sensitive config comes from environment variables with safe local defaults:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_ACCESS_EXPIRATION`,
`JWT_REFRESH_EXPIRATION`, `CORS_ALLOWED_ORIGINS`.

---

## Phase 2 — Incident management

### Endpoints

| Method | Path | Status | Who |
|--------|------|--------|-----|
| POST   | `/api/incidents` | 201 | CUSTOMER (own), AGENT, ADMIN |
| GET    | `/api/incidents` | 200 | any authenticated (customers see only their own) |
| GET    | `/api/incidents/{id}` | 200 | owner or AGENT/ADMIN |
| PUT    | `/api/incidents/{id}` | 200 | owner (while OPEN) or AGENT/ADMIN |
| PATCH  | `/api/incidents/{id}/status` | 200 | AGENT, ADMIN |
| DELETE | `/api/incidents/{id}` | 204 | owner (while OPEN) or AGENT/ADMIN |

### Validation

- `title`: `@NotBlank`, `@Size(min=5, max=200)`
- `description`: `@NotBlank`, `@Size(min=10, max=5000)`
- `category`/`severity`/`status`: enum-typed fields — invalid values fail JSON
  deserialization → 400 with a clear message (via `HttpMessageNotReadableException`
  and `MethodArgumentTypeMismatchException` handlers).

### Global error format (no stack traces, ever)

```json
{
  "timestamp": "2026-09-06T10:30:00Z",
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Incident not found with id: 10",
  "path": "/api/incidents/10"
}
```

### Pagination & filtering — how `Pageable`/`Page` work

`GET /api/incidents?page=0&size=10&sort=createdAt,desc&status=OPEN&severity=CRITICAL&category=PAYMENT`

Spring MVC converts `page`, `size`, `sort` params into a `Pageable` object.
The repository returns a `Page<T>`: the matching rows **plus** `totalElements`,
`totalPages`, `page`, `size` — so the DB executes a `count(*)` alongside the data query.
The result is wrapped in a stable `PagedResponse<T>` envelope so the API contract does not
depend on framework classes.

Filters are optional and combinable, implemented with **JPA Specifications**
(`IncidentSpecifications.withFilters`) instead of one repository method per combination.
Guard rails: page size capped at 100, sort fields whitelisted, default sort `createdAt,desc`.

### Business rules in the service (not the controller)

- New incidents always start `OPEN` (client cannot set status/id/timestamps).
- Status transitions follow a small state machine; `CLOSED` is terminal.
  `OPEN → IN_PROGRESS | WAITING_FOR_CUSTOMER | RESOLVED | CLOSED`,
  `RESOLVED → CLOSED`, `CLOSED → (nothing)`.
- Resolution text only with `RESOLVED`/`CLOSED`.

---

## Phase 3 — API & database quality

- **Swagger** documents auth, incident and user endpoints with request/response examples
  and a bearer-token "Authorize" button.
- **Actuator**: `health` and `info` public; richer metrics ADMIN-only.
- **Database constraints**
  - `NOT NULL` on every required column; `TEXT` columns for long descriptions.
  - `UNIQUE(email)` on users — duplicate registration is impossible at the DB level too.
  - Indexes: `status`, `severity`, `category` on incidents (they back the most common
    list filters); unique index on `users.email` (login lookup); unique index on
    `refresh_tokens.token_hash` (refresh lookup) + `user_id` index (revoke-all-by-user).
    No other indexes — unused indexes slow writes for no benefit.
  - Enums stored as `STRING` (readable in SQL, safe to reorder in Java).
- **`@Transactional` explained**: wraps a method so all DB work inside happens in one
  transaction — commit on success, rollback on runtime exception. `@Transactional(readOnly = true)`
  is a hint that allows Hibernate to skip dirty checking. Note that `save()` already runs in
  a transaction; explicit `@Transactional` matters when a method touches several writes that
  must succeed or fail together (e.g. refresh-token rotation).

---

## Phase 4 — Security

### Authentication vs Authorization

- **Authentication** = *who are you?* (JWT validation, login). Handled by
  `JwtAuthenticationFilter` + `AuthenticationManager`.
- **Authorization** = *what are you allowed to do?* (roles, ownership). Handled by
  `SecurityFilterChain` rules, `@PreAuthorize`, and ownership checks in services.

### Key concepts

- **SecurityFilterChain** — the ordered servlet-filter pipeline every request passes through
  (the modern replacement for the deprecated `WebSecurityConfigurerAdapter`).
- **AuthenticationManager** — performs credential checks; wired to our
  `CustomUserDetailsService` (loads the user + BCrypt hash) and `PasswordEncoder`
  (verifies the submitted password). Used by `/api/auth/login`.
- **UserDetails / UserDetailsService** — Spring Security's "user" contract; our
  `CustomUserDetails` additionally carries the DB `id` and `role` for ownership checks.
- **JWT** — a signed (not encrypted!) token: `header.payload.signature`. Claims: `sub`
  (email), `uid`, `role`, `iat`, `exp`. No sensitive data in claims — anyone holding the
  token can decode the payload.
- **Access token** — 15 min, sent as `Authorization: Bearer <token>`.
- **Refresh token** — 7 days, opaque random UUID; only its SHA-256 hash is stored
  (DB leak ≠ usable tokens), supports revocation, and is **rotated on every use**
  (single-use → stolen-token replay fails).
- **Stateless authentication** — no server sessions; every request carries its own token.
- **Roles/authorities** — authorities stored as `ROLE_CUSTOMER`, `ROLE_AGENT`, `ROLE_ADMIN`.
  `hasRole('ADMIN')` == `hasAuthority('ROLE_ADMIN')` (hasRole adds the `ROLE_` prefix for you).

### Login flow (in words)

```
POST /api/auth/login (email, password)
  → AuthenticationManager.authenticate()
      → CustomUserDetailsService loads user by email
      → PasswordEncoder compares BCrypt hash
  → success: JwtService issues access + refresh tokens
  → client stores tokens, sends "Authorization: Bearer <accessToken>" afterwards
Every request:
  → JwtAuthenticationFilter validates the JWT and sets the SecurityContext
  → SecurityFilterChain / @PreAuthorize / service ownership checks authorize
```

### Public vs protected, 401 vs 403

Public: `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`,
Swagger, `/actuator/health|info`. Everything else needs a JWT.

- **401 Unauthorized** — no token / invalid / expired token. "I don't know who you are."
  (Returned by `RestAuthenticationEntryPoint` as JSON.)
- **403 Forbidden** — valid token, insufficient role. "I know who you are, but no."
  (Returned by `RestAccessDeniedHandler`.)

### The customer ownership rule (server-enforced)

A CUSTOMER calling `GET /api/incidents/100` for an incident reported by someone else gets
a business error (403 via handler). The check lives in `IncidentServiceImpl.enforceOwnership`
and the list endpoint force-filters by the caller's id — clients cannot override either.

### Refresh token flow

`POST /api/auth/refresh` with the refresh token → validity + revocation check against the
stored hash → old token revoked, new pair issued (rotation). Daily job purges expired rows.
`ADMIN` role changes/deletions revoke that user's refresh sessions.

### CORS

Origins come from `CORS_ALLOWED_ORIGINS` (comma-separated). There is intentionally no
"allow all origins" option in the code.

### Secrets

`JWT_SECRET`, DB credentials and CORS origins are environment variables — never hard-coded,
`.env` is gitignored, `.env.example` contains only fake values.

---

## Testing

`mvn test` — 37 unit tests, no database, no real HTTP:

- **IncidentServiceTest** — creation defaults, ownership (customer A vs B), missing incident 404,
  edit-while-OPEN rule, status state machine, resolution rules, delete permissions,
  pagination/filtering paths.
- **AuthServiceTest** — registration + BCrypt hashing, duplicate email, login success/failure,
  no user enumeration, refresh rotation, unknown/revoked/expired refresh tokens.
- **UserServiceTest** — role change revokes sessions, last-ADMIN protection, deletes.
- **JwtServiceTest** — real token signing: claims, expiry, foreign signatures, garbage input,
  refresh-token hashing.

Manual API testing: use Swagger UI (`Authorize` with the access token) or the 14-step
Postman checklist (register → login → protected calls with/without/invalid/expired token →
401/403 checks → cross-customer access → admin endpoints).

---

## What comes next (later phases)

AI classification & severity suggestion, summaries, similar-incident search (embeddings +
vector DB), resolution recommendations, Redis caching, Kafka events, microservice split,
Docker deployment, React frontend.
