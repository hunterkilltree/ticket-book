# Infrastructure — local Kubernetes (agent instructions)

How to run the Concert Ticket Booking System locally on Kubernetes. Read together with
the root `CLAUDE.md` (overview) and `backend/CLAUDE.md` (code conventions).

## What exists

```
infrastructure/
├── preflight.sh           # checks docker/kubectl/cluster; refuses to run if unmet
├── build-images.sh        # builds all service images (see "Build model")
├── deploy.sh              # applies manifests in order, waits for infra to be ready
├── teardown.sh            # kubectl delete namespace ticketing
├── docker/
│   ├── Dockerfile.build   # ONE Gradle pass -> compiles every service's bootJar
│   └── Dockerfile.runtime # per-service runtime image (copies jar from builder)
└── k8s/                   # plain YAML, applied in numeric order
    ├── 00-namespace-config.yaml  # ns + shared ConfigMap + Secret
    ├── 10-postgres.yaml          # 1 Postgres, init.sql creates 7 DBs
    ├── 11-redis / 12-kafka (KRaft) / 13-elasticsearch / 14-mailhog
    ├── 20..29-<service>.yaml      # one file per Spring service (Deployment + Service)
    └── 30-nginx.yaml             # nginx LB (type: LoadBalancer) routes /api/* to services
```

Reusable workflows are slash commands in `.claude/commands/`: `/start-local`,
`/rebuild-service`, `/stop-local`, `/troubleshoot-local`.

## Environment / decisions (do not change without asking)

- Target cluster: **Docker Desktop Kubernetes** (context `docker-desktop`). Images
  build into the shared Docker daemon; manifests use `imagePullPolicy: IfNotPresent`
  (no registry).
- Load balancer: **nginx routes `/api/*` directly to each service**, bypassing the
  Spring gateway. nginx is `Service type: LoadBalancer` at **http://localhost**.
  The Spring gateway is still deployed but optional.
- Infra runs **in-cluster** (Postgres, Redis, Kafka, Elasticsearch, MailHog).
  Storage is `emptyDir` — data is ephemeral; Flyway recreates schema on restart.
- Namespace: `ticketing`. Service env comes from the `ticket-config` ConfigMap +
  `ticket-secret` Secret, with a per-service `DB_URL`.

## Build model (important)

`build-images.sh` is two-phase to avoid re-downloading and re-compiling per service:
1. **Builder image** (`Dockerfile.build`): single `./gradlew bootJar -x test --continue`
   pass. `--continue` surfaces **all** module compile errors at once. A BuildKit
   cache mount (`/root/.gradle`) persists the Gradle dist + deps across runs.
2. **Runtime images** (`Dockerfile.runtime`): copy each jar out of the builder — seconds each.

Usage:
- `./build-images.sh` — everything
- `./build-images.sh <svc> [<svc>...]` — repackage specific services after a fix
- `SKIP_BUILDER=1 ./build-images.sh <svc>` — reuse last compile, repackage only

## Standard workflow

```bash
cd infrastructure
./build-images.sh        # compile + package all 10 images
./deploy.sh              # namespace, infra, services, nginx
kubectl -n ticketing get pods -w
```
Test once `nginx-lb` is Ready: `curl http://localhost/healthz` -> `ok`,
then `curl -i http://localhost/api/events`.

Stop: `./teardown.sh` (delete) or `kubectl -n ticketing scale deploy --all --replicas=0` (pause).
Note: `VmmemWSL` memory is the WSL2/Docker Desktop VM — it persists after teardown;
quit Docker Desktop (and `wsl --shutdown`) to release it.

## Tech baseline

- Java 21 toolchain, Spring Boot 4.0.6, Gradle 9.4.x (needs JDK 17+ to run).
- Spring Cloud train: **2025.1.x (Oakwood)** — aligned with Boot 4.0.x.

## Known issues & fixes

