#!/usr/bin/env bash
# Build local Docker images for the services and the frontend SPA.
#
# Java services (two-phase, cached):
#   1) Builder image — ONE Gradle pass compiles all services (--continue reports every
#      error at once); a BuildKit cache mount persists the Gradle dist + dependencies.
#   2) Runtime images — each copies its jar from the builder (seconds each).
# Frontend:
#   built from frontend/Dockerfile (Vite build -> nginx static image).
#
# Usage:
#   ./build-images.sh                  # build everything (all services + frontend)
#   ./build-images.sh booking-service  # rebuild specific service(s)
#   ./build-images.sh frontend         # rebuild only the frontend
#   SKIP_BUILDER=1 ./build-images.sh booking-service   # reuse last compile, repackage
set -euo pipefail
export DOCKER_BUILDKIT=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Refuse to run unless the docker daemon is available.
"$SCRIPT_DIR/preflight.sh" build || exit 1
echo

REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"
FRONTEND_DIR="$REPO_ROOT/frontend"
BUILD_DF="$SCRIPT_DIR/docker/Dockerfile.build"
RUN_DF="$SCRIPT_DIR/docker/Dockerfile.runtime"
BUILDER_IMG="ticketbooking/builder:local"

ALL_JAVA=(
  gateway user-service event-service search-service booking-service
  order-service payment-service ticket-service notification-service admin-service
)

# Resolve the requested set. Default = all Java services + frontend.
if [ "$#" -gt 0 ]; then REQUESTED=("$@"); else REQUESTED=("${ALL_JAVA[@]}" frontend); fi

# Split into Java services vs frontend.
JAVA_SVCS=(); WANT_FRONTEND=0
for s in "${REQUESTED[@]}"; do
  if [ "$s" = "frontend" ]; then WANT_FRONTEND=1; else JAVA_SVCS+=("$s"); fi
done

FAILED=()

# --- Java: Phase 1 builder ----------------------------------------------------
if [ "${#JAVA_SVCS[@]}" -gt 0 ]; then
  if [ "${SKIP_BUILDER:-0}" != "1" ]; then
    echo "==> [1/2] Compiling services in one Gradle pass (cached deps)"
    if ! docker build -f "$BUILD_DF" -t "$BUILDER_IMG" "$BACKEND_DIR"; then
      echo
      echo "Compilation failed. Fix the error(s) above (Gradle --continue lists them all),"
      echo "then re-run — cached dependencies make the retry fast."
      exit 1
    fi
  else
    echo "==> [1/2] Skipping builder (SKIP_BUILDER=1); reusing $BUILDER_IMG"
  fi

  # --- Java: Phase 2 runtime images ------------------------------------------
  echo "==> [2/2] Packaging service runtime images"
  for svc in "${JAVA_SVCS[@]}"; do
    echo "  -> ticketbooking/${svc}:local"
    if ! docker build -f "$RUN_DF" \
          --build-arg BUILDER="$BUILDER_IMG" \
          --build-arg SERVICE="$svc" \
          -t "ticketbooking/${svc}:local" \
          "$SCRIPT_DIR/docker"; then
      FAILED+=("$svc")
    fi
  done
fi

# --- Frontend image -----------------------------------------------------------
if [ "$WANT_FRONTEND" -eq 1 ]; then
  echo "==> Building ticketbooking/frontend:local (Vite build -> nginx)"
  if ! docker build -f "$FRONTEND_DIR/Dockerfile" -t "ticketbooking/frontend:local" "$FRONTEND_DIR"; then
    FAILED+=("frontend")
  fi
fi

echo
if [ "${#FAILED[@]}" -eq 0 ]; then
  echo "==> All images built:"
  docker images "ticketbooking/*" --format '  {{.Repository}}:{{.Tag}}  ({{.Size}})'
else
  echo "==> Failed: ${FAILED[*]}"
  echo "    Re-run just those:  ./build-images.sh ${FAILED[*]}"
  exit 1
fi
