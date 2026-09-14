# ITSEC Test

REST API for managing articles with JWT authentication, email OTP, role based access control, account lockout and audit logging.

Built with Java 21, Spring Boot 4.1, Spring Security 7, PostgreSQL 16 and Redis 7.

## Getting started

```bash
cp .env.example .env
docker compose up -d
```

This starts the API on port 8080, PostgreSQL on 5432 and Redis on 6379. Flyway creates the tables and a default super admin on the first start.

| | |
| --- | --- |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI spec | http://localhost:8080/v3/api-docs |
| Health check | http://localhost:8080/actuator/health |
| Postman collection | [docs/postman_collection.json](docs/postman_collection.json) |

Default super admin: `superadmin` / `SuperAdmin#2026`. MFA is disabled for this account so the API can be tried without setting up email.

If the browser shows `HTTP Status 400 Bad Request`, open http://127.0.0.1:8080 instead. This happens when the browser sends too many cookies for localhost.

### Email

OTP codes are sent over SMTP, so fill in the `SMTP_*` and `MAIL_FROM` variables in `.env`. Any SMTP provider works, for example Gmail with an app password or Resend.

To test without email, set `MAIL_DELIVERY_ENABLED=false` and read the code from the logs:

```bash
docker compose logs app | grep "OTP for"
```

## Features

### Articles

CRUD at `/api/v1/articles` with the fields id, title, content, author_id, created_at and updated_at. The list endpoint supports paging and filtering by keyword, status and author.

I added a `status` field (`DRAFT`, `PUBLISHED`, `ARCHIVED`) because viewers can only see public articles, so there has to be a way to mark an article as public. Deleting an article is a soft delete.

### Authentication

Endpoints under `/api/v1/auth`: register, login, verify OTP, resend OTP, refresh, logout and me. Login accepts a username or an email.

- The access token is a JWT (HS256) that expires after 15 minutes.
- The refresh token is a random string stored in Redis for 7 days. Each one can only be used once.
- Logout puts the access token on a denylist in Redis, so it stops working right away.

### Security

**MFA.** After the password is checked, a 6 digit code is sent to the user's email. Tokens are only returned after the code is verified. The code is stored as a hash, expires after 5 minutes and allows 3 wrong attempts.

**Account lockout.** 5 failed logins within 10 minutes locks the account for 30 minutes. The lock is stored in Redis and also saved to `users.locked_until`.

**Audit log.** `GET /api/v1/audit-logs` (super admin only) lists events for article and user CRUD, registration, login attempts, OTP, lockout, token refresh, logout, rate limiting and denied access. Each entry has the user, action, endpoint, IP address, browser, OS, device type and timestamp, and can be filtered by user, action, status, resource and date range.

**RBAC.**

| Role | Create | Read | Update | Delete | Manage users | Audit logs |
| --- | --- | --- | --- | --- | --- | --- |
| SUPER_ADMIN | yes | all | all | all | yes | yes |
| EDITOR | yes | all | own | own | no | no |
| CONTRIBUTOR | yes | published and own | own | no | no | no |
| VIEWER (default) | no | published | no | no | no | no |

A viewer who requests a draft gets 404 instead of 403, so they cannot tell whether the draft exists.

### Other

- Request validation with Bean Validation, including a password strength check
- Swagger UI with springdoc
- Unit and integration tests, with a JaCoCo check that fails the build below 80% line coverage
- Multi-stage Dockerfile and Docker Compose
- Rate limiting in Redis. Login is limited to 5 requests per minute per IP, and responses include `X-RateLimit-*` headers
- Article details are cached in Redis

## Tests

```bash
./mvnw verify
./mvnw verify -Punit-only
```

Integration tests use Testcontainers, so Docker has to be running. `-Punit-only` skips them, which is also what the Docker image build uses.

If JDK 21 is not installed, the build can run inside a container:

```bash
docker run --rm -v "$PWD":/app -w /app \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --add-host host.docker.internal:host-gateway \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  maven:3.9-eclipse-temurin-21 mvn verify
```

## Configuration

All settings come from environment variables. See [.env.example](.env.example).

| Variable | Default | Description |
| --- | --- | --- |
| `JWT_SECRET` | development value | At least 32 characters. Change it outside local development. |
| `SMTP_HOST`, `SMTP_PORT` | `smtp.gmail.com`, `587` | SMTP server, using STARTTLS |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | empty | SMTP credentials |
| `MAIL_FROM` | `no-reply@itsec-test.local` | Sender address |
| `MAIL_DELIVERY_ENABLED` | `true` | `false` writes the OTP to the log instead of sending it |
| `MFA_ENABLED` | `true` | Turns MFA on or off for all users |
| `RATE_LIMIT_ENABLED` | `true` | Turns rate limiting on or off |

## Known limitations

- A locked account gets a different message than a wrong password, so it is possible to find out that the account exists.
- Logging in with a username that does not exist responds faster than a wrong password, because no password hash is checked.
- The rate limiter trusts `X-Forwarded-For`. That is fine behind a reverse proxy, but set `server.forward-headers-strategy: none` if the app is exposed directly.
- Only single articles are cached, not article lists.
- CORS allows all origins.

## Project structure

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
