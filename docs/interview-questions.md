# Interview Q&A — Concert Ticket Booking System

Answers are grounded in the actual implementation. Use these as talking points, not
scripts. Every answer references specific classes, files, or design decisions from
this codebase.

---

## Table of Contents

1. [System Design](#1-system-design)
2. [Kubernetes](#2-kubernetes)
3. [Java](#3-java)
4. [Spring Boot](#4-spring-boot)
5. [AWS](#5-aws)

---

## 1. System Design

---

**Q1. How do you prevent double-booking when two users try to reserve the same seat at the same time?**

The guard is a Redis distributed lock in `SeatLockService`. When a user reserves seat `S` for event `E`, the service executes:

```
SET seat:{eventId}:{seatId} {userId} NX EX {ttl}
```

`NX` makes the write atomic and conditional — it only succeeds if the key does not already exist. Only one thread (across any service replica) wins. The loser gets `false` and receives a `409 Conflict` response. No database-level locking is needed, and no round-trip transaction is required.

The TTL (5–10 minutes) ensures that if payment never completes, the lock self-expires and `SeatReaperService` (a `@Scheduled` bean) scans `RESERVED` seats, confirms their Redis key is gone, and transitions them back to `AVAILABLE` — broadcasting the change over WebSocket so all connected seat maps update instantly.

---

**Q2. Walk me through the full seat-to-ticket flow.**

```
1. User opens seat map  →  GET /api/bookings/events/{id}/seats
                           Returns current seat states from DB.
                           WebSocket connection opened to /topic/events/{id}/seats.

2. User selects seats   →  POST /api/bookings/reserve
                           SeatLockService: Redis SET NX EX (atomic lock).
                           Seat state → RESERVED in DB.
                           SeatStatusBroadcaster pushes update to all WebSocket clients.
                           Response includes expiresAt so UI shows countdown.

3. User pays            →  POST /api/orders  (idempotency key in header)
                           POST /api/payments (idempotency key in header)
                           payment-service calls external gateway.

4. Payment succeeds     →  payment-service publishes Kafka event: payment_completed
                           booking-service PaymentEventConsumer: seat → SOLD, lock released.
                           ticket-service TicketIssuedConsumer: generates QR, sends email.
                           Kafka topic: ticket_issued

5. Payment fails        →  seat lock expires naturally (or POST /api/bookings/release).
                           SeatReaperService catches any stragglers.
```

---

**Q3. Why Kafka instead of synchronous REST calls between services?**

Three reasons that matter for a ticketing platform:

- **Decoupling under load.** During a flash sale, `booking-service` can produce `seat_reserved` events at full throughput without waiting for `order-service`, `payment-service`, and `ticket-service` to be ready. Each service consumes at its own pace.
- **Fault isolation.** If `notification-service` is down, no bookings are lost — messages accumulate in the topic and are processed when the service recovers. A synchronous call chain would propagate the failure upstream.
- **Replay.** Kafka retains events. If a bug corrupts `ticket-service`'s state, you can reset the consumer offset and replay `ticket_issued` to rebuild state without re-running payments.

The trade-off is eventual consistency: the seat transitions to `SOLD` asynchronously after the `payment_completed` event is consumed. For this system, that delay is acceptable (milliseconds in practice).

---

**Q4. How do you handle idempotency in checkout to prevent duplicate charges?**

Both `order-service` and `payment-service` have an `idempotencyKey` unique column on their entity. The frontend generates a UUID per checkout session and sends it as a request header. On arrival:

- If the key is new → process normally, persist the key with the result.
- If the key already exists → return the stored result immediately, no re-processing.

This means the user or client can safely retry on a `5xx` or network timeout without risk of a duplicate order or double charge. The idempotency check happens inside a `@Transactional` method on a table with a unique constraint, so concurrent retries are safe.

---

**Q5. Why is `booking-service` the most complex service? What would happen if it went down?**

It owns the most critical state transition (`AVAILABLE → RESERVED → SOLD`) and coordinates three technologies simultaneously — PostgreSQL (seat state), Redis (locks), and WebSocket (live broadcast). It also consumes Kafka events from `payment-service` to confirm seats.

If `booking-service` went down mid-reservation:
- Active Redis locks still have their TTL and will expire naturally — seats are not permanently locked.
- `SeatReaperService` will catch any `RESERVED` seats with expired locks on restart.
- In-flight Kafka messages (`payment_completed`) are durably stored and will be redelivered when the service comes back up (at-least-once delivery).
- WebSocket clients lose their connection and must reconnect; the seat map is re-fetched via HTTP on reconnect.

The system self-heals without manual intervention.

---

**Q6. How does the search service work, and when would you switch fully to Elasticsearch?**

`search-service` is Elasticsearch-backed with no relational DB. Currently, the event catalog queries `event-service` directly via REST (simpler, sufficient for the current load). `search-service` is the scale-out path:

- Full-text search across title, artist, description
- Faceted filters (location, date range, price tier)
- Fuzzy matching and relevance ranking

The switch is triggered when query complexity or volume exceeds what a PostgreSQL `LIKE` / `ILIKE` can handle without full-table scans. The migration path is: `event-service` publishes `EventPublished` / `EventUpdated` Kafka events → `search-service` consumes and indexes into Elasticsearch.

---

**Q7. How do you ensure the event-driven flow is reliable (at-least-once vs exactly-once)?**

The current design uses **at-least-once delivery** (Kafka default):

- Producers use `acks=all` (all ISR replicas acknowledge).
- Consumers commit offsets only after successful processing.
- If a consumer crashes before committing, the message is redelivered.

Consumers are made idempotent to handle redelivery safely:
- `PaymentEventConsumer`: checks if seat is already `SOLD` before transitioning — a no-op if replayed.
- `ticket-service`: idempotency key on the ticket record prevents duplicate issuance.

True exactly-once requires Kafka transactions + transactional outbox pattern, which adds complexity. For this use case, idempotent consumers give the same observable behavior at lower cost.

---

**Q8. What are the scalability bottlenecks and how would you address them?**

| Bottleneck | Approach |
|---|---|
| **Seat lock contention (Redis)** | Redis is single-threaded per key; contention on popular seats is natural. Use Redis Cluster to shard across nodes. For extreme load (stadium concerts), use a queue-based virtual waiting room. |
| **PostgreSQL write throughput** | Add a read replica for catalog reads. For the seat state table, consider partitioning by event. |
| **Kafka consumer lag** | Add partitions and scale consumer replicas (one consumer per partition max). Monitor lag via Prometheus. |
| **WebSocket connections** | STOMP broker is in-process; for multi-replica `booking-service`, add a dedicated message broker (Redis Pub/Sub or a full broker like RabbitMQ) so a seat change on replica A broadcasts to clients connected on replica B. |
| **Flash sale burst** | Rate limit at the gateway (per user + per IP). Queue reservation requests through Kafka so DB writes are absorbed gradually. |

---

**Q9. How does real-time seat availability work across multiple browser clients?**

`booking-service` runs a STOMP broker (`@EnableWebSocketMessageBroker`). Each client subscribes to `/topic/events/{eventId}/seats` on page load. Whenever `SeatService.reserve()` or the reaper transitions a seat, `SeatStatusBroadcaster.convertAndSend(destination, payload)` pushes a delta to all subscribers on that topic.

The limitation in the current setup: the in-process STOMP broker only reaches clients connected to that pod. With multiple `booking-service` replicas, you need to promote the broker to an external one (e.g., enable RabbitMQ STOMP relay or use Redis Pub/Sub as the fanout layer).

---

**Q10. How does the system handle a payment gateway timeout?**

`payment-service` sets explicit HTTP timeouts on the external gateway client. If the timeout fires:

1. The payment record stays in `PENDING` state (not `FAILED` yet).
2. A background job or Kafka retry re-queries the gateway's idempotency endpoint to check the actual outcome.
3. If the gateway confirms failure, `payment-service` publishes a `payment_failed` event.
4. `booking-service` consumes it and releases the seat lock.
5. If the gateway timed out on their end (money not moved), the idempotency key prevents a double charge on retry.

---

## 2. Kubernetes

---

**Q11. How is the local Kubernetes stack built and deployed?**

The build is two-phase to avoid re-downloading dependencies per service:

1. **Builder image** (`Dockerfile.build`): a single `./gradlew bootJar -x test --continue` pass compiles all 10 services. A BuildKit cache mount (`/root/.gradle`) persists the Gradle dist and dependency cache across runs, so subsequent builds are fast. `--continue` surfaces all compile errors at once.
2. **Runtime images** (`Dockerfile.runtime`): copies the jar out of the builder — takes seconds per service.

`build-images.sh` supports full rebuilds, single-service rebuilds (`./build-images.sh booking-service`), and skipping the builder entirely (`SKIP_BUILDER=1`) when only repackaging is needed.

`deploy.sh` applies manifests in numeric order: `00-` (namespace + config) → `10-13` (infra: Postgres, Redis, Kafka, Elasticsearch) → `14` (MailHog) → `20-29` (one file per service) → `30` (nginx) → `40` (frontend).

---

**Q12. What are the three health probes and how are they configured?**

Each service has all three:

- **startupProbe**: `tcpSocket` on the service port, `failureThreshold: 60`, `periodSeconds: 5` → allows up to 5 minutes for the JVM to boot and Flyway to run migrations before Kubernetes declares the pod dead.
- **readinessProbe**: `tcpSocket` on the service port, `periodSeconds: 10` → tells the Service to stop routing traffic to this pod if it's not ready (e.g., DB connection not yet established).
- **livenessProbe**: `tcpSocket` on the service port, `periodSeconds: 20` → restarts the pod if it becomes unresponsive (deadlock, OOM, etc.).

Production improvement: replace `tcpSocket` with `httpGet /actuator/health/readiness` and `/actuator/health/liveness` (Spring Boot Actuator exposes these natively) for deeper health checks — for example, readiness can verify DB and Redis connectivity, not just that the port is open.

---

**Q13. How is configuration injected into services, and how are secrets managed?**

All services use `envFrom` to pull from two Kubernetes objects:

- `ticket-config` (ConfigMap): non-sensitive values — `KAFKA_BROKERS`, `REDIS_HOST`, `REDIS_PORT`, `ELASTICSEARCH_URI`, `MAIL_HOST`, `DB_USER`.
- `ticket-secret` (Secret): sensitive values — `DB_PASSWORD`, `JWT_SECRET`.

Each service also sets a per-service `DB_URL` env var (e.g., `jdbc:postgresql://postgres:5432/userdb`) directly in its Deployment manifest, since each service has its own database.

Spring Boot services read these via `${ENV_VAR:default}` in `application.yml`, so the same jar runs locally (using defaults) and in-cluster (using injected values) without code changes.

In production, replace the in-cluster Secret with AWS Secrets Manager + the External Secrets Operator, or use Vault.

---

**Q14. How does nginx route traffic to backend services?**

nginx runs as a `Service type: LoadBalancer` in the `ticketing` namespace. It is the only component exposed externally (port 80). Routing rules:

- `/api/users/*` → `user-service:8081`
- `/api/bookings/*` → `booking-service:8084`
- `/api/events/*` → `event-service:8082`
- `/ws` → `booking-service:8084` (WebSocket upgrade)
- `/` → `frontend:3000` (React SPA)

This bypasses the Spring Cloud Gateway in the local setup (simpler, faster iteration). The Spring Gateway pod is still deployed and can act as the real entry point for staging/production, where it enforces JWT validation and rate limiting before any request reaches a service.

---

**Q15. What would you change to make this Kubernetes setup production-ready?**

| Area | Change |
|---|---|
| **Replicas** | Set `replicas: ≥ 2` for all services; `≥ 3` for `booking-service`. |
| **PodDisruptionBudgets** | Add PDB per service (`minAvailable: 1`) to survive node drains. |
| **Rolling update** | Set `maxUnavailable: 0`, `maxSurge: 1` to ensure zero-downtime deploys. |
| **Probes** | Switch from `tcpSocket` to `httpGet /actuator/health/readiness` + `/liveness`. |
| **Persistent storage** | Replace `emptyDir` with PersistentVolumeClaims for Postgres and Redis (or use managed services). |
| **Resource limits** | Tune CPU limits; currently only memory limits are set (CPU is unbounded). |
| **Secrets** | External Secrets Operator pulling from AWS Secrets Manager or Vault. |
| **Namespace isolation** | Separate namespaces for infra vs application workloads; RBAC per namespace. |
| **Ingress** | Replace nginx Deployment with an Ingress Controller (nginx-ingress or AWS ALB) + TLS termination. |
| **HPA** | Add HorizontalPodAutoscaler on CPU/RPS for `booking-service` and `gateway`. |

---

**Q16. What is the namespace structure and why does it matter?**

All resources live in the `ticketing` namespace. Kubernetes namespaces provide:

- **Logical isolation**: `kubectl get pods -n ticketing` scopes commands to this system only.
- **RBAC boundary**: service accounts and roles can be scoped per namespace.
- **Resource quotas**: LimitRange and ResourceQuota can cap total CPU/memory for the namespace.
- **Network policies**: deny all inter-namespace traffic by default, allow only what's needed.

In production you would typically split into `ticketing-infra` (Postgres, Redis, Kafka) and `ticketing-app` (the services), with a NetworkPolicy allowing only app pods to reach infra pods.

---

**Q17. How would you do a zero-downtime rolling deploy of `booking-service`?**

1. Build a new image (`./build-images.sh booking-service`).
2. Update the image tag in `k8s/20-booking-service.yaml`.
3. `kubectl apply -f k8s/20-booking-service.yaml` — Kubernetes starts a new pod with `maxSurge: 1`.
4. The new pod must pass `startupProbe` (JVM boot + Flyway) then `readinessProbe` before traffic is shifted.
5. Only after the new pod is `Ready` does Kubernetes terminate an old pod (`maxUnavailable: 0`).
6. In-flight WebSocket connections to the old pod are dropped; clients reconnect and re-subscribe.

Key pre-deploy checklist: ensure the new Flyway migration is backward-compatible (old pods can still run against the new schema during the overlap window). A `NOT NULL` column addition must have a default or a two-phase deploy.

---

## 3. Java

---

**Q18. What Java 21 features does this project use?**

- **Records** — all DTOs are `record` types (e.g., `EventResponse`, `BookingRequest`). Immutable, concise, compiler-generated `equals`/`hashCode`/`toString`. Fit perfectly for request/response objects that should never be mutated.
- **Text blocks** — used in SQL strings and JSON literals in tests.
- **Pattern matching for `instanceof`** — used in exception handlers and mapper code.
- **Sealed classes** — candidate for the seat state machine (`AVAILABLE | RESERVED | SOLD`) to make exhaustive `switch` expressions compiler-enforced.
- **Virtual threads (Project Loom)** — Spring Boot 4 enables virtual threads by default (`spring.threads.virtual.enabled=true`). All `@RestController` request-handling threads become virtual, dramatically increasing I/O-bound throughput without adding thread pool sizing headaches.

---

**Q19. Why are UUIDs used as primary keys instead of auto-increment longs?**

Three reasons relevant to this architecture:

1. **Distributed ID generation**: with multiple service instances, UUIDs can be generated client-side (in Java) without a DB round-trip. Auto-increment requires a DB sequence, which is a bottleneck and a single point of failure.
2. **Merging and migration**: UUIDs are globally unique, so merging data from staging to production or across shards is safe. Numeric IDs collide.
3. **Security**: sequential numeric IDs are enumerable — a caller can guess `GET /api/orders/1001` to probe records they don't own. UUIDs are not guessable.

Trade-off: UUID primary keys are 16 bytes vs 8 bytes for a `bigint`, which slightly increases index size and reduces cache efficiency. For most services in this system, that cost is negligible.

---

**Q20. How does the `AuditableEntity` base class work?**

```java
@MappedSuperclass
public abstract class AuditableEntity {
    @Column(updatable = false)
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
```

Every entity extends this, so all tables get `created_at` and `updated_at` automatically. JPA lifecycle callbacks (`@PrePersist`, `@PreUpdate`) fire within the same transaction — no extra queries, no AOP proxy required.

---

**Q21. Why are DTO types `record` instead of plain classes?**

Records are ideal for DTOs because:

- **Immutability by default** — all fields are `final`; no accidental mutation after deserialization.
- **Compact declaration** — a record with 5 fields is 1 line vs 30+ lines of boilerplate with getters/setters.
- **Value-based equality** — `equals()` and `hashCode()` compare field values, not identity, which is correct for data objects.
- **Interop with Jackson** — Spring Boot's Jackson auto-configuration supports records out of the box (constructor-based deserialization).

Entities are still regular classes because JPA requires a no-arg constructor and mutable state.

---

**Q22. How does thread safety work in `SeatLockService`?**

The Redis `SET NX EX` command is atomic on the Redis side — even if two threads call it concurrently, only one gets `true`. In the Spring Boot service, `SeatLockService` is a `@Service` singleton (shared across all request threads), which is safe because it holds no mutable instance state — every operation is a stateless Redis command via `StringRedisTemplate`.

With virtual threads (enabled in Spring Boot 4), hundreds of concurrent reservation requests can be in-flight simultaneously without saturating an OS thread pool. Each request parks its virtual thread on the Redis call, and the carrier thread is freed to serve other requests.

---

**Q23. How is code coverage measured and reported?**

JaCoCo is applied to every subproject in the root `build.gradle`:

```groovy
apply plugin: 'jacoco'

tasks.named('test') {
    useJUnitPlatform()
    finalizedBy tasks.named('jacocoTestReport')
}

tasks.named('jacocoTestReport') {
    reports { xml.required = true }
}
```

`./gradlew test` automatically runs `jacocoTestReport` after tests. SonarCloud reads the XML report (`build/reports/jacoco/test/jacocoTestReport.xml`) and displays coverage metrics per module. The `SONAR_TOKEN` is stored as a GitHub Actions secret and passed to `./gradlew sonar` in CI.

---

## 4. Spring Boot

---

**Q24. How does the Kafka consumer in `PaymentEventConsumer` work?**

```java
@KafkaListener(topics = Topics.PAYMENT_COMPLETED, groupId = "booking-service")
public void onPaymentCompleted(EventEnvelope<PaymentCompletedEvent> envelope) {
    seatService.confirmSeats(envelope.payload().bookingId());
}
```

- `@KafkaListener` binds to the `payment_completed` topic.
- The `groupId` scopes offset tracking — if multiple `booking-service` replicas run, they form a consumer group and Kafka distributes partitions among them (no duplicate processing per partition).
- `EventEnvelope<T>` is the shared wrapper from `common-platform` — it carries the payload plus metadata (eventId, timestamp, sourceService) for traceability.
- On `confirmSeats`: seat state transitions to `SOLD`, Redis lock is deleted, `SeatStatusBroadcaster` pushes the update.

If processing throws an exception, the offset is not committed, and the message is retried. Configure a `DefaultErrorHandler` with exponential backoff and a dead-letter topic to avoid infinite retry loops.

---

**Q25. How does Redis seat locking work with `Spring Data Redis`?**

`SeatLockService` uses `StringRedisTemplate`:

```java
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent("seat:" + eventId + ":" + seatId, userId, Duration.ofMinutes(ttl));
```

`setIfAbsent` maps to `SET NX EX` — atomic, no Lua script needed. The return value is `true` (lock acquired) or `false`/`null` (already taken).

To release:
```java
redisTemplate.delete("seat:" + eventId + ":" + seatId);
```

The TTL acts as a safety net: even if `release()` is never called (crash, timeout), the key expires and the seat becomes available again. `SeatReaperService` handles the DB side — it polls `RESERVED` seats and for each one checks if the Redis key still exists; if not, it sets the seat back to `AVAILABLE`.

---

**Q26. How do Flyway migrations work in this project, and what was the Boot 4 gotcha?**

Each DB-backed service has migration scripts at `src/main/resources/db/migration/V{n}__description.sql`. Flyway runs on startup, compares `flyway_schema_history` in the DB, and applies any pending migrations before Spring's JPA layer validates the schema (`ddl-auto: validate`).

Boot 4 gotcha: Flyway 11 (shipped with Spring Boot 4) split database driver support into separate modules. `flyway-core` alone does not include the PostgreSQL driver adapter — adding only `flyway-core` means no migrations run, then `ddl-auto: validate` fails with "table not found". Fix: add `runtimeOnly 'org.flywaydb:flyway-database-postgresql'` to every service's `build.gradle`.

---

**Q27. How does Spring Cloud Gateway route and protect requests?**

`gateway` (port 8080) uses `spring-cloud-starter-gateway-server-webmvc` (Spring Cloud 2025.1.x / Gateway 5.0 — note the renamed artifact). Routes are configured in `application.yml`:

```yaml
spring.cloud.gateway.server.webmvc.routes:
  - id: user-service
    uri: http://user-service:8081
    predicates:
      - Path=/api/users/**
```

The `JwtAuthFilter` (`OncePerRequestFilter`) intercepts every request, extracts the `Authorization: Bearer <token>` header, validates the HS256 JWT (secret from `JWT_SECRET` env), and either sets the security context or returns `401`. Currently a stub in local dev — enforcement is the next hardening step.

Rate limiting is configured as a `RequestRateLimiterGatewayFilterFactory` using Redis as the token bucket store — the same Redis instance used for seat locks.

---

**Q28. How is RFC 7807 ProblemDetail error handling implemented?**

`GlobalExceptionHandler` in `common-platform` is a `@RestControllerAdvice`. Every service scans `com.ticketbooking` so it's picked up automatically.

```java
@ExceptionHandler(GeneralNotFoundException.class)
ProblemDetail handleNotFound(GeneralNotFoundException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    pd.setType(URI.create("https://ticketbooking.com/errors/not-found"));
    return pd;
}
```

Spring Boot 4 / Spring MVC 6 natively serializes `ProblemDetail` to `application/problem+json`. Clients get a consistent error shape across all services:
```json
{
  "type": "https://ticketbooking.com/errors/not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Event 123 not found"
}
```

---

**Q29. How does Testcontainers work for integration tests?**

`booking-service` integration tests use `@Testcontainers` + `@SpringBootTest`:

```java
@Testcontainers
@SpringBootTest
class BookingServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @Container
    static KafkaContainer kafka = new KafkaContainer(...);
}
```

Testcontainers spins up real Docker containers for Postgres and Kafka before the Spring context loads, overrides `spring.datasource.url` and `spring.kafka.bootstrap-servers` via `@DynamicPropertySource`, and tears them down after the test class. This means integration tests run against the real stack — not mocks — in CI without any pre-provisioned infrastructure.

---

**Q30. How does the WebSocket STOMP setup work?**

`WebSocketConfig` in `booking-service`:

```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
}

@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");
    registry.setApplicationDestinationPrefixes("/app");
}
```

- Clients connect to `ws://localhost/ws` (proxied by nginx with `Upgrade: websocket`).
- They subscribe to `/topic/events/{eventId}/seats`.
- `SeatStatusBroadcaster.convertAndSend("/topic/events/" + eventId + "/seats", payload)` pushes deltas to all subscribers.
- The in-process broker (`enableSimpleBroker`) works for a single replica; for multi-replica, configure an external broker relay.

---

**Q31. How does Spring Boot Actuator support observability?**

Actuator is included in every service (`spring-boot-starter-actuator`). Key endpoints:

- `GET /actuator/health` — liveness: is the JVM alive?
- `GET /actuator/health/readiness` — readiness: are DB, Redis, Kafka connections up?
- `GET /actuator/prometheus` — Micrometer metrics in Prometheus text format.

Prometheus scrapes `/actuator/prometheus` from each pod. Grafana dashboards (pre-provisioned in `infrastructure/docker/grafana/`) visualize ticket sales/second, Kafka consumer lag, P99 latency, and payment success rate. OpenTelemetry auto-instrumentation (via `TracingConfig`) exports distributed traces so a single user request can be followed across all service hops.

---

**Q32. What is the `@Transactional(readOnly = true)` pattern and why does it matter?**

Read-only transactions are declared on service methods that only query data (e.g., `EventService.getEvent()`). This tells:

- **JPA / Hibernate**: skip dirty-checking at flush time — no need to compare entity snapshots, reducing CPU cost.
- **PostgreSQL driver**: can route the connection to a read replica if the connection pool is configured to do so.
- **Spring**: prevents accidental writes — any `save()` call inside a `readOnly` transaction will throw.

The convention in this codebase: `@Transactional` on all write methods; `@Transactional(readOnly = true)` on all read methods. This is enforced at the service layer — repositories themselves are not annotated.

---

## 5. AWS

---

**Q33. How would you map this stack to AWS managed services?**

| Current (local k8s) | AWS equivalent |
|---|---|
| PostgreSQL pod | **Amazon RDS for PostgreSQL** (Multi-AZ for HA, read replicas for scale) |
| Redis pod | **Amazon ElastiCache for Redis** (cluster mode for sharding, automatic failover) |
| Kafka (KRaft pod) | **Amazon MSK** (managed Kafka; handles broker HA, storage, upgrades) |
| Elasticsearch pod | **Amazon OpenSearch Service** |
| Docker Desktop k8s | **Amazon EKS** (managed control plane) |
| nginx LoadBalancer | **AWS ALB Ingress Controller** (replaces nginx; native TLS termination, WAF integration) |
| MailHog | **Amazon SES** (production email delivery) |
| Local Docker daemon | **Amazon ECR** (private container registry) |
| ConfigMap + Secret | **AWS Secrets Manager** + External Secrets Operator (or SSM Parameter Store for non-sensitive config) |

---

**Q34. How would you deploy this to EKS?**

1. **ECR**: push images with `docker push <account>.dkr.ecr.<region>.amazonaws.com/ticketbooking/<svc>:<tag>`.
2. **EKS cluster**: provision with `eksctl` or Terraform. Enable IRSA (IAM Roles for Service Accounts) so pods can access AWS services without long-lived credentials.
3. **Manifests**: update image refs from `ticketbooking/user-service:local` to the ECR URI. Change `imagePullPolicy: IfNotPresent` to `Always` (or pin to a digest).
4. **ALB Ingress Controller**: install via Helm. Replace `k8s/30-nginx.yaml` with an `Ingress` resource; ALB handles TLS termination with ACM certs and routes to services.
5. **External Secrets Operator**: syncs Secrets Manager secrets into Kubernetes `Secret` objects, removing the need to store `DB_PASSWORD` in a manifest.
6. **CI/CD**: GitHub Actions → build → `docker push ECR` → `kubectl rollout restart` (or Argo CD GitOps sync).

---

**Q35. How would you manage secrets on AWS?**

Replace the Kubernetes `Secret` (plaintext in YAML) with AWS Secrets Manager:

1. Store `DB_PASSWORD`, `JWT_SECRET`, etc. in Secrets Manager.
2. Install the External Secrets Operator in the cluster.
3. Create an `ExternalSecret` CR pointing to the Secrets Manager path.
4. ESO syncs the value into a native Kubernetes `Secret` on a rotation schedule.
5. Pods reference the `Secret` as before — no code changes.

The service account for ESO pods gets an IAM role (via IRSA) with `secretsmanager:GetSecretValue` permission — no static AWS keys anywhere.

For non-sensitive config (Kafka broker endpoint, Redis host), use SSM Parameter Store and a `ParameterStore`-backed `ExternalSecret`.

---

**Q36. How would you set up CI/CD for this project on AWS?**

Pipeline: **GitHub Actions → ECR → EKS**

```
push to main
  → GitHub Actions: ./gradlew build test
  → ./gradlew sonar (SonarCloud quality gate)
  → docker build + push to ECR (each changed service, detected by path filters)
  → kubectl set image deployment/<svc> <svc>=<ecr-uri>:<git-sha>
     (or Argo CD syncs the updated image tag from git)
```

Key practices:
- **Path-based triggers**: only rebuild and redeploy services whose source changed (Gradle `--continue` helps catch all compile errors in one pass).
- **Image tags = git SHA**: makes rollback trivial (`kubectl set image ... <svc>=<ecr-uri>:<previous-sha>`).
- **Argo CD (GitOps)**: manifests live in git; Argo CD continuously reconciles cluster state. Audit trail comes from git history, not shell history.

---

**Q37. How would you handle auto-scaling on AWS?**

- **HPA (CPU/RPS)**: add `HorizontalPodAutoscaler` for `booking-service` and `gateway` — scale pods when CPU > 60% or request rate exceeds threshold. Requires the Metrics Server installed in EKS.
- **KEDA**: for Kafka-driven scaling — scale `booking-service` consumer pods based on consumer group lag on the `payment_completed` topic. No lag = 1 pod; lag spike = N pods.
- **Cluster Autoscaler or Karpenter**: add EC2 nodes when pod scheduling fails due to insufficient node capacity. Karpenter is preferred on EKS — it provisions right-sized nodes within seconds based on pod resource requests.

---

**Q38. How would you handle observability on AWS?**

| Signal | Tool |
|---|---|
| **Metrics** | Keep Prometheus + Grafana (self-managed) or migrate to **Amazon Managed Grafana + Amazon Managed Service for Prometheus** |
| **Logs** | FluentBit DaemonSet → **Amazon CloudWatch Logs** (or OpenSearch) |
| **Traces** | OpenTelemetry SDK (already in `TracingConfig`) → **AWS X-Ray** (via ADOT Collector sidecar) |
| **Alarms** | CloudWatch Alarms on EKS metrics (pod restarts, CPU, memory); PagerDuty/Opsgenie integration |

The OpenTelemetry auto-instrumentation in `common-platform.observability.TracingConfig` already instruments all Spring MVC requests, Kafka sends/receives, and JDBC calls. Switching the export endpoint from a local collector to the ADOT sidecar is a config change, not a code change.

---

**Q39. How would you design the VPC for this system on AWS?**

```
VPC (10.0.0.0/16)
├── Public subnets (2 AZs)     — ALB only (no backend pods here)
├── Private subnets (2 AZs)    — EKS worker nodes, pods
└── Isolated subnets (2 AZs)   — RDS, ElastiCache, MSK (no internet route)
```

- Backend pods run in private subnets; they initiate outbound connections (to payment gateway) via a NAT Gateway. Inbound traffic comes only through the ALB.
- Databases run in isolated subnets reachable only from private subnet security groups.
- Security groups enforce least privilege: only `booking-service` SG can reach Redis SG on port 6379; only the services that need DB access get a rule into the RDS SG.

---

**Q40. What would you add to the current GitHub Actions CI to make it production-grade?**

The existing workflows cover build, test, static analysis, and image push. Gaps to fill:

1. **Dependency scanning**: add `trivy` image scan or Snyk to fail the pipeline on critical CVEs.
2. **SAST**: SonarQube is present; also add OWASP Dependency-Check for known vulnerable transitive dependencies.
3. **Integration test gate**: run `./gradlew integrationTest` (Testcontainers) — currently run only locally.
4. **Load test**: run a k6 smoke test (`scripts/load-test/`) against a staging environment before promoting to production.
5. **GitOps PR**: instead of `kubectl set image` in CI, have CI open a PR to the infra repo updating the image tag; Argo CD auto-syncs after merge.
6. **Rollback step**: add a manual approval gate before production deploy; automatic rollback if error-rate SLO is breached within 5 minutes of deploy (use Argo Rollouts canary or blue-green).
