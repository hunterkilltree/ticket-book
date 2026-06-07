# Frontend — Getting Started

How to run the Concert Ticket Booking frontend (Vite + React + TypeScript). For conventions
and architecture see `frontend/CLAUDE.md`.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Node.js + npm | 20 LTS or newer | Install via [nvm](https://github.com/nvm-sh/nvm) or [nodejs.org](https://nodejs.org). Required for the dev server and build. |
| Backend stack | — | Optional but recommended. The UI shell runs without it, but any data (`/api/*`) needs the backend up. See `docs/backend-development.md` / `infrastructure/CLAUDE.md`. |
| Docker Desktop + Kubernetes | — | Only for the containerized path (Option B). Same setup as the backend. |

## Option A — Vite dev server (recommended for development)

Fast hot-reload loop. Run from `frontend/`:

```bash
cd frontend
npm install     # first time only
npm run dev
```

Open **http://localhost:5173**.

The dev server proxies `/api` and the `/ws` WebSocket to **http://localhost** (the cluster's
nginx LoadBalancer) — so start the backend stack to get live data:

```bash
cd infrastructure && ./build-images.sh && ./deploy.sh
```

If your backend is reachable somewhere else, point the proxy at it:

```bash
VITE_PROXY_TARGET=http://my-host:8080 npm run dev
```

Without a backend running, navigation and the UI shell still work; data calls just return
errors until the services are up (expected).

## Option B — Containerized in the cluster (production-like)

Builds the SPA into an image and serves it through the cluster nginx at the root path.

```bash
cd infrastructure
./build-images.sh frontend     # builds ticketbooking/frontend:local
./deploy.sh                    # deploys everything; nginx serves the SPA at /
```

Open **http://localhost** (the app UI; `/api/*` and `/ws` route to the backend automatically).

After a frontend change, rebuild and roll it:

```bash
cd infrastructure
./build-images.sh frontend
kubectl -n ticketing rollout restart deploy/frontend
```

## Commands (from `frontend/`)

```bash
npm run dev        # dev server on :5173
npm run build      # type-check (tsc -b) + production build to dist/
npm run preview    # serve the production build locally
npm run test       # Vitest
npm run lint       # ESLint
npm run format     # Prettier
```

## Configuration

Environment variables (in `frontend/.env`, override with `.env.local`):

- `VITE_API_BASE_URL` — base path for API calls. Empty = same origin (the proxy / nginx route it).
- `VITE_WS_URL` — WebSocket path for the live seat map (default `/ws`).
- `VITE_PROXY_TARGET` — (dev only, read by `vite.config.ts`) where to proxy `/api` and `/ws`.
  Default `http://localhost`.

## Project layout

Feature-based under `frontend/src/` (`components/ features/ pages/ hooks/ services/ types/
utils/ styles/`). Full description and coding conventions are in `frontend/CLAUDE.md`.

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `EADDRINUSE` on 5173 | Port busy — run `npm run dev -- --port 5174`. |
| `/api/...` returns 502 or network error | Backend not running — deploy the stack (`infrastructure/deploy.sh`) or check `kubectl -n ticketing get pods`. |
| Seat map not updating | WebSocket not connected — ensure `/ws` proxies through and `booking-service` is Ready. |
| `engine "node"` / syntax errors on install | Node too old — use Node 20+ (`nvm use 20`). |
| Blank page after deploy (Option B) | Image not rebuilt — `./build-images.sh frontend` then `kubectl -n ticketing rollout restart deploy/frontend`. |

## Current state

Foundation/scaffold only — plumbing and app shell exist; feature screens (catalog, seat-picker,
checkout, tickets, admin) are stubs. See the roadmap in `frontend/CLAUDE.md`.
