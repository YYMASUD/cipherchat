# CipherChat

A direct-message application with client-side end-to-end encryption, a Java 21 /
Spring Boot 3.4 backend, PostgreSQL persistence, and a React 18 / Vite frontend.
The backend accepts and persists encrypted message envelopes, never message text.

## Implemented features

- Registration, login, BCrypt hashing, JWT access tokens, rotating opaque refresh
  tokens stored as SHA-256 hashes, and refresh-token logout/revocation.
- Case-insensitive usernames, bounded user search, direct conversations, participant
  authorization, paginated history, and idempotent message submission.
- Authenticated native WebSocket/STOMP private events with restricted subscriptions
  and destinations. Connections close when their access token expires.
- AES-256-GCM per-message encryption; RSA-OAEP/SHA-256 wraps each AES key separately
  for sender and recipient. Client message IDs are authenticated as associated data.
- Password-encrypted private keys in IndexedDB using PBKDF2/SHA-256 (600,000
  iterations), AES-GCM, and random salts/IVs. Decrypted session keys and tokens stay
  in memory. Reloading requires signing in and unlocking again.
- Encrypted key backup download/import, key-pair verification on restore, pinned
  contact keys (TOFU), and SHA-256 public-key fingerprints for manual verification.
- Typing indicators, online presence, delivery/read receipts, reconnect refresh,
  periodic database reconciliation, and a persistent ciphertext-only outbox.
- Optional Redis Pub/Sub for cross-instance events and expiring presence leases.
  Local mode needs no Redis. PostgreSQL history is authoritative when events are
  missed. The UI polls every 15 seconds as a fallback.
- Responsive UI, validation, per-instance authentication/message rate limits,
  Flyway migrations, API integration tests, socket tests, and cryptographic tests.

## Requirements and startup

- JDK 21 (Java 8 on PATH will not work).
- PostgreSQL 16; Docker Compose can start PostgreSQL and Redis.
- Node.js 20.19+ or a supported newer LTS for the frontend toolchain.

From `D:/Code/Webchat/chat-app`:

```powershell
docker compose up -d
$env:JAVA_HOME = 'C:/Users/CloudZone/.jdks/ms-21.0.12.1'
.\mvnw.cmd spring-boot:run
```

Use your own JDK path if different. In a second terminal:

```powershell
Set-Location D:/Code/Webchat/chat-app/frontend
npm ci
npm run dev
```

Open **http://localhost:5173**. Vite proxies `/api` and `/ws` to port 8080.
Health is at **http://localhost:8080/actuator/health**. Create two accounts in
separate browser profiles, download both encrypted key backups, search for the
other account, and start a conversation.

If Docker is unavailable, provide an existing PostgreSQL instance through the
environment variables below. Redis is optional in development.

## Key recovery: read before using

Download the **encrypted key backup** after registration and keep it safe. On
another browser, choose that file on the login screen and enter the same account
password. The backup is checked against the account's public key and the
private/public pair is verified before use.

The private key is not uploaded. There is no server-side password reset or key
recovery. Losing both IndexedDB data and the backup permanently loses access to
encrypted history. Never silently regenerate keys for an existing account.
The outbox contains ciphertext only and resumes after signing into the same account.
Signing out retains encrypted identity and pending-message data locally.

## Configuration

Profiles: `dev` (default), `prod`, and `test`. See `.env.example`; Spring does
**not** automatically load that file. Export variables or set IDE run configuration
values. Do not commit real credentials.

| Variable | Default / purpose |
|---|---|
| `SERVER_PORT` | `8080` |
| `SPRING_PROFILES_ACTIVE` | `dev` |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Dev: `localhost`, `5432`, `chatapp` |
| `DB_USER`, `DB_PASSWORD` | Dev: `chatapp` / `chatapp`; required in prod |
| `DB_SSLMODE` | Dev: `prefer`; configure trusted TLS for remote databases |
| `DB_URL` | Required production JDBC PostgreSQL URL |
| `JWT_SECRET` | Base64 HS256 secret, at least 32 random bytes; required in prod |
| `JWT_ISSUER` | `chat-app` |
| `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL` | `900000`, `604800000` milliseconds |
| `CORS_ALLOWED_ORIGINS` | Local Vite origins and localhost:3000; override in prod |
| `MSG_MAX_PAYLOAD` | `65536` ciphertext characters; DTO hard cap is also 65536 |
| `REALTIME_REDIS_ENABLED` | `false` in dev, `true` in prod |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Host required in prod; port `6379`, optional password |
| `DB_POOL_MAX`, `DB_POOL_MIN` | Production: `20`, `5` |

Generate a secret in PowerShell:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Flyway owns the schema (`V1__init_schema.sql`); Hibernate validates it. Tests run
the same migration on H2 in PostgreSQL compatibility mode. No destructive schema
updates or automatic baselining are enabled.

## REST API

All endpoints except auth and health require `Authorization: Bearer <accessToken>`.
Requests are JSON. Null response fields are omitted. Errors use `{ "message": "..." }`.

