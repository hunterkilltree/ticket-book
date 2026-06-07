# CLAUDE.md

Guidance for Claude when working in this repository. This root file is intentionally
brief. Component-specific memory lives in nested `CLAUDE.md` files that load on demand
when Claude works in that subtree:

- **`backend/CLAUDE.md`** — Spring Boot conventions, module & package layout, service internals.
- **`infrastructure/CLAUDE.md`** — local Kubernetes setup, build model, known issues & fixes.
- **`docs/implementation-log.md`** — feature implementation progress and the **next step to build**. Read this first when continuing feature work.

Read the relevant nested file before changing code in that area.

## Project overview

Concert Ticket Booking System — an event-driven microservices platform for browsing
concerts, locking seats, and purchasing tickets. `backend/` is a Gradle multi-module
build (11 modules); each module is a standalone Spring Boot app. The local runtime is a
self-contained Docker Desktop Kubernetes stack under `infrastructure/`.

## Tech stack

Java 21 · Spring Boot 4.0.6 · Spring Cloud 2025.1.x (Oakwood) · Gradle 9.4 (multi-module) ·
PostgreSQL + Flyway · Redis · Apache Kafka · Elasticsearch · WebSocket (STOMP) ·
Micrometer + Prometheus · Testcontainers · Lombok.

## Services & ports

| Service | Port | Notes |
|---|---|---|
| gateway | 8080 | routing, JWT validation (optional locally — nginx is the entry point) |
| user-service | 8081 | |
| event-service | 8082 | |
| search-service | 8083 | Elasticsearch-backed (no DB) |
| booking-service | 8084 | Redis seat locks, WebSocket, seat reaper — most complex |
| order-service | 8085 | idempotency key |
| payment-service | 8086 | idempotency key |
| ticket-service | 8087 | QR generation, email |
| notification-service | 8088 | Kafka consumer, email (no inbound REST) |
| admin-service | 8089 | |

Event flow: `seat_reserved → payment_completed → ticket_issued` (topic name constants in
`com.ticketbooking.common.messaging.Topics`).

## Commands

```bash
# Backend (run from backend/) — full set in backend/CLAUDE.md
cd backend && ./gradlew build                     # build all modules
cd backend && ./gradlew test                      # all tests
cd backend && ./gradlew :booking-service:test     # one module
cd backend && ./gradlew :booking-service:bootRun  # run one service locally

# Local stack (run from infrastructure/) — full workflow in infrastructure/CLAUDE.md
cd infrastructure && ./build-images.sh && ./deploy.sh
kubectl -n ticketing get pods -w                  # API at http://localhost/api/...
```