Fixes already applied (keep them):
- **Spring Cloud BOM missing** — root `backend/build.gradle` imports
  `org.springframework.cloud:spring-cloud-dependencies:2025.1.1` (gateway starter had no version).
- **Gateway starter renamed** — `gateway/build.gradle` uses
  `spring-cloud-starter-gateway-server-webmvc` (was `...-gateway-mvc`, removed in
  Gateway 5.0) and `spring-boot-starter-web` instead of `webflux`.
- **Ambiguous `convertAndSend`** — `SeatStatusBroadcaster` casts the Map payload to
  `(Object)` so the compiler picks `convertAndSend(destination, payload)`.

Still open / watch for:
- **admin-service** has Flyway + JPA `ddl-auto: validate` but **no migration**.
  If it has entities it will crash-loop on startup — add a `V1__*.sql` matching its
  entities, or relax `ddl-auto`.
- **search-service** has no migration (expected — Elasticsearch-backed, no DB).
- **Gateway route config**: `application.yml` uses the old `spring.cloud.gateway.routes`
  namespace; Gateway 5.0 server-webmvc expects `spring.cloud.gateway.server.webmvc.*`.
  Not blocking (nginx is the real entry point) — only fix if the Spring gateway must work.
- New compile errors from other modules surface in the builder pass with `--continue` —
  fix them all in one round, then `./build-images.sh`.

## When asked to "run it"

1. `./build-images.sh`; if compile fails, fix every error reported by `--continue`, repeat.
2. `./deploy.sh`; watch `kubectl -n ticketing get pods`.
3. If a pod crash-loops, `kubectl -n ticketing logs deploy/<svc>` — check "Known issues" first.
4. Verify with the health curls. Report the result.

## Progress log (what's already been done)

Newest first. Keep appending here so the next session knows the current state.

- **Manifests split per service.** `k8s/20-services.yaml` was broken into one file per
  service: `20-user-service.yaml` … `28-admin-service.yaml`, `29-gateway.yaml` (each holds
  that service's Deployment + Service). `deploy.sh` now applies them via the
  `2[0-9]-*.yaml` glob.
- **Project cleanup.** Deleted the dead `backend/src/` IntelliJ skeleton
  (`org.example.backend` + its tests) and the empty `backend/compose.yaml`. Left
  `docs/backend-development.md` (outdated, references a non-existent docker-compose) and
  `.idea/` in place — candidates if further cleanup is wanted.
- **Memory restructured.** Root `CLAUDE.md` trimmed to a lean overview; backend detail
  moved to `backend/CLAUDE.md`; local-k8s detail lives here in `infrastructure/CLAUDE.md`
  (the old non-standard `.claude/CLAUDE.md` was removed). Slash commands in
  `.claude/commands/` reference this file.
- **Build sped up / made resilient.** Two-phase build: `Dockerfile.build` compiles all
  services in one Gradle pass with `--continue` (reports all compile errors at once) and a
  BuildKit cache mount; `Dockerfile.runtime` packages each jar in seconds. `build-images.sh`
  supports single-service rebuilds and `SKIP_BUILDER=1`.
- **Source bugs fixed** (see Known issues): Spring Cloud BOM added, gateway starter
  renamed to `...-gateway-server-webmvc`, `SeatStatusBroadcaster` payload cast to `(Object)`.
- **Initial setup created.** `infrastructure/` with preflight/build/deploy/teardown scripts
  and `k8s/` manifests (namespace+config, Postgres/Redis/Kafka/Elasticsearch/MailHog,
  services, nginx LoadBalancer). Targets Docker Desktop Kubernetes.

### Not done yet / next candidates
- Add the missing `admin-service` Flyway migration (it may crash-loop without it).
- Update gateway route YAML for Spring Cloud Gateway 5.0 if the Spring gateway must work.
- Optionally clean up `README.md` / `docs/backend-development.md` (stale references).
