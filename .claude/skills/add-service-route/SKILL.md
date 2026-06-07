---
name: add-service-route
description: Expose a backend service through the nginx load balancer (and optionally the Spring gateway) in this ticket-booking project. Use when asked to add a route, expose a service, wire up an /api path, or make a service reachable at http://localhost. Edits the nginx ConfigMap and, optionally, the gateway.
---

# Add a service route

nginx is the entry point at `http://localhost` and routes `/api/*` directly to each service.
The Spring gateway is optional but can be kept in sync.

## nginx (required for any /api/<x>/** path)
Edit the `nginx-conf` ConfigMap in `infrastructure/k8s/30-nginx.yaml`:

1. Add an upstream (inside `http { ... }`):
   ```
   upstream <x>_svc { server <service>.ticketing.svc.cluster.local:<PORT>; }
   ```
2. Add a location (inside `server { ... }`):
   ```
   location /api/<x>/ { proxy_pass http://<x>_svc; }
   ```
3. If the service uses WebSocket/STOMP, add the upgrade headers (copy the `/api/bookings/`
   block as a model):
   ```
   proxy_set_header Upgrade    $http_upgrade;
   proxy_set_header Connection $connection_upgrade;
   ```
4. Apply and restart nginx so it re-reads the config:
   ```
   kubectl apply -f infrastructure/k8s/30-nginx.yaml
   kubectl -n ticketing rollout restart deploy/nginx-lb
   ```

Important: nginx resolves the upstream hostnames at startup, so the service's k8s **Service**
must already exist when nginx starts (deploy.sh applies services before nginx).

## Gateway (optional)
To also route through the Spring gateway:
- Add `<X>_SERVICE_URL: "http://<service>:<PORT>"` env to `infrastructure/k8s/29-gateway.yaml`.
- Add the route to `backend/gateway/src/main/resources/application.yml`.
- Caveat: this gateway is Spring Cloud Gateway 5.0 (server-webmvc); its route config namespace
  is `spring.cloud.gateway.server.webmvc.*`, not the legacy `spring.cloud.gateway.routes`.
  See `infrastructure/CLAUDE.md` Known issues.

## Verify
`curl -i http://localhost/api/<x>` — any HTTP response (even 404/401) means routing works.