| Method / path | Request or behavior |
|---|---|
| `POST /api/auth/register` | `{ username, password, publicKey }`; tokens + user, status 201 |
| `POST /api/auth/login` | `{ username, password }`; `{ accessToken, refreshToken, user }` |
| `POST /api/auth/refresh` | `{ refreshToken }`; consumes old token and returns new pair |
| `POST /api/auth/logout` | `{ refreshToken }`; revokes it, status 204 |
| `GET /api/users/me` | Current public profile |
| `GET /api/users?q=prefix` | Up to 20 users, excluding self |
| `GET /api/users/{id}/public-key` | `{ id, username, publicKey }` (Base64 SPKI) |
| `GET /api/conversations?page=0` | Up to 50 `{ id, peer, createdAt, online }` |
| `POST /api/conversations` | `{ userId }`; existing or new direct conversation |
| `GET /api/conversations/{id}/messages?page=0` | `{ messages, hasMore }`, 50 newest first |
| `POST /api/conversations/{id}/messages` | `{ clientId, ciphertext, iv, recipientKey, senderKey }` |
| `POST /api/conversations/receipts` | `{ messageId, read }`; recipient only |

Messages include `id`, `conversationId`, `senderId`, `clientId`, encrypted envelope
fields, `createdAt`, and optional `deliveredAt` / `readAt`. Binary fields use standard
Base64; IVs are 12 bytes. Reusing a sender's client ID returns the original message.
A concurrent creation may return 409: retry the same request after the winner commits.
History uses offset pagination; concurrent inserts can shift page boundaries, so
clients deduplicate by message ID. Older history is loaded manually in the UI.

## WebSocket contract

Use native WebSocket `/ws` (no SockJS dependency). STOMP `CONNECT` must include
`Authorization: Bearer ...`. Subscribe **only** to `/user/queue/events`.
Events are `{ type, data }`: `message`, `receipt`, `conversation`, `typing`, or `error`.
Messages/receipts contain message responses. Typing contains `conversationId`,
`userId`, and `typing`. Send typing to `/app/typing` with `{ conversationId, typing }`.
Messages and receipts use REST for acknowledgements and safe retries.
Clients cannot publish to broker queues or subscribe to another user's queue.

Presence is best effort: Redis mode renews a 25-second lease every 10 seconds.
Disconnects may take up to 25 seconds to appear offline. Delivery means a recipient
client acknowledged an envelope; read means its conversation was open in a visible
document, not proof that a human actually read it.

## Testing and build

```powershell
$env:JAVA_HOME = 'C:/Users/CloudZone/.jdks/ms-21.0.12.1'
.\mvnw.cmd clean verify
Set-Location D:/Code/Webchat/chat-app/frontend
npm ci
npm test
npm run build
npm audit
```

Backend tests cover migrations, authentication, token rotation/revocation,
expiry/signatures/issuer, search, membership isolation, idempotency, receipts,
payload validation, live socket delivery, and forbidden subscriptions. Frontend
Node tests execute real Web Crypto encryption, backup restoration, wrong-password
rejection, sender/recipient decryption, and tamper rejection. Browser interaction
and multi-node Redis/PostgreSQL behavior still require deployment-level testing.

## Production deployment and security boundaries

1. Set `prod` and required database/JWT/Redis variables. Use a random JWT secret
   and explicitly set CORS to your HTTPS frontend origin.
2. Build with `mvnw.cmd package`; run `java -jar target/chat-app-0.0.1-SNAPSHOT.jar`.
3. Build the frontend and serve `frontend/dist` from a reverse proxy. Proxy `/api/`
   and `/ws` to the backend, preserving WebSocket Upgrade/Connection headers.
4. Terminate TLS, redirect HTTP to HTTPS, and use WSS. Trust forwarded headers only
   from that proxy; keep the backend, Redis, and PostgreSQL off the public network.
5. Add a restrictive frontend CSP, body limits, connection quotas, and shared edge
   rate limiting. Back up PostgreSQL and secure Redis access.

Important limitations:

- No forward secrecy, Double Ratchet, or cryptographic sender signatures. The
  server is assumed honest-but-curious; first-use keys need independent fingerprint
  verification to resist a malicious server. E2EE does not hide metadata.
- Compromised browsers, XSS, or malicious frontend deployments can read unlocked
  data. Passwords reach the authentication server over TLS; this does not protect
  against a malicious authentication server.
- Logout revokes refresh tokens; issued access tokens/sockets remain valid until
  short expiry. Tokens are not persisted across reloads.
- Rate limits are per instance. Redis Pub/Sub is transient; history polling and
  outbox retries supply eventual recovery, not a durable event queue. Redis must
  be reachable at startup when shared mode is enabled.
- Password reset/change, attachments, groups, calls, and key rotation are outside
  the documented direct-chat scope and are not implemented.

This covers the original phases 2–6, not an independently audited production
cryptosystem.