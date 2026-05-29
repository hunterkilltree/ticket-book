#!/usr/bin/env bash
# Remove the entire local stack.
set -euo pipefail
kubectl delete namespace ticketing --ignore-not-found
echo "==> ticketing namespace deleted."
