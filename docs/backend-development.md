# Backend — Local Development & Deployment Guide

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 21 | Gradle toolchain auto-downloads; install via [SDKMAN](https://sdkman.io) or [Adoptium](https://adoptium.net) |
| Docker + Docker Compose | 24+ | Required for infra containers and integration tests |
| Gradle | 9.4.1 | Use the included `./gradlew` wrapper — do not install separately |

---

## Running Locally

### 1. Start infrastructure

The services depend on PostgreSQL, Kafka, Redis, and Elasticsearch. Spin them up first:

```bash
# Infra only (PostgreSQL, Kafka, Redis, Elasticsearch, Prometheus, Grafana)
docker compose -f infrastructure/docker/docker-compose.infra.yml up -d
```

> Until `docker-compose.infra.yml` is created, start each dependency manually:
>
> ```bash
> # PostgreSQL
> docker run -d --name pg \
>   -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
>   -p 5432:5432 postgres:16
>
> # Kafka (with KRaft — no ZooKeeper)
> docker run -d --name kafka \
>   -e KAFKA_NODE_ID=1 \
>   -e KAFKA_PROCESS_ROLES=broker,controller \
>   -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
>   -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
>   -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
>   -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
>   -p 9092:9092 apache/kafka:3.7.0
>
> # Redis
> docker run -d --name redis -p 6379:6379 redis:7
>
> # Elasticsearch
> docker run -d --name elasticsearch \
>   -e discovery.type=single-node \
>   -e xpack.security.enabled=false \
>   -p 9200:9200 elasticsearch:8.13.0
> ```

### 2. Create per-service databases

Each service uses its own PostgreSQL database. Create them once:

```bash
docker exec -it pg psql -U postgres -c "
  CREATE DATABASE userdb;
  CREATE DATABASE eventdb;
  CREATE DATABASE bookingdb;
  CREATE DATABASE orderdb;
  CREATE DATABASE paymentdb;
  CREATE DATABASE ticketdb;
  CREATE DATABASE admindb;
"
```

### 3. Build all modules

```bash
cd backend
./gradlew build
```

### 4. Run a service

Start services individually. Each is a standalone Spring Boot application:

```bash
# From the backend/ directory
./gradlew :gateway:bootRun
./gradlew :user-service:bootRun
./gradlew :event-service:bootRun
./gradlew :search-service:bootRun
./gradlew :booking-service:bootRun
./gradlew :order-service:bootRun
./gradlew :payment-service:bootRun
./gradlew :ticket-service:bootRun
./gradlew :notification-service:bootRun
./gradlew :admin-service:bootRun
```

Recommended startup order when running all services together:

```
gateway → user-service → event-service → search-service
       → booking-service → order-service → payment-service
       → ticket-service → notification-service → admin-service
```

### 5. Verify health

```bash
curl http://localhost:8080/actuator/health   # gateway
curl http://localhost:8081/actuator/health   # user-service
curl http://localhost:8084/actuator/health   # booking-service
# ...same pattern for all ports 8080–8089
```

---

## Service Ports & Environment Variables

| Service | Port | Key env vars |
|---|---|---|
| gateway | 8080 | `USER_SERVICE_URL`, `EVENT_SERVICE_URL`, `BOOKING_SERVICE_URL`, etc. |
| user-service | 8081 | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BROKERS` |
| event-service | 8082 | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BROKERS` |
| search-service | 8083 | `ELASTICSEARCH_URI`, `KAFKA_BROKERS` |
| booking-service | 8084 | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `KAFKA_BROKERS` |
| order-service | 8085 | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BROKERS` |
| payment-service | 8086 | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BROKERS`, `PAYMENT_GATEWAY_URL` |
| ticket-service | 8087 | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BROKERS`, `MAIL_HOST`, `MAIL_PORT` |
| notification-service | 8088 | `KAFKA_BROKERS`, `MAIL_HOST`, `MAIL_PORT` |
| admin-service | 8089 | `DB_URL`, `DB_USER`, `DB_PASSWORD` |

All variables have localhost defaults — no `.env` file is needed for a standard local setup. To override, export before running:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/bookingdb
export REDIS_HOST=localhost
export KAFKA_BROKERS=localhost:9092
./gradlew :booking-service:bootRun
```

---

## Testing

```bash
# Run all tests
cd backend && ./gradlew test

# Run tests for one module
./gradlew :booking-service:test

# Run a single test class
./gradlew :user-service:test --tests "com.ticketbooking.user.UserServiceTests"

# Run integration tests (boots real containers via Testcontainers — requires Docker)
./gradlew :booking-service:bootTestRun
```

Testcontainers spins up PostgreSQL, Kafka, and Redis containers automatically during integration tests. No manual setup is needed.

---

## Database Migrations

Flyway runs automatically on startup. Migrations live in each service under:

```
<service>/src/main/resources/db/migration/V{n}__description.sql
```

To run migrations without starting the full service:

```bash
./gradlew :user-service:flywayMigrate \
  -Dflyway.url=jdbc:postgresql://localhost:5432/userdb \
  -Dflyway.user=postgres \
  -Dflyway.password=postgres
```

---

## Deployment

### Docker

Build a single service image:

```bash
cd backend
./gradlew :booking-service:bootBuildImage
```

This uses Spring Boot's built-in Buildpacks integration — no `Dockerfile` is needed. The image is tagged `com.ticketbooking/booking-service:0.0.1-SNAPSHOT`.

Build all service images:

```bash
./gradlew bootBuildImage
```

### Docker Compose (full stack)

```bash
docker compose -f infrastructure/docker/docker-compose.yml up
```

This starts all services plus infrastructure. Individual services can be excluded:

```bash
docker compose -f infrastructure/docker/docker-compose.yml up gateway booking-service redis kafka pg
```

### Kubernetes (Kustomize)

Apply an environment overlay:

```bash
# Dev
kubectl apply -k infrastructure/k8s/overlays/dev

# Staging
kubectl apply -k infrastructure/k8s/overlays/staging

# Prod
kubectl apply -k infrastructure/k8s/overlays/prod
```

Check rollout status:

```bash
kubectl rollout status deployment/booking-service -n ticket-booking
kubectl get pods -n ticket-booking
```

### Helm

Install or upgrade the umbrella chart:

```bash
# Dev
helm upgrade --install ticket-booking infrastructure/helm/ticket-booking/ \
  -f infrastructure/helm/ticket-booking/values.dev.yaml \
  --namespace ticket-booking --create-namespace

# Staging
helm upgrade --install ticket-booking infrastructure/helm/ticket-booking/ \
  -f infrastructure/helm/ticket-booking/values.staging.yaml \
  --namespace ticket-booking
```

Uninstall:

```bash
helm uninstall ticket-booking -n ticket-booking
```

---

## Observability

All services expose metrics and traces on their `/actuator` endpoints.

| Endpoint | Purpose |
|---|---|
| `/actuator/health` | Liveness / readiness |
| `/actuator/info` | Build info |
| `/actuator/prometheus` | Prometheus scrape endpoint |

**Grafana:** `http://localhost:3000` (admin / admin)

**Prometheus:** `http://localhost:9090`

Distributed traces are exported automatically via Micrometer Brave. The sampling rate is `1.0` (100%) in all environments by default — lower this in production via `management.tracing.sampling.probability`.

---

## Common Troubleshooting

**`Unable to acquire connection` on startup**
Postgres is not ready or the database doesn't exist. Check `docker ps` and re-run the `CREATE DATABASE` step.

**`LEADER_NOT_AVAILABLE` Kafka error on first run**
Kafka topic auto-creation is delayed. Wait a few seconds and retry, or pre-create topics:

```bash
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic seat_reserved --partitions 3 --replication-factor 1
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic payment_completed --partitions 3 --replication-factor 1
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic ticket_issued --partitions 3 --replication-factor 1
```

**Flyway migration checksum mismatch**
Never edit an existing migration file. Add a new `V{n+1}__description.sql` instead.

**`Port already in use`**
Find and kill the conflicting process:

```bash
lsof -ti:8084 | xargs kill -9
```
