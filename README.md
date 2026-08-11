# 🎫 Concert Ticket Booking System

A web-based platform that allows users to browse concerts, select seats, purchase tickets, and receive confirmations — built to handle high availability and strong consistency under peak demand.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Repository Structure](#repository-structure)
- [Backend Services](#backend-services)
- [Frontend](#frontend)
- [Infrastructure](#infrastructure)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Key Business Rules](#key-business-rules)
- [Non-Functional Requirements](#non-functional-requirements)
- [Observability](#observability)

---

## Overview

The Concert Ticket Booking System enables:

- **Customers** to browse upcoming concerts, select seats, and purchase tickets
- **Event organizers** to create and manage events, venues, and pricing tiers
- **Administrators** to manage users, view sales reports, and monitor system health

### Core Features

- User registration, login, and booking history
- Event catalog with filters (location, date, artist)
- Real-time seat map with live availability via WebSocket
- Temporary seat reservation (5–10 minute lock) with automatic expiry
- Idempotent checkout with payment gateway integration
- Digital ticket issuance (QR/barcode), downloadable and email-deliverable
- Flash sale handling via Kafka queuing and rate limiting
- Admin dashboard with event management and system health monitoring

### Ticket Purchase Flow

```
User selects concert
  → Selects seats
    → System locks seats (Redis, 5–10 min TTL)
      → User completes payment
        ├── Success → booking confirmed → ticket issued
        └── Failure / Timeout → seats released
```

---

## Architecture

The system is built as an **event-driven microservices architecture** deployed on Kubernetes.

```
                        ┌────────────┐
                        │  Frontend  │  Vite + React + TypeScript
                        └─────┬──────┘
                              │ REST / WebSocket
                        ┌─────▼──────┐
                        │  API       │
                        │  Gateway   │  JWT auth, rate limiting, routing
                        └─────┬──────┘
           ┌──────────────────┼──────────────────┐
           │                  │                  │
    ┌──────▼─────┐   ┌────────▼──────┐   ┌───────▼──────┐
    │   User     │   │    Event      │   │   Search     │
    │  Service   │   │   Service     │   │   Service    │
    └────────────┘   └───────────────┘   └──────────────┘
                                │ Kafka events
           ┌──────────────────┬─┴────────────────┐
           │                  │                  │
    ┌──────▼─────┐   ┌────────▼──────┐   ┌───────▼──────┐
    │  Booking   │   │    Order      │   │   Payment    │
    │  Service   │   │   Service     │   │   Service    │
    │ (Redis     │   └───────────────┘   └──────────────┘
    │  locks)    │                  │
    └────────────┘          ┌───────▼──────┐   ┌──────────────┐
                            │   Ticket     │   │Notification  │
                            │   Service   │   │  Service     │
                            └─────────────┘   └──────────────┘
```

**Event flow (Kafka topics):** `seat_reserved` → `payment_completed` → `ticket_issued`

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Java 25, Spring Boot 4.1.0, Gradle (multi-module) |
| **Frontend** | Vite, React, TypeScript (strict), Tailwind CSS, Zustand |
| **Database** | PostgreSQL 16 with TLS |
| **Search** | Elasticsearch |
| **Messaging** | Apache Kafka |
| **Cache / Locking** | Redis |
| **API Gateway** | Spring Cloud Gateway |
| **Schema Migrations** | Flyway |
| **Containerization** | Docker, Docker Compose |
| **Orchestration** | Kubernetes + Helm (Kustomize overlays) |
| **Observability** | Prometheus, Grafana, OpenTelemetry (distributed tracing) |
| **Load Testing** | k6 |

---

## Repository Structure

```
ticket-booking/
├── backend/                    # Gradle multi-module root
│   ├── settings.gradle.kts
│   ├── build.gradle.kts        # version catalog + common config
│   ├── gradle/
│   │   └── libs.versions.toml  # centralized dependency versions
│   ├── common-platform/        # shared library
│   ├── gateway/
│   ├── user-service/
│   ├── event-service/
│   ├── search-service/
│   ├── booking-service/
│   ├── order-service/
│   ├── payment-service/
│   ├── ticket-service/
│   ├── notification-service/
│   └── admin-service/
│
├── frontend/                   # Vite + React + TypeScript
│
├── infrastructure/
│   ├── docker/
│   │   ├── docker-compose.yml          # full local stack
│   │   ├── docker-compose.infra.yml    # infra only (run services from IDE)
│   │   ├── prometheus/
│   │   ├── grafana/
│   │   └── wiremock/payment-stubs/
│   ├── k8s/
│   │   ├── base/                       # Kustomize base
│   │   └── overlays/{dev,staging,prod}/
│   └── helm/
│       └── ticket-booking/             # umbrella chart with per-service subcharts
│
├── docs/
│   ├── architecture/
│   │   ├── overview.md
│   │   ├── seat-booking-flow.md
│   │   └── adr/                        # architecture decision records
│   ├── api/                            # OpenAPI specs per service
│   └── runbooks/
│
├── scripts/
│   ├── bootstrap.sh                    # first-time dev setup
│   ├── load-test/                      # k6 scenarios
│   └── kafka/create-topics.sh
│
├── .github/workflows/                  # CI: build, test, scan, push
├── .editorconfig
├── .gitignore
└── README.md
```

---

## Backend Services

All backend services are Java 25 / Spring Boot 4.1.0 modules inside the Gradle multi-module build. Each service follows the same internal layout:

```
<service>/
├── build.gradle.kts
├── Dockerfile
└── src/
    ├── main/java/com/ticketbooking/<service>/
    │   ├── config/          # Spring, Kafka, Redis, Security configs
    │   ├── controller/      # REST & WebSocket handlers
    │   ├── service/         # Business logic
    │   ├── repository/      # JpaRepository extensions
    │   ├── entity/          # JPA entities (singular naming, UUID PKs)
    │   ├── dto/             # Request / response DTOs (entities never exposed)
    │   ├── messaging/       # Kafka producers & consumers
    │   ├── exception/       # Domain-specific exceptions
    │   └── mapper/          # Entity ↔ DTO mappers
    └── resources/
        ├── application.yml
        └── db/migration/    # Flyway scripts
```

### Service Responsibilities

| Service | Responsibility |
|---|---|
| **gateway** | JWT validation, rate limiting, request routing |
| **user-service** | Registration, login, profile, booking history |
| **event-service** | Concert CRUD, venue, seating layout, pricing |
| **search-service** | Elasticsearch-backed concert discovery and filtering |
| **booking-service** | Seat reservation, Redis locking, expiry reaper, WebSocket broadcast |
| **order-service** | Order creation, price calculation, discount application |
| **payment-service** | Payment gateway integration, state management (pending/success/failed) |
| **ticket-service** | QR/barcode ticket generation, email delivery, download |
| **notification-service** | Order confirmations, payment status, event reminders |
| **admin-service** | User/event management, sales reports, system health |

### Shared Library — `common-platform`

```
com.ticketbooking.common/
├── exception/        # GeneralNotFoundException, UnauthorizedActionException,
│                     # InvalidStateTransitionException, GlobalExceptionHandler
├── dto/              # ProblemDetail (RFC 7807), PageRequest, PageResponse
├── messaging/        # EventEnvelope, KafkaSerdeConfig, Topics constants
├── observability/    # TracingConfig (OpenTelemetry), CorrelationIdFilter, MetricsConfig
├── security/         # JwtAuthFilter, AuthenticatedUser
└── audit/            # AuditableEntity (@MappedSuperclass with timestamps)
```

### Booking Service — Key Detail

The booking service is the most critical component. It uses:

- **Redis distributed locks** (`SeatLockService`) to prevent double-booking under concurrency
- **Scheduled expiry** (`SeatReaperService`) to automatically release timed-out reservations
- **WebSocket broadcasting** (`SeatStatusBroadcaster`) to push real-time seat availability to all connected clients
- **Kafka consumers** to react to `payment_completed` (confirm seats) and `EventPublished` (initialize seat inventory)

Seat states: `available` → `reserved` → `sold`

---

## Frontend

Built with **Vite + React + TypeScript** (strict mode) using a feature-based folder structure.

```
frontend/src/
├── components/          # Shared UI: Button, Input, Modal, Toast, Header, Footer
├── features/
│   ├── auth/            # Login/register, Zustand auth store
│   ├── catalog/         # Event browsing, search filters
│   ├── seat-picker/     # SeatMap, WebSocket hook, reservation countdown
│   ├── checkout/        # Order summary, idempotent payment form
│   ├── tickets/         # Ticket card display
│   └── admin/           # Event form, admin API
├── pages/               # Route-level composition (HomePage, SeatPickerPage, etc.)
├── hooks/               # useDebounce, usePolling
├── services/            # Typed Axios client, WebSocket client, error mapping
├── types/               # Domain types (Event, Seat, Order, Ticket), API types
├── utils/               # currency, date, seatLayout helpers
└── styles/              # globals.css, design tokens (tokens.css)
```

**Key frontend patterns:**
- All API calls are typed; no raw `fetch` in components
- Seat availability is driven by a WebSocket connection (`useSeatWebSocket`)
- Reservation countdown is managed by a dedicated hook (`useReservationCountdown`)
- Checkout uses idempotency keys (`useIdempotentCheckout`) to prevent duplicate orders
- Errors conform to RFC 7807 Problem Details via discriminated union types

---

## Infrastructure

### Local Development

**Full stack (all services + infra):**
```bash
docker compose -f infrastructure/docker/docker-compose.yml up
```

**Infra only (run services from IDE):**
```bash
docker compose -f infrastructure/docker/docker-compose.infra.yml up
```

This spins up: PostgreSQL, Kafka, Redis, Elasticsearch, Prometheus, Grafana, and WireMock (payment stubs).

### Kubernetes (local — Docker Desktop)

Plain `kubectl` manifests that bring up the full stack (all backing infra + 10
services + an nginx load balancer) on Docker Desktop's built-in Kubernetes.

**Requirements (install / enable these first):**

| Requirement | Notes |
|---|---|
| **Docker Desktop** | With **Kubernetes enabled** (Settings → Kubernetes → *Enable Kubernetes*, wait until green). Provides both the Docker daemon for builds and a single-node cluster. |
| **kubectl** | Bundled with Docker Desktop. Point it at the cluster: `kubectl config use-context docker-desktop`. |
| **bash** | To run the `*.sh` scripts. On Windows use **Git Bash** or **WSL** (these are bash, not PowerShell). |
| **Docker Desktop memory ≈ 8 GB** | Settings → Resources → Memory. 10 JVMs + Elasticsearch + Kafka is heavy; less will crash-loop. |
| **Internet (first build only)** | The image build pulls base images and downloads Gradle dependencies. |

> No local Java, Gradle, Postgres, Redis, Kafka, or Elasticsearch needed — the
> jars build inside a container and all backing services run as pods.

**Verify prerequisites:**
```bash
docker version          # daemon responds
kubectl get nodes       # docker-desktop node is Ready
```

**Manifest layout:**
```
infrastructure/
├── docker/Dockerfile      # generic multi-stage build, param'd by SERVICE
├── build-images.sh        # builds ticketbooking/<svc>:local for all 10 services
├── deploy.sh              # applies manifests in order, waits for infra
├── teardown.sh            # kubectl delete namespace ticketing
└── k8s/
    ├── 00-namespace-config.yaml   # namespace + shared ConfigMap + Secret
    ├── 10-postgres.yaml           # 1 Postgres, auto-creates 7 DBs
    ├── 11-redis.yaml              # seat locks
    ├── 12-kafka.yaml              # single-node KRaft (no ZooKeeper)
    ├── 13-elasticsearch.yaml      # single-node, security off
    ├── 14-mailhog.yaml            # SMTP sink + web UI
    ├── 20-services.yaml           # all 10 Spring services (Deployment + Service)
    └── 30-nginx.yaml              # nginx LB → routes /api/* to each service
```

**Deploy:**
```bash
cd infrastructure
./build-images.sh          # builds all 10 images into the local Docker daemon
./deploy.sh                # creates ns, waits for infra, then deploys apps + nginx
kubectl -n ticketing get pods -w
```

Once `nginx-lb` is Ready, the API is on **http://localhost** (e.g.
`/api/users`, `/api/events`, `/api/bookings`). Tear down with `./teardown.sh`.

### CI/CD

GitHub Actions workflows (`.github/workflows/`) handle:
- Build and unit tests
- Integration tests
- Static analysis and security scans
- Docker image build and push

---

## Getting Started

### Prerequisites

- **Docker Desktop** (includes Compose) — all you need for the one-command run below.
- **Git Bash or WSL** on Windows (the scripts are bash, not PowerShell).
- ~**8 GB** free for Docker (10 JVMs + Elasticsearch + Kafka).
- _Optional, for developing outside containers:_ Java 21 + Node.js 20+.

### Run it — one command (Docker Compose)

```bash
git clone <repo-url> && cd ticket-book

./start.sh        # builds all images, then `docker compose up -d` for the whole stack
```

`start.sh` brings up Postgres, Redis, Kafka, Elasticsearch, MailHog, the 10 services, the frontend
SPA, and the nginx edge. The first run downloads dependencies (slow); later runs are cached. Give the
containers a minute to become healthy, then open:

- **App UI:** http://localhost
- **API:** http://localhost/api/...
- **MailHog (sent emails):** http://localhost:8025

Log in with a seeded demo account — see [Demo data & accounts](#demo-data--accounts).

### Stop / free resources

```bash
./stop.sh         # stop & remove containers + network, KEEP the database volume (fast restart)
./stop.sh --all   # also wipe the DB volume + remove built images (frees the most disk)
```

While it runs: `docker compose ps`, `docker compose logs -f <service>`.

> Prefer **Kubernetes**? Use the cluster path instead — see [Infrastructure → Kubernetes](#kubernetes-local--docker-desktop)
> (`infrastructure/build-images.sh` + `infrastructure/deploy.sh`).

### Develop outside containers (optional)

```bash
# Backend — run one service locally (keep the infra containers up)
cd backend && ./gradlew :booking-service:bootRun

# Frontend — hot-reload dev server, proxies /api + /ws to the running stack
cd frontend && npm install && npm run dev   # http://localhost:5173
```

### Environment Variables

All configurable variables (and which are secrets) are documented in **[`.env.example`](.env.example)**.
Copy it to a local `.env` and fill in real values:

```bash
cp .env.example .env
```

`.env` is gitignored — never commit real secrets. Where each value is actually consumed:

| Scope | Source |
|---|---|
| Local Kubernetes | shared values in the `ticket-config` ConfigMap + `ticket-secret` Secret (`infrastructure/k8s/00-namespace-config.yaml`) — change the secret values there for real deployments |
| Service from IDE / `./gradlew bootRun` | exported env vars (the `.env` values) |
| Frontend | the `VITE_*` vars go in `frontend/.env` (Vite only reads that) |
| CI / code quality | `SONAR_TOKEN` is a GitHub Actions secret — see [`docs/sonarcloud-setup.md`](docs/sonarcloud-setup.md) |

**Secrets to set** (🔐): `JWT_SECRET`, `DB_PASSWORD`, and `SONAR_TOKEN`. Per-service backend defaults
also live in each service's `application.yml`.

### Demo data & accounts

Seeded automatically on startup so you can test immediately:

- **Sample events** — `event-service` seeds venues + published concerts (browse at `http://localhost/events`).
- **Demo logins** — created by an idempotent startup seeder in `user-service`:

| Email | Password | Role |
|---|---|---|
| `customer@demo.local` | `Password123!` | CUSTOMER |
| `admin@demo.local` | `Password123!` | ADMIN (sees the Admin page) |

Manual test flow: log in → **Browse** → open an event → **Pick seats** → reserve → **Proceed to checkout**
→ **Pay** → **My Tickets**. Confirmation emails land in MailHog at `http://localhost:8025`.

---

## Development Workflow

### Backend Conventions

- **Entities**: UUID primary keys, singular class names, located in `entity/` package
- **DTOs**: All REST and WebSocket responses use DTOs — JPA entities are never exposed
- **Transactions**: `@Transactional` on all service methods that perform writes
- **Error handling**: Throw domain exceptions (`GeneralNotFoundException`, etc.); `GlobalExceptionHandler` maps them to RFC 7807 Problem Detail responses
- **Database migrations**: Flyway scripts in `resources/db/migration/`, versioned `V{n}__description.sql`

### Frontend Conventions

- TypeScript strict mode — no `any`, no implicit types
- Use `interface` for public API shapes, `type` for unions and utilities
- Components: small, single-responsibility, functional with hooks
- State: local state by default; Zustand only for auth and shared UI state
- API layer: all calls go through `services/apiClient.ts`

### Running Tests

```bash
# Backend — all modules
cd backend && ./gradlew test

# Backend — integration tests (requires Docker)
./gradlew integrationTest

# Frontend
cd frontend && npm test

# Load tests (requires running stack)
cd scripts/load-test && k6 run scenario-flash-sale.js
```

---

## Key Business Rules

| Rule | Detail |
|---|---|
| **No double booking** | Redis distributed lock per seat; only one reservation wins under concurrency |
| **Reservation expiry** | Seats auto-released after 5–10 minutes if payment is not completed |
| **Payment window** | Payment must complete within the reservation window |
| **Seat confirmation** | Seats transition to `sold` only after a successful payment event |
| **Payment failure** | Failed or timed-out payments trigger automatic seat release |
| **Flash sale fairness** | Requests queued via Kafka; rate limiting per user/IP; CAPTCHA for anti-bot |
| **Idempotency** | Order and payment endpoints accept idempotency keys to prevent duplicates |

---

## Non-Functional Requirements

| Requirement | Target |
|---|---|
| **Seat selection latency** | < 200ms |
| **System uptime** | ≥ 99.9% |
| **Scalability** | Horizontally scalable via Kubernetes; Kafka consumers scale with load |
| **Consistency** | Strong consistency for seat allocation (no double booking) |
| **Reliability** | Retry mechanisms for payments and messaging failures |
| **Availability** | Graceful degradation under load |

---

## Observability

| Signal | Tooling |
|---|---|
| **Metrics** | Prometheus + Grafana dashboards |
| **Distributed Tracing** | OpenTelemetry (auto-instrumented via `TracingConfig`) |
| **Correlation IDs** | `CorrelationIdFilter` propagates trace IDs across services |
| **Centralized Logging** | Structured logs with correlation ID attached |

**Key metrics tracked:**
- Ticket sales per second
- Seat reservation failures
- Payment success rate
- Consumer lag per Kafka topic

Grafana dashboards are pre-provisioned under `infrastructure/docker/grafana/`.

---

## Domain Model

| Entity | Description |
|---|---|
| **User** | Registered customer or organizer |
| **Event** | A concert with venue, date, and seating layout |
| **Venue** | Physical location with seat configuration |
| **Seat** | Individual bookable seat (state: available / reserved / sold) |
| **Order** | A user's checkout session linking seats and payment |
| **Payment** | Payment record (states: pending / success / failed) |
| **Ticket** | Issued digital ticket with QR/barcode after successful payment |

---

## High Availability (99.9% Uptime)

99.9% uptime allows **≤ 8.7 hours of downtime per year (≈ 43 minutes/month)**. The
strategies below are grouped by layer and tied directly to the tech in this stack.

---

### 1. Kubernetes — redundancy and safe deploys

| Practice | How |
|---|---|
| **Multiple replicas per service** | Set `replicas: ≥ 2` for all 10 services in `k8s/20-services.yaml`. For booking-service (most critical), use ≥ 3. |
| **Rolling updates, not big-bang** | Default `RollingUpdate` strategy with `maxUnavailable: 0` and `maxSurge: 1` so old pods stay alive until new ones are ready. |
| **Readiness & liveness probes** | Point both at Spring Boot Actuator: `GET /actuator/health` (liveness) and `GET /actuator/health/readiness` (readiness). Kubernetes will not route traffic to a pod that fails readiness. |
| **PodDisruptionBudgets** | Add a PDB per service (e.g., `minAvailable: 1`) so node drains during cluster maintenance never take a service fully offline. |
| **Resource limits** | Set CPU/memory `requests` and `limits` to prevent one noisy service from starving others. |

---

### 2. PostgreSQL — resilient data layer

| Practice | How |
|---|---|
| **Read replica** | Provision a streaming replica; route read-heavy services (event-service, search-service fallback) to it. |
| **Connection pooling** | Each Spring Boot service uses HikariCP (default). Tune `maximumPoolSize` per service load; avoid exhausting DB connections under flash-sale bursts. |
| **Backward-compatible migrations** | Flyway scripts must be additive — never drop or rename a column in the same deploy that removes the code that uses it. Use a two-phase approach (add → deploy → clean up). |
| **Regular backups + tested restores** | Automate pg_dump to object storage. Restore drills matter more than the backup schedule. |

---

### 3. Redis — seat lock resilience

Seat locks are the single most availability-critical component; a Redis outage loses all in-flight reservations.

| Practice | How |
|---|---|
| **Redis Sentinel or Cluster** | Replace the single-node Redis pod with a Sentinel setup (1 primary + 2 replicas + 3 sentinels) or Redis Cluster. Spring Data Redis handles failover automatically. |
| **Persistence** | Enable both RDB snapshots and AOF (`appendonly yes`) so a restart doesn't lose active seat locks. |
| **Lock TTL as a safety net** | `SeatReaperService` already handles expired locks — ensure TTLs are set on every `SETNX` call so a crash during reaper execution self-heals. |
| **Circuit breaker around Redis** | If Redis is unreachable, fail-fast the reservation endpoint (return 503) rather than letting threads pile up waiting. |

---

### 4. Kafka — durable event streaming

| Practice | How |
|---|---|
| **Replication factor ≥ 3** | All topics (`seat_reserved`, `payment_completed`, `ticket_issued`) should have `replication.factor=3` and `min.insync.replicas=2`. |
| **Producer acks=all** | Set `acks=all` on Kafka producers so a message is only acknowledged after all in-sync replicas have written it. |
| **Consumer idempotency** | Consumers already process at-least-once; ensure handlers are idempotent (check DB state before acting) so a redelivered event is a no-op. |
| **Consumer lag alerting** | Alert when lag on any topic exceeds a threshold (e.g., > 1 000 messages). Runaway lag on `payment_completed` means tickets stop being issued. |
| **Dead-letter topics** | Route messages that fail after N retries to a `*.dlq` topic and alert on non-zero DLQ size. |

---

### 5. Application — graceful degradation

| Practice | How |
|---|---|
| **Graceful shutdown** | Set `spring.lifecycle.timeout-per-shutdown-phase=30s` so in-flight requests complete before the pod terminates. |
| **Circuit breakers** | Add Resilience4j circuit breakers on downstream HTTP calls (e.g., payment-service → external gateway, booking-service → order-service). Open circuit returns 503 immediately instead of cascading timeouts. |
| **Retries with backoff** | Use Resilience4j Retry or Spring's `RetryTemplate` for transient failures (DB connection blips, Kafka producer retries). Pair with exponential backoff + jitter. |
| **Idempotency keys** | Order and payment endpoints already accept idempotency keys — ensure the client retries on 5xx (not 4xx) to tolerate transient failures without duplicate charges. |
| **Timeouts everywhere** | Set explicit connect + read timeouts on all HTTP clients (RestClient, WebClient) and Kafka producers/consumers. Unbounded waits kill thread pools. |

---

### 6. Gateway — edge protection

| Practice | How |
|---|---|
| **Rate limiting** | Already planned at the gateway — enforce per-user and per-IP limits to prevent a single actor from exhausting service capacity. |
| **Health-check routing** | Gateway should route only to pods whose readiness probe is passing. Combine with Kubernetes Service's `readinessGates`. |
| **Multiple gateway replicas** | Run ≥ 2 gateway pods; nginx (or cloud LB) distributes across them. A single gateway pod is a single point of failure. |

---

### 7. Observability — detect before users do

| Signal | Target |
|---|---|
| **Uptime SLO dashboard** | Grafana panel tracking the rolling 30-day error rate against the 99.9% budget. Alert when burn rate exceeds 2× the budget. |
| **P99 latency per service** | Alert when seat-selection P99 exceeds 200ms (the SLA target). |
| **Payment success rate** | Alert when `payment_completed` / `payment_initiated` ratio drops below 0.95. |
| **Pod restart rate** | Alert on any pod restarting more than twice in 5 minutes — early warning of crash-loops. |
| **Kafka consumer lag** | Alert per topic when lag > 1 000 messages (see §4). |

Grafana dashboards live in `infrastructure/docker/grafana/`. Add the above panels and wire alerts to your notification channel (PagerDuty, Slack, email).

---

### 8. Deployment checklist for each release

Follow this before every production deploy to avoid self-inflicted downtime:

1. Migration script reviewed for backward compatibility (no breaking DDL in same deploy).
2. New service image passes all integration tests (Testcontainers suite green).
3. Rolling update configured — `maxUnavailable: 0` confirmed in the manifest.
4. Readiness probe verified to reflect real service health (not just "JVM started").
5. Kafka consumer group offsets confirmed — no accidental `auto.offset.reset=earliest` on an existing topic.
6. Rollback plan documented — know the previous image tag and the `kubectl rollout undo` command before you deploy.

---

## Out of Scope

- PCI compliance
- Multi-currency support
- Complex refund workflows
