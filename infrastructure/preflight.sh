#!/usr/bin/env bash
# Preflight check: verify every requirement for the local Kubernetes stack is met.
# Logs each check as [ OK ] / [FAIL] / [WARN]; exits non-zero (refusing to run) if
# any hard requirement is missing.
#
# Usage:
#   ./preflight.sh           # full check (build + deploy): docker, kubectl, cluster
#   ./preflight.sh build     # build-only check: docker daemon
#   ./preflight.sh deploy    # deploy-only check: docker, kubectl, cluster
set -uo pipefail

MODE="${1:-full}"          # full | build | deploy
MIN_MEM_GB=7              # recommended Docker memory; below this we WARN

# --- logging helpers ---------------------------------------------------------
if [ -t 1 ]; then G=$'\e[32m'; R=$'\e[31m'; Y=$'\e[33m'; B=$'\e[1m'; N=$'\e[0m'; else G=; R=; Y=; B=; N=; fi
FAILURES=()
ok()   { printf "  [ %sOK%s ] %s\n"   "$G" "$N" "$1"; }
fail() { printf "  [%sFAIL%s] %s\n"   "$R" "$N" "$1"; FAILURES+=("$1"); }
warn() { printf "  [%sWARN%s] %s\n"   "$Y" "$N" "$1"; }
have() { command -v "$1" >/dev/null 2>&1; }

need_docker=1
need_kubectl=1
need_cluster=1
case "$MODE" in
  build)  need_kubectl=0; need_cluster=0 ;;
  deploy) ;;
  full)   ;;
  *) echo "Unknown mode '$MODE' (use: full | build | deploy)"; exit 2 ;;
esac

echo "${B}==> Preflight ($MODE) — checking requirements${N}"

# --- bash --------------------------------------------------------------------
if [ -n "${BASH_VERSION:-}" ]; then ok "bash present (v${BASH_VERSION%%(*})"; else fail "bash is required (use Git Bash or WSL on Windows)"; fi

# --- docker CLI + daemon -----------------------------------------------------
if [ "$need_docker" -eq 1 ]; then
  if have docker; then
    ok "docker CLI found ($(docker --version 2>/dev/null | sed 's/,.*//'))"
    if docker info >/dev/null 2>&1; then
      ok "docker daemon is running"
      mem_bytes="$(docker info --format '{{.MemTotal}}' 2>/dev/null || echo 0)"
      if [ "${mem_bytes:-0}" -gt 0 ] 2>/dev/null; then
        mem_gb=$(( mem_bytes / 1024 / 1024 / 1024 ))
        if [ "$mem_gb" -ge "$MIN_MEM_GB" ]; then
          ok "docker memory ~${mem_gb}GB (>= ${MIN_MEM_GB}GB recommended)"
        else
          warn "docker memory ~${mem_gb}GB is below the recommended ${MIN_MEM_GB}GB — services may crash-loop (Settings → Resources → Memory)"
        fi
      fi
    else
      fail "docker daemon not reachable — start Docker Desktop"
    fi
  else
    fail "docker CLI not found — install Docker Desktop"
  fi
fi

# --- kubectl -----------------------------------------------------------------
if [ "$need_kubectl" -eq 1 ]; then
  if have kubectl; then
    ok "kubectl found ($(kubectl version --client -o yaml 2>/dev/null | grep -m1 gitVersion | awk '{print $2}'))"
  else
    fail "kubectl not found — install it (bundled with Docker Desktop)"
  fi
fi

# --- cluster reachable + node Ready -----------------------------------------
if [ "$need_cluster" -eq 1 ]; then
  if have kubectl; then
    ctx="$(kubectl config current-context 2>/dev/null || echo '')"
    if [ -n "$ctx" ]; then
      ok "kubectl context: $ctx"
      [ "$ctx" = "docker-desktop" ] || warn "context is '$ctx', not 'docker-desktop' (run: kubectl config use-context docker-desktop)"
    else
      fail "no kubectl context set"
    fi
    if kubectl cluster-info >/dev/null 2>&1; then
      ok "cluster API reachable"
      if kubectl get nodes 2>/dev/null | grep -qw Ready; then
        ok "at least one node is Ready"
      else
        fail "no Ready nodes — enable Kubernetes in Docker Desktop (Settings → Kubernetes)"
      fi
    else
      fail "cannot reach cluster — enable Kubernetes in Docker Desktop and wait until it is green"
    fi
  fi
fi

# --- summary -----------------------------------------------------------------
echo
if [ "${#FAILURES[@]}" -eq 0 ]; then
  echo "${B}${G}All requirements met.${N}"
  exit 0
else
  echo "${B}${R}Cannot run — ${#FAILURES[@]} requirement(s) not met:${N}"
  for f in "${FAILURES[@]}"; do echo "  ${R}- $f${N}"; done
  echo
  echo "Fix the items above and re-run. See README → Kubernetes (local) for details."
  exit 1
fi
