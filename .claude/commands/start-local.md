---
description: Build all service images and deploy the full stack to local Kubernetes
---
Bring the Concert Ticket Booking System up locally on Docker Desktop Kubernetes.

Steps:
1. `cd infrastructure && ./build-images.sh`. This compiles all services in one Gradle
   pass (`--continue` reports every compile error at once) and packages 10 runtime images.
   If compilation fails, fix ALL reported errors (see `infrastructure/CLAUDE.md` → Known issues
   for the common ones), then re-run. Do not proceed until the build succeeds.
2. `./deploy.sh`. This applies the namespace, backing infra (Postgres/Redis/Kafka/
   Elasticsearch/MailHog), then the services and the nginx load balancer.
3. Watch `kubectl -n ticketing get pods` until everything (especially `nginx-lb`) is
   `Running` / `READY 1/1`. Services may restart a few times while infra warms up.
4. Verify: `curl http://localhost/healthz` should return `ok`; then
   `curl -i http://localhost/api/events` should return any HTTP response (not a connection refused).
5. If a pod crash-loops, run `kubectl -n ticketing logs deploy/<svc>` and consult
   `infrastructure/CLAUDE.md`. The likely culprit is `admin-service` (missing Flyway migration).

Report the final pod status and curl results.
