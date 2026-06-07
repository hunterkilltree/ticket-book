---
name: add-k8s-service-manifest
description: Create the Kubernetes Deployment + Service manifest for a backend service in this ticket-booking project. Use when asked to add a k8s manifest, deploy a service to the cluster, or create the deployment YAML for a service. Produces a numbered per-service file under infrastructure/k8s/ matching the existing convention, which deploy.sh applies automatically.
---

# Add a Kubernetes service manifest

One file per service under `infrastructure/k8s/`, numbered 20–29. `deploy.sh` applies them
via the `2[0-9]-*.yaml` glob, so a correctly numbered file needs no script change.

## Steps

1. Pick the next free number `NN` in 20–29 (e.g. copy `infrastructure/k8s/24-order-service.yaml`).
   If 20–29 are exhausted, renumber or extend the glob in `deploy.sh`.
2. Create `infrastructure/k8s/<NN>-<service>.yaml` with a Deployment + Service:

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
          env:                                 # DB-backed only
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

Notes:
- Probes are `tcpSocket` (not HTTP health) so a service is not marked unready just because a
  dependency is briefly down during bring-up.
- Shared env (KAFKA_BROKERS, REDIS_HOST, ELASTICSEARCH_URI, MAIL_HOST/PORT, DB_USER) comes from
  the `ticket-config` ConfigMap; `DB_PASSWORD` from `ticket-secret`. Only `DB_URL` is per-service.
3. Apply: `kubectl apply -f infrastructure/k8s/<NN>-<service>.yaml` (or `./deploy.sh`),
   then `kubectl -n ticketing rollout status deploy/<service>`.
