#!/usr/bin/env bash
# Deploy the full local stack to the current kubectl context (built for Docker Desktop k8s).
# Prereq: run ./build-images.sh first so ticketbooking/*:local images exist locally.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="$SCRIPT_DIR/k8s"
NS=ticketing

# Refuse to run unless docker, kubectl and a reachable cluster are all present.
"$SCRIPT_DIR/preflight.sh" deploy || exit 1
echo

echo "==> Namespace, config & secrets"
kubectl apply -f "$K8S_DIR/00-namespace-config.yaml"

echo "==> Backing infrastructure (postgres, redis, kafka, elasticsearch, mailhog)"
kubectl apply -f "$K8S_DIR/10-postgres.yaml"
kubectl apply -f "$K8S_DIR/11-redis.yaml"
kubectl apply -f "$K8S_DIR/12-kafka.yaml"
kubectl apply -f "$K8S_DIR/13-elasticsearch.yaml"
kubectl apply -f "$K8S_DIR/14-mailhog.yaml"

echo "==> Waiting for infrastructure to become ready (this can take a couple of minutes)"
kubectl -n "$NS" rollout status deploy/postgres      --timeout=180s
kubectl -n "$NS" rollout status deploy/redis         --timeout=120s
kubectl -n "$NS" rollout status deploy/kafka         --timeout=180s
kubectl -n "$NS" rollout status deploy/elasticsearch --timeout=240s
kubectl -n "$NS" rollout status deploy/mailhog       --timeout=120s

echo "==> Application services"
for f in "$K8S_DIR"/2[0-9]-*.yaml; do
  kubectl apply -f "$f"
done

echo "==> nginx load balancer"
kubectl apply -f "$K8S_DIR/30-nginx.yaml"

echo "==> Done. Watch pods come up with:"
echo "    kubectl -n $NS get pods -w"
echo
echo "Once nginx-lb is Ready, the API is at:  http://localhost/api/..."
echo "MailHog UI:  kubectl -n $NS port-forward svc/mailhog 8025:8025  ->  http://localhost:8025"
