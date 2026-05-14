# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Concert Ticket Booking System — an event-driven microservices platform for browsing concerts, selecting/locking seats, and purchasing tickets. See `README.md` for full architecture diagrams and business rules.

**Current state:** The repo is in early scaffolding. Only `backend/` exists. The multi-module Gradle layout, frontend, infrastructure, and scripts described in the README are not yet implemented.

## Backend

### Commands

```bash
# Build
cd backend && ./gradlew build

# Run tests
cd backend && ./gradlew test

# Run a single test class
cd backend && ./gradlew test --tests "org.example.backend.BackendApplicationTests"

# Run with Testcontainers (integration tests via TestBackendApplication)
cd backend && ./gradlew bootTestRun

# Run the application
cd backend && ./gradlew bootRun
```

### Tech Stack (current)

- **Java 21** (toolchain), **Spring Boot 4.0.6**, **Gradle** (single module, Groovy DSL)
- **Lombok** — used for boilerplate reduction; annotate test classes with `@Testcontainers`-style annotations via `testAnnotationProcessor`
- **Micrometer + Brave** — distributed tracing wired via `spring-boot-micrometer-tracing-brave`
- **Prometheus** — metrics endpoint via `micrometer-registry-prometheus`
- **Testcontainers** — `TestcontainersConfiguration` (currently a stub) is imported by `BackendApplicationTests` to bootstrap the Spring context for integration tests; `TestBackendApplication` provides a local runnable entry point that also loads that config

### Package Structure (current)

```
org.example.backend/
└── BackendApplication.java   # @SpringBootApplication entry point
```

### Planned Multi-Module Layout (from README — not yet created)

When building out services, each module goes under `backend/` and follows this internal layout:

```
<service>/
└── src/main/java/com/ticketbooking/<service>/
    ├── config/       # Spring, Kafka, Redis, Security configs
    ├── controller/   # REST & WebSocket handlers
    ├── service/      # Business logic (@Transactional on all write methods)
    ├── repository/   # JpaRepository extensions
    ├── entity/       # JPA entities — UUID PKs, singular class names
    ├── dto/          # Request/response DTOs (entities are never exposed directly)
    ├── messaging/    # Kafka producers & consumers
    ├── exception/    # Domain-specific exceptions
    └── mapper/       # Entity ↔ DTO mappers
```

A `common-platform` shared library will provide: `GlobalExceptionHandler`, `EventEnvelope`, `KafkaSerdeConfig`, `JwtAuthFilter`, `AuditableEntity`, and RFC 7807 `ProblemDetail` DTOs.

## Architecture Decisions

### Seat Booking Flow

```
User selects seats
  → booking-service acquires Redis distributed lock per seat (5–10 min TTL)
    → User pays
      ├── payment_completed event → seats transition to `sold`
      └── timeout / payment_failed → SeatReaperService releases the lock
```

Kafka topics in order: `seat_reserved` → `payment_completed` → `ticket_issued`

Seat states: `available` → `reserved` → `sold`

### Key Conventions (for when those modules are built)

- **Error handling**: throw `GeneralNotFoundException` / `UnauthorizedActionException` / `InvalidStateTransitionException`; `GlobalExceptionHandler` maps them to RFC 7807 Problem Detail responses
- **Database migrations**: Flyway, versioned `V{n}__description.sql` under `resources/db/migration/`
- **Idempotency**: order and payment endpoints accept idempotency keys
- **Flash sales**: requests queue via Kafka; rate limiting per user/IP

## Infrastructure (planned — not yet present)

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
