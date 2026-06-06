---
description: Diagnose a failing/crash-looping pod in the local ticketing stack
argument-hint: [service]
---
Diagnose problems in the local `ticketing` namespace. If a service name is given in
$ARGUMENTS, focus on it; otherwise survey all pods.

Steps:
1. `kubectl -n ticketing get pods` — identify pods that are not `Running`/`Ready`
   (CrashLoopBackOff, Pending, Error, ImagePullBackOff).
2. For a failing pod: `kubectl -n ticketing logs deploy/<svc> --tail=100` and
   `kubectl -n ticketing describe pod <pod>` (events at the bottom).
3. Match against `infrastructure/CLAUDE.md` → Known issues:
   - `admin-service` crash on startup -> likely missing Flyway migration (Flyway + JPA
     `validate` with no `V1__*.sql`). Offer to scaffold a migration or relax `ddl-auto`.
   - `ImagePullBackOff` -> the image was not built; run `./build-images.sh <svc>`
     (manifests use `imagePullPolicy: IfNotPresent`, so the image must exist locally).
   - A service stuck waiting on infra -> check `postgres`/`kafka`/`elasticsearch` pods are Ready.
   - Connection errors -> verify env in the `ticket-config` ConfigMap and the service's `DB_URL`.
4. Apply the fix, rebuild/redeploy as needed, and re-check pod status.

Report the root cause and what you changed.
