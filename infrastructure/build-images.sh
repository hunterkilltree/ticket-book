#!/usr/bin/env bash
# Build local Docker images for the Spring Boot services.
#
# Two phases:
#   1) Builder image  — ONE Gradle pass compiles all services. Dependencies and
#      the Gradle distribution are cached (BuildKit), so re-runs are fast and a
#      single compile error no longer means re-downloading everything. Gradle's
#      --continue reports ALL module errors at once.
#   2) Runtime images — each just copies its jar from the builder (seconds each).
#
# Usage:
#   ./build-images.sh                 # build everything
#   ./build-images.sh booking-service # rebuild only one (or several) services'
#                                     # runtime image after a fix
#   SKIP_BUILDER=1 ./build-images.sh booking-service
#                                     # reuse existing builder image, just repackage
set -euo pipefail
export DOCKER_BUILDKIT=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Refuse to run unless the docker daemon is available.
"$SCRIPT_DIR/preflight.sh" build || exit 1
echo

REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"
BUILD_DF="$SCRIPT_DIR/docker/Dockerfile.build"
RUN_DF="$SCRIPT_DIR/docker/Dockerfile.runtime"
BUILDER_IMG="ticketbooking/builder:local"

ALL_SERVICES=(
  gateway user-service event-service search-service booking-service
  order-service payment-service ticket-service notification-service admin-service
)

# Optional positional args = subset of services to (re)package.
if [ "$#" -gt 0 ]; then SERVICES=("$@"); else SERVICES=("${ALL_SERVICES[@]}"); fi

# --- Phase 1: compile everything once -----------------------------------------
if [ "${SKIP_BUILDER:-0}" != "1" ]; then
  echo "==> [1/2] Compiling all services in one Gradle pass (cached deps)"
  if ! docker build -f "$BUILD_DF" -t "$BUILDER_IMG" "$BACKEND_DIR"; then
    echo
    echo "Compilation failed. Fix the error(s) above (Gradle --continue lists them all),"
    echo "then re-run ./build-images.sh — cached dependencies make the retry fast."
    exit 1
  fi
else
  echo "==> [1/2] Skipping builder (SKIP_BUILDER=1); reusing $BUILDER_IMG"
fi

# --- Phase 2: package per-service runtime images ------------------------------
echo "==> [2/2] Packaging runtime images"
FAILED=()
for svc in "${SERVICES[@]}"; do
  echo "  -> ticketbooking/${svc}:local"
  if ! docker build -f "$RUN_DF" \
        --build-arg BUILDER="$BUILDER_IMG" \
        --build-arg SERVICE="$svc" \
        -t "ticketbooking/${svc}:local" \
        "$SCRIPT_DIR/docker"; then
    FAILED+=("$svc")
  fi
done

echo
if [ "${#FAILED[@]}" -eq 0 ]; then
  echo "==> All images built:"
  docker images "ticketbooking/*" --format '  {{.Repository}}:{{.Tag}}  ({{.Size}})'
else
  echo "==> Some runtime images failed to package: ${FAILED[*]}"
  echo "    Re-run just those:  ./build-images.sh ${FAILED[*]}"
  exit 1
fi
