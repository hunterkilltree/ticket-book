# Backend — agent instructions

Gradle multi-module build; each module is a standalone Spring Boot app. Read together
with the root `CLAUDE.md`.

## Commands

```bash
# Build all modules
./gradlew build

# Run tests across all modules
./gradlew test

# Run tests for a single module
./gradlew :booking-service:test

# Run a single test class
./gradlew :user-service:test --tests "com.ticketbooking.user.UserServiceTests"

# Run a specific service locally
./gradlew :booking-service:bootRun

# Run with Testcontainers (integration)
./gradlew :booking-service:bootTestRun
```

## Module layout

```
backend/
├── build.gradle              # root: shared config, BOM imports (Boot + Spring Cloud), Lombok for all subprojects
├── settings.gradle           # declares all 11 modules
├── common-platform/          # shared library (no Spring Boot plugin)
├── gateway/                  # port 8080 — routing, JWT validation
├── user-service/             # 8081
├── event-service/            # 8082
├── search-service/           # 8083 — Elasticsearch-backed
├── booking-service/          # 8084 — Redis locks, WebSocket, seat reaper
├── order-service/            # 8085
├── payment-service/          # 8086
├── ticket-service/           # 8087 — QR generation, email delivery
├── notification-service/     # 8088 — Kafka consumer, email
└── admin-service/            # 8089
```

## Internal package structure (every service)

```
com.ticketbooking.<service>/
├── config/       # Spring, Kafka, Redis, Security configs
├── controller/   # REST & WebSocket handlers
├── service/      # Business logic (@Transactional on all write methods)
├── repository/   # JpaRepository extensions
├── entity/       # JPA entities — UUID PKs, singular class names
├── dto/          # Request/response records (entities never exposed directly)
├── messaging/    # Kafka producers & consumers
├── exception/    # Domain-specific exceptions (extend RuntimeException)
└── mapper/       # Entity ↔ DTO converters (@Component, no MapStruct yet)
```

## common-platform

Shared library depended on by every service (`implementation project(':common-platform')`):

| Package | Contents |
|---|---|
| `audit` | `AuditableEntity` — `@MappedSuperclass` with `createdAt` / `updatedAt` via JPA lifecycle hooks |
| `exception` | `GeneralNotFoundException`, `UnauthorizedActionException`, `InvalidStateTransitionException`, `GlobalExceptionHandler` (RFC 7807 ProblemDetail) |
| `messaging` | `EventEnvelope<T>` — Kafka message wrapper; `Topics` — all topic name constants |
| `security` | `JwtAuthFilter` (`OncePerRequestFilter` stub), `AuthenticatedUser` |

## booking-service — key components

The most complex service. Key classes:

| Class | Role |
|---|---|
| `SeatLockService` | Redis `SET NX EX` per seat — atomic acquire; returns false if another caller already holds the lock |
| `SeatReaperService` | `@Scheduled` — scans `RESERVED` seats, releases any whose Redis key has expired |
| `SeatStatusBroadcaster` | Sends STOMP messages to `/topic/events/{eventId}/seats` on every state change |
| `PaymentEventConsumer` | Kafka listener on `payment_completed` — transitions seat to `SOLD` and releases the lock |

Seat state machine: `AVAILABLE` → `RESERVED` → `SOLD` (or back to `AVAILABLE` on reap/failure).

## Kafka event flow

```
seat_reserved  →  payment_completed  →  ticket_issued
```

All topic names are constants in `com.ticketbooking.common.messaging.Topics`.

## Key conventions

- **Entities**: extend `AuditableEntity`; UUID PKs via `@GeneratedValue(strategy = GenerationType.UUID)`; singular class names.
- **DTOs**: Java `record` types; entities are never returned from controllers.
- **Error handling**: throw `GeneralNotFoundException` / `UnauthorizedActionException` / `InvalidStateTransitionException`; `GlobalExceptionHandler` maps them to RFC 7807 `ProblemDetail`.
- **Transactions**: `@Transactional` on all service write methods; read-only queries use `@Transactional(readOnly = true)`.
- **Idempotency**: `order-service` and `payment-service` entities have an `idempotencyKey` unique column.
- **Flyway**: every service with a DB has `resources/db/migration/V{n}__description.sql`. (admin-service currently lacks one — see infrastructure/CLAUDE.md.)
- **Config**: environment variables with local defaults, e.g. `${DB_URL:jdbc:postgresql://localhost:5432/userdb}`.
- **Build config**: the root `build.gradle` imports both the Spring Boot BOM and the
  Spring Cloud BOM (`2025.1.1`). Spring Cloud starters (e.g. the gateway) rely on it for versions.
