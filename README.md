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

- Java 25
- Node.js 20+
- Docker & Docker Compose
- Kubernetes CLI (`kubectl`) + Helm (for cluster deployments)

### First-time Setup

```bash
# Clone the repo
git clone <repo-url>
cd ticket-booking

# Run the bootstrap script (installs tooling, creates Kafka topics, seeds DB)
./scripts/bootstrap.sh

# Start infrastructure
docker compose -f infrastructure/docker/docker-compose.infra.yml up -d

# Build all backend services
cd backend
./gradlew build

# Start frontend
cd ../frontend
npm install
npm run dev
```

### Environment Variables

Each backend service has:
- `application.yml` — shared defaults
- `application-local.yml` — local dev overrides
- `application-staging.yml` — staging overrides

Frontend environment files: `.env.development`, `.env.staging`

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

## Out of Scope

- PCI compliance
- Multi-currency support
- Complex refund workflows
