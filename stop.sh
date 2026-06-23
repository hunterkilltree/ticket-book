#!/usr/bin/env bash
# Stop the Docker Compose stack and free resources.
#
#   ./stop.sh          stop & remove the containers + network (KEEPS the database volume)
#   ./stop.sh --all    also delete the database volume (wipes data) and the built images,
#                      then hint at reclaiming build cache — frees the most disk.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ "${1:-}" = "--all" ]; then
  echo "==> Stopping containers and removing volumes (data will be wiped)..."
  docker compose down -v --remove-orphans

  echo "==> Removing built images (ticketbooking/*)..."
  docker images 'ticketbooking/*' -q | sort -u | xargs -r docker rmi -f || true

  echo "==> To reclaim build cache too, run:  docker builder prune -af"
else
  echo "==> Stopping containers (database volume kept; next start is fast)..."
  docker compose down --remove-orphans
fi

echo "Done."
