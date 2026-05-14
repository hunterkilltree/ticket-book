# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Concert Ticket Booking System — an event-driven microservices platform for browsing concerts, selecting/locking seats, and purchasing tickets.

The `backend/` directory is a Gradle multi-module build. Each service is a standalone Spring Boot application. There is no frontend or infrastructure code yet.

## Backend

### Commands

```bash
# Build all modules
cd backend && ./gradlew build

# Run tests across all modules
cd backend && ./gradlew test

# Run tests for a single module
cd backend && ./gradlew :booking-service:test

# Run a single test class
cd backend && ./gradlew :user-service:test --tests "com.ticketbooking.user.UserServiceTests"

# Run a specific service locally
cd backend && ./gradlew :booking-service:bootRun

# Run with Testcontainers (integration)
cd backend && ./gradlew :booking-service:bootTestRun
```

### Tech Stack

- **Java 21** (toolchain), **Spring Boot 4.0.6**, **Gradle** (multi-module, Groovy DSL)
- **Lombok** — boilerplate reduction; wired via `annotationProcessor` and `testAnnotationProcessor`
- **PostgreSQL** + **Flyway** — each service owns its own DB; migrations in `resources/db/migration/`
- **Redis** — distributed seat locks in `booking-service` (Spring Data Redis)
- **Apache Kafka** — async event flow; producers/consumers in each service's `messaging/` package
- **Elasticsearch** — event search in `search-service` (Spring Data Elasticsearch)
- **WebSocket (STOMP)** — real-time seat map updates broadcast from `booking-service`
- **Micrometer + Brave + Prometheus** — tracing and metrics in every service
- **Testcontainers** — integration tests use real containers (PostgreSQL, Kafka, etc.)

### Module Layout

```
backend/
├── build.gradle              # root: shared config, BOM import, Lombok for all subprojects
├── settings.gradle           # declares all 11 modules
├── common-platform/          # shared library (no Spring Boot plugin)
├── gateway/                  # port 8080 — routing, JWT validation
├── user-service/             # port 8081
├── event-service/            # port 8082
├── search-service/           # port 8083 — Elasticsearch-backed
├── booking-service/          # port 8084 — Redis locks, WebSocket, seat reaper
├── order-service/            # port 8085
├── payment-service/          # port 8086
├── ticket-service/           # port 8087 — QR generation, email delivery
├── notification-service/     # port 8088 — Kafka consumer, email
└── admin-service/            # port 8089
```

### Internal Package Structure (every service)

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

### common-platform

Shared library depended on by every service (`implementation project(':common-platform')`):

| Package | Contents |
|---|---|
| `audit` | `AuditableEntity` — `@MappedSuperclass` with `createdAt` / `updatedAt` via JPA lifecycle hooks |
| `exception` | `GeneralNotFoundException`, `UnauthorizedActionException`, `InvalidStateTransitionException`, `GlobalExceptionHandler` (RFC 7807 ProblemDetail) |
| `messaging` | `EventEnvelope<T>` — Kafka message wrapper; `Topics` — all topic name constants |
| `security` | `JwtAuthFilter` (`OncePerRequestFilter` stub), `AuthenticatedUser` |

### booking-service — Key Components

This is the most complex service. Key classes:

| Class | Role |
|---|---|
| `SeatLockService` | Redis `SET NX EX` per seat — atomic acquire; returns false if another caller already holds the lock |
| `SeatReaperService` | `@Scheduled` — scans `RESERVED` seats, releases any whose Redis key has expired |
| `SeatStatusBroadcaster` | Sends STOMP messages to `/topic/events/{eventId}/seats` on every state change |
| `PaymentEventConsumer` | Kafka listener on `payment_completed` — transitions seat to `SOLD` and releases the lock |

Seat state machine: `AVAILABLE` → `RESERVED` → `SOLD` (or back to `AVAILABLE` on reap/failure)

### Kafka Event Flow

```
seat_reserved  →  payment_completed  →  ticket_issued
```

All topic names are constants in `com.ticketbooking.common.messaging.Topics`.

### Key Conventions

- **Entities**: extend `AuditableEntity`; UUID PKs via `@GeneratedValue(strategy = GenerationType.UUID)`; singular class names
- **DTOs**: Java `record` types; entities are never returned from controllers
- **Error handling**: throw `GeneralNotFoundException` / `UnauthorizedActionException` / `InvalidStateTransitionException`; `GlobalExceptionHandler` maps them to RFC 7807 `ProblemDetail`
- **Transactions**: `@Transactional` on all service write methods; read-only queries use `@Transactional(readOnly = true)`
- **Idempotency**: `order-service` and `payment-service` entities have a `idempotencyKey` unique column
- **Flyway**: every service with a DB has `resources/db/migration/V{n}__description.sql`
- **Config**: environment variables with local defaults, e.g. `${DB_URL:jdbc:postgresql://localhost:5432/userdb}`

## Infrastructure (not yet present — planned)

```bash
# Full local stack
docker compose -f infrastructure/docker/docker-compose.yml up

# Infra only (PostgreSQL, Kafka, Redis, Elasticsearch, Prometheus, Grafana, WireMock)
docker compose -f infrastructure/docker/docker-compose.infra.yml up -d

# Kubernetes (Kustomize)
kubectl apply -k infrastructure/k8s/overlays/dev

# Helm
helm upgrade --install ticket-booking infrastructure/helm/ticket-booking/ -f values.dev.yaml
```
