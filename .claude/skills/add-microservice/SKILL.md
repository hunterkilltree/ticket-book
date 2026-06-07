---
name: add-microservice
description: Scaffold a new Spring Boot service in this ticket-booking backend. Use when asked to "add a service", "create a new microservice", or "add a module". Creates the Gradle module, package layout, and application.yml, then delegates DB, Kubernetes, and routing to the focused skills add-flyway-migration, add-k8s-service-manifest, and add-service-route.
---

# Add a microservice (orchestrator)

Scaffolds the Spring Boot app itself, then chains the focused skills for the rest.
Read `backend/CLAUDE.md` (conventions) and `infrastructure/CLAUDE.md` (deploy) first.

## 1. Settle inputs
- **Service name**: kebab-case ending in `-service` (e.g. `review-service`).
- **Base package**: `com.ticketbooking.<short>` (e.g. `review`).
- **Port**: next free port. Used: 8080 gateway, 8081–8089. Pick the next unused
  (e.g. `8090`); confirm no `application.yml` already uses it.
- **DB-backed?  Inbound /api/** route?  Kafka?** — these decide which sub-skills run.

## 2. Gradle module
- Create `backend/<service>/build.gradle` from the closest sibling:
  DB-backed -> copy `order-service/build.gradle`; stateless -> copy `notification-service/build.gradle`.
  Always keep `implementation project(':common-platform')`; drop deps it doesn't use.
- Register it in `backend/settings.gradle`: `include '<service>'`.

## 3. Package + main class
Create `backend/<service>/src/main/java/com/ticketbooking/<short>/<Service>Application.java`
(`@SpringBootApplication`, standard `main`). Add subpackages as needed:
`config/ controller/ service/ repository/ entity/ dto/ messaging/ exception/ mapper/`.
Follow `backend/CLAUDE.md`: entities extend `AuditableEntity` (UUID PKs), DTOs are `record`s,
shared exceptions, `@Transactional` on writes.

## 4. application.yml
Create `backend/<service>/src/main/resources/application.yml` with env-var defaults so it
runs from the IDE and in k8s. Always include `server.port` and the `management` block
(`health,info,prometheus` + tracing). Add `spring.datasource`/`jpa`/`flyway` only if DB-backed,
`spring.kafka.bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}` only if it uses Kafka.

## 5. Delegate the rest
- **DB-backed** -> run the **add-flyway-migration** skill (creates the DB + V1 migration).
- Always -> run the **add-k8s-service-manifest** skill (Deployment + Service).
- **Serves /api/<x>/**** -> run the **add-service-route** skill (nginx + optional gateway).

## 6. Build, deploy, verify, record
```bash
cd infrastructure
./build-images.sh <service>
kubectl apply -f k8s/<NN>-<service>.yaml      # or ./deploy.sh
kubectl -n ticketing rollout status deploy/<service>
```
Append a one-line entry (service, port, DB) to the Progress log in `infrastructure/CLAUDE.md`.
