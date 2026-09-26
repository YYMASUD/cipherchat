# chat-app — Real-Time End-to-End Encrypted Chat

A production-oriented, multi-user real-time chat application where message
plaintext **never** reaches the server. Messages are encrypted on the client
(Web Crypto API) and the backend only ever stores/forwards ciphertext.

> **Project status: Phase 1 (Foundation) complete.**
> See [Build phases](#build-phases) — the app is built incrementally.

---

## Tech stack

| Layer      | Technology |
|------------|------------|
| Backend    | Java 21 · Spring Boot **3.4.x** · Spring Web · Spring Security · Spring Data JPA · WebSocket/STOMP |
| Database   | PostgreSQL 16 (Flyway migrations) |
| Cache/PubSub | Redis 7 *(used from Phase 6)* |
| Auth       | JWT (access + refresh) via `io.jsonwebtoken`, BCrypt password hashing |
| Mapping    | MapStruct 1.6.3 · Lombok |
| Frontend   | React 18 + Vite · Web Crypto API · STOMP.js/SockJS · Axios · TailwindCSS *(Phase 4+)* |
| Infra      | Docker Compose (PostgreSQL + Redis) |

---

## Requirements

- **JDK 21** (the build targets Java 21)
- **Docker Desktop** (for PostgreSQL + Redis) — or an existing PostgreSQL 16 instance
- **Node.js 18+** *(only needed from Phase 4 for the frontend)*

> ⚠️ **JDK note:** this machine's `java` on PATH is Java 8. Always build with the
> project's JDK 21 (IntelliJ uses `ms-21`). For CLI builds set `JAVA_HOME` first:
> ```powershell
> $env:JAVA_HOME = "C:\Users\CloudZone\.jdks\ms-21.0.12.1"
> ```

---

## Quick start

### 1. Start the database

```bash
docker compose up -d
```

This starts:
- **PostgreSQL** on `localhost:5432` — db `chatapp`, user `chatapp`, password `chatapp`
- **Redis** on `localhost:6379`

Check health: `docker compose ps`

### 2. Run the backend

**From IntelliJ IDEA:** open the project (it already points at the `ms-21` SDK) and
run `ChatAppApplication`. The default profile is `dev`.

**From the command line (Windows):**
```powershell
$env:JAVA_HOME = "C:\Users\CloudZone\.jdks\ms-21.0.12.1"
cd D:/Code/Webchat/chat-app
.\mvnw.cmd spring-boot:run
```

The app starts on **http://localhost:8080**.

### 3. Verify it is up

```powershell
# App health (expect {"status":"UP", ...})
Invoke-RestMethod http://localhost:8080/actuator/health
```

---

## Configuration

Configuration is YAML-based with one file per profile:

| File | Purpose |
|------|---------|
| `src/main/resources/application.yml` | Base settings shared by all profiles |
| `application-dev.yml` | Local dev → docker-compose PostgreSQL + Redis, debug logging |
| `application-prod.yml` | Production → all secrets from environment variables, no defaults |
| `src/test/resources/application-test.yml` | Tests → in-memory H2 (PostgreSQL mode), no Docker needed |

Select a profile with `SPRING_PROFILES_ACTIVE` (defaults to `dev`).

### Environment variables

| Variable | Default (dev) | Description |
|----------|---------------|-------------|
| `SPRING_PROFILES_ACTIVE` | `dev` | Active profile |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `chatapp` | PostgreSQL (dev) |
| `DB_USER` / `DB_PASSWORD` | `chatapp` / `chatapp` | PostgreSQL credentials (dev) |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | *(none)* | Required in `prod` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis connection |
| `JWT_SECRET` | dev placeholder | **Base64** HS256 secret (≥256 bits). Override in prod! |
| `JWT_ACCESS_TTL` | `900000` (15 min) | Access token lifetime (ms) |
| `JWT_REFRESH_TTL` | `604800000` (7 days) | Refresh token lifetime (ms) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Comma-separated allowed origins |

> The JWT settings are read from Phase 2 onward; they are present now so the
> configuration surface is complete and testable.

---

## Project layout

```
chat-app/
├── docker-compose.yml                 # PostgreSQL + Redis for local dev
├── pom.xml                            # Maven build (Java 21, Spring Boot 3.4.x)
├── src/
│   ├── main/
│   │   ├── java/com/cloudzone/chatapp/
│   │   │   ├── ChatAppApplication.java
│   │   │   ├── config/                # Security, CORS, WebSocket, Redis (later phases)
│   │   │   ├── controller/            # REST + WebSocket controllers
│   │   │   ├── service/               # Business logic
│   │   │   ├── repository/            # Spring Data JPA repositories
│   │   │   ├── entity/                # JPA entities
│   │   │   ├── dto/                   # Request/response DTOs
│   │   │   ├── mapper/                # MapStruct mappers
│   │   │   ├── security/              # JWT filter, token provider, user details
│   │   │   └── exception/             # Global exception handler + custom exceptions
│   │   └── resources/
│   │       ├── application*.yml        # Profile configuration
│   │       └── db/migration/           # Flyway SQL migrations
│   └── test/
│       ├── java/com/cloudzone/chatapp/ # Unit + integration tests
│       └── resources/application-test.yml
└── frontend/                          # React + Vite app (Phase 4+)
```

---

## E2EE design (how it will work)

> Implemented in **Phase 5**; documented here so the architecture is clear up front.

**Key model — hybrid encryption:**

1. **At registration (client side):** the browser generates an **RSA-OAEP** key pair.
   Only the **public key** is uploaded to the server. The **private key** is stored
   in the browser's **IndexedDB**, encrypted with an **AES-GCM** key derived from
   the user's password via **PBKDF2**. The plaintext private key never leaves the device.
2. **To send a message:**
   - fetch the recipient's public key from `GET /api/users/{id}/public-key`;
   - generate a random **AES-GCM** key (per message) and a unique **IV**;
   - encrypt the message body with AES-GCM;
   - encrypt that AES key with the recipient's **RSA-OAEP** public key;
   - send only `{ ciphertext, encryptedSymmetricKey, iv }` to the server.
3. **The server** persists and forwards ciphertext only — it holds no private key
   and therefore cannot decrypt anything.
4. **The recipient** decrypts the AES key with their RSA private key, then decrypts
   the message. The AES key is also wrapped with the *sender's* public key so the
   sender can read their own sent history.

**Security trade-offs made for simplicity (documented honestly):**
- The server enforces auth and rate limits but is assumed **honest-but-curious**;
  it is explicitly *not* trusted with plaintext, but it can still observe metadata
  (who talks to whom, when, message sizes).
- There is **no forward secrecy** (a compromised long-lived private key exposes past
  messages). A future enhancement is the Double Ratchet / X3DH (as in Signal).
- Key verification is **TOFU** (trust-on-first-use); no out-of-band fingerprint
  verification is enforced in the base build.

---

## Frontend (Phase 4+)

A React 18 + Vite app will live in `/frontend` with registration, login, chat UI,
and the client-side E2EE implementation. Run it with `npm install && npm run dev`
(default Vite port **5173**, already allowed in CORS).

---

## Testing

```bash
# All tests (no Docker required — uses in-memory H2)
.\mvnw.cmd test
```

- `ChatAppApplicationTests` — smoke test that the Spring context boots.
- From Phase 2: auth tests (register/login/refresh). From Phase 3: messaging tests.

---

## Build phases

| Phase | Scope | Status |
|-------|-------|--------|
| **1** | Foundation: pom, `application.yml`, Docker Compose, app boots | ✅ **Done** |
| 2 | User auth: entity, Flyway, register/login, Spring Security + JWT | ⏳ |
| 3 | Real-time chat: WebSocket/STOMP, conversations & messages (plaintext first) | ⏳ |
| 4 | Frontend: React + Vite, register/login, chat UI | ⏳ |
| 5 | E2EE: client key generation, AES-GCM + RSA-OAEP, IndexedDB key storage | ⏳ |
| 6 | Enhancements: presence, typing, delivery/read, Redis, offline, tests, docs | ⏳ |

---

## Enabling HTTPS/TLS (production)

The app itself speaks plain HTTP and is meant to run **behind a TLS-terminating
reverse proxy** (Nginx, Caddy, Traefik, or a cloud load balancer):

1. Obtain a certificate (e.g. Let's Encrypt / `certbot`).
2. Configure the proxy to terminate TLS on `443` and forward to the app on `8080`,
   setting `X-Forwarded-Proto`/`X-Forwarded-For` headers.
3. The app already sets `server.forward-headers-strategy: framework` in the `prod`
   profile, so it respects those headers (correct secure-cookie / redirect decisions).
4. Redirect all `80` → `443`. Use `wss://` for the WebSocket endpoint (`/ws`).

> Never run this app over plain HTTP in production: with E2EE the *payload* is safe,
> but JWTs and refresh tokens still travel in headers/cookies and must be protected
> in transit.