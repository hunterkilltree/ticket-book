#!/usr/bin/env bash
# Start the whole project as Docker containers (Docker only — no Kubernetes).
#
#   ./start.sh
#
# It (1) builds all service images + the frontend (fast two-phase build, cached),
# then (2) runs the full stack with Docker Compose: Postgres, Redis, Kafka,
# Elasticsearch, MailHog, the 10 services, the frontend SPA, and the nginx edge.
#
# When it's up:   App UI -> http://localhost      MailHog -> http://localhost:8025
# Watch logs:     docker compose logs -f
# Stop:           docker compose down        (add -v to also wipe the database volume)
#
# (The Kubernetes path still exists too: infrastructure/build-images.sh + infrastructure/deploy.sh)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> [1/2] Building images"
"$SCRIPT_DIR/infrastructure/build-images.sh"

echo
echo "==> [2/2] Starting containers (docker compose up -d)"
docker compose -f "$SCRIPT_DIR/docker-compose.yml" up -d

echo
echo "==> Up. Services start in dependency order; give them a minute."
echo "    App UI:     http://localhost"
echo "    API:        http://localhost/api/..."
echo "    MailHog UI:  http://localhost:8025"
echo "    Status:     docker compose ps        Logs: docker compose logs -f        Stop: docker compose down"
