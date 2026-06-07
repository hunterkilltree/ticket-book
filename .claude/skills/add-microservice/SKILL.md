---
name: add-microservice
description: Scaffold a new Spring Boot microservice in this ticket-booking backend end to end. Use when asked to "add a service", "create a new microservice", "add a module", or wire up a new backend service. Handles the Gradle module, package layout, application.yml, settings.gradle entry, Flyway + Postgres DB, the Kubernetes manifest, and the nginx/gateway routes so the new service matches existing conventions.
---

# Add a microservice

Create a new Spring Boot service that matches this repo's conventions. Read
`backend/CLAUDE.md` (code conventions) and `infrastructure/CLAUDE.md` (deploy) first.

## Inputs to settle before scaffolding

- **Service name**: kebab-case, ending in `-service` (e.g. `review-service`).
- **Base package**: `com.ticketbooking.<short>` (e.g. `review`).
- **Port**: next free port after the current max. Existing: 8080 gateway, 8081–8089 used.
  Pick the next unused (e.g. `8090`). Confirm none of the `application.yml` files already use it.
- **Needs a DB?** If it persists relational data, it gets a Postgres DB + Flyway migration.
- **Inbound REST path?** If it serves `/api/<x>/**`, it needs an nginx route (and optionally a gateway route).
- **Kafka?** Producer/consumer if it participates in the event flow.

## Steps

### 1. Gradle module
- Create `backend/<service>/build.gradle`. Start from a sibling that matches the needs:
  - DB-backed -> copy `order-service/build.gradle` (web, data-jpa, validation, actuator,
    kafka, flyway, postgresql, micrometer-prometheus, test + testcontainers).
  - No DB -> copy `notification-service/build.gradle`.
  - Always: `implementation project(':common-platform')`.
- Add the module to `backend/settings.gradle`: `include '<service>'`.

### 2. Package layout
Create `backend/<service>/src/main/java/com/ticketbooking/<short>/` with
`<Service>Application.java` (`@SpringBootApplication`, `main` calls `SpringApplication.run`).
Add subpackages as needed, per `backend/CLAUDE.md`:
`config/ controller/ service/ repository/ entity/ dto/ messaging/ exception/ mapper/`.
Conventions: entities extend `AuditableEntity` with UUID PKs; DTOs are `record`s; throw the
shared exceptions; `@Transactional` on writes.

### 3. application.yml
Create `backend/<service>/src/main/resources/application.yml`. Use env vars with local
defaults (so it runs both from the IDE and in k8s). DB-backed template:

```yaml
spring:
  application:
    name: <service>
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/<short>db}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  tracing:
    sampling:
      probability: 1.0
server:
  port: <PORT>
```
Drop the `datasource`/`flyway`/`jpa` blocks for a stateless service; keep `kafka` only if used.

### 4. Database (DB-backed services only)
- Add a Flyway migration `backend/<service>/src/main/resources/db/migration/V1__create_<x>.sql`
  whose schema matches the JPA entities (because `ddl-auto: validate` is on — no migration ==
  startup crash).
- Add the database to the Postgres init script in `infrastructure/k8s/10-postgres.yaml`
  (the `postgres-init` ConfigMap `init.sql`): `CREATE DATABASE <short>db;`.

### 5. Kubernetes manifest
Create `infrastructure/k8s/<NN>-<service>.yaml` (next free `NN` in 20–29; if 20–29 are taken,
continue at 2A-style numbering or renumber). Copy an existing per-service file and change
name/port/DB_URL. Template:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: <service>
  namespace: ticketing
  labels:
    app: <service>
spec:
  replicas: 1
  selector:
    matchLabels:
      app: <service>
  template:
    metadata:
      labels:
        app: <service>
    spec:
      containers:
        - name: <service>
          image: ticketbooking/<service>:local
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: <PORT>
          envFrom:
            - configMapRef:
                name: ticket-config
            - secretRef:
                name: ticket-secret
          env:                                  # DB-backed only
            - name: DB_URL
              value: "jdbc:postgresql://postgres:5432/<short>db"
          resources:
            requests:
              cpu: "100m"
              memory: "384Mi"
            limits:
              memory: "768Mi"
          startupProbe:
            tcpSocket: { port: <PORT> }
            periodSeconds: 5
            failureThreshold: 60
          readinessProbe:
            tcpSocket: { port: <PORT> }
            periodSeconds: 10
          livenessProbe:
            tcpSocket: { port: <PORT> }
            periodSeconds: 20
---
apiVersion: v1
kind: Service
metadata:
  name: <service>
  namespace: ticketing
spec:
  selector:
    app: <service>
  ports:
    - port: <PORT>
      targetPort: <PORT>
```
`deploy.sh` applies these via the `2[0-9]-*.yaml` glob, so no script change is needed if the
file is numbered 20–29.

### 6. Routing (only if it exposes /api/<x>/**)
- **nginx** (`infrastructure/k8s/30-nginx.yaml`): add an `upstream <x>_svc { server <service>.ticketing.svc.cluster.local:<PORT>; }`
  and a `location /api/<x>/ { proxy_pass http://<x>_svc; }`. Add WebSocket upgrade headers if it uses STOMP.
- **gateway** (optional): add a `<X>_SERVICE_URL` env var in `infrastructure/k8s/29-gateway.yaml`
  and a matching route in `backend/gateway/src/main/resources/application.yml`.

### 7. Build, deploy, verify
```bash
cd infrastructure
./build-images.sh <service>                 # compile (cached) + package just this image
kubectl apply -f k8s/<NN>-<service>.yaml     # or re-run ./deploy.sh
kubectl -n ticketing rollout status deploy/<service>
curl -i http://localhost/api/<x>             # if it has a route
```

### 8. Update memory
Append a one-line entry to the Progress log in `infrastructure/CLAUDE.md` noting the new service,
its port, and DB.

## Checklist
- [ ] `settings.gradle` includes the module
- [ ] `build.gradle` deps match what the service uses
- [ ] `application.yml` port is unique; env defaults present
- [ ] DB created in `10-postgres.yaml` + Flyway `V1` migration matches entities (if DB-backed)
- [ ] k8s manifest numbered 20–29, name/port/DB_URL correct
- [ ] nginx (+ gateway) route added if it serves /api/**
- [ ] image builds and the pod reaches Ready
- [ ] Progress log updated
