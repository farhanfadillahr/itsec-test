# Project structure

This project uses a modular monolith structure. Code is grouped by feature instead of by layer like in MVC, so each feature has its own module with its controller, service, domain and persistence code.

## Modules

```
com.itsectest
├── shared/          code used by all modules
│   ├── config/      Spring configuration (security, JWT, OpenAPI, async)
│   ├── security/    Role, AuthPrincipal, token denylist, 401 and 403 handlers
│   ├── ratelimit/   rate limit filter and Redis rate limiter
│   ├── error/       exceptions and the global exception handler
│   ├── web/         response wrappers, pagination, validators
│   └── audit/api/   AuditEvent, AuditPublisher, @Auditable
├── user/            accounts and roles
├── auth/            login, MFA, tokens, lockout
├── article/         article CRUD
└── audit/           audit log storage and API
```

Every feature module has the same layout:

```
<module>/
├── api/        what other modules are allowed to use
├── domain/     entities, repository interfaces and business rules
├── internal/   implementation, only used inside the module
└── web/        REST controller and DTOs
```

## Module rules

- A module can use the `api` and `domain` packages of another module, but not its `internal` package.
- `shared` does not depend on any feature module.
- Domain code does not depend on the web layer.

These rules are checked with ArchUnit in `ModuleBoundaryTest`, so the build fails when a class breaks them.

The only dependency between feature modules is from `auth` to `user`, through `UserFacade`. The auth module never sees the `User` entity or the password hash. It calls `matchesPassword(userId, password)` instead.

## Design patterns

| Pattern | Where | Purpose |
| --- | --- | --- |
| Facade | `UserFacade` | Single entry point for other modules to access user data |
| Strategy | `IdentifierStrategy` (username, email) | Log in with a username or an email without if/else branches |
| Strategy | `OtpChannel` | The OTP delivery method can be replaced, for example SMS instead of email |
| Observer | `AuditPublisher`, `AuditEventListener` | Services publish audit events and the audit module saves them asynchronously after the transaction commits |
| Decorator | `CachedArticleRepository` | Adds Redis caching on top of the JPA repository without changing `ArticleService` |
| Chain of Responsibility | Spring Security filter chain | Rate limiting, JWT authentication and authorization run as separate filters |
| Repository | `*Repository` in `domain`, `*JpaAdapter` in `internal` | Services depend on interfaces, which makes them easy to unit test |
| Factory | `TokenFactory` | Access and refresh tokens are created in one place |
| Specification | `*Specifications` | Builds dynamic filters for the list endpoints |
| Template Method | `RateLimitFilter` extends `OncePerRequestFilter` | Spring defines the request flow and the filter implements one step of it |
| Builder | Lombok `@Builder` on entities and `AuditEvent` | Readable object creation when there are many optional fields |

## Authorization

Role checks are done in the controllers with `@PreAuthorize`. Ownership checks, for example an editor updating only their own articles, are done in `ArticleAccessPolicy`, which the service calls after loading the article. `ArticleAccessPolicy` has no dependencies, so every rule in the role table is covered by simple unit tests.

## Notes

- Entities use JPA annotations directly in the `domain` package. Keeping separate domain models and JPA entities would add a lot of mapping code for only three entities.
- `Article.authorId` is a UUID instead of a `@ManyToOne` relation to `User`, so the article module does not depend on the user entity. Author names are loaded with one query per page through `UserFacade`.
