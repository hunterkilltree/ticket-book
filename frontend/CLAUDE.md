# Frontend — agent instructions

Vite + React + TypeScript (strict) SPA for the ticket-booking platform. Read together with
the root `CLAUDE.md`. Current state: **foundation/scaffold only** — plumbing and an app
shell are in place; feature screens are stubs.

## Commands (run from `frontend/`)

```bash
npm install        # first time
npm run dev        # Vite dev server on http://localhost:5173 (proxies /api + /ws to the cluster)
npm run build      # type-check (tsc -b) + production build to dist/
npm run test       # Vitest
npm run lint       # ESLint
npm run format     # Prettier
```

## How it runs / integrates

- **Dev**: `npm run dev` serves on :5173 and proxies `/api` and `/ws` to `VITE_PROXY_TARGET`
  (default `http://localhost`, i.e. the cluster nginx LoadBalancer). Configure in `vite.config.ts`.
- **In-cluster**: `frontend/Dockerfile` builds the SPA and serves it with nginx. The image is
  `ticketbooking/frontend:local`; `infrastructure/k8s/40-frontend.yaml` deploys it; the cluster
  nginx (`30-nginx.yaml`) routes `/` to the frontend Service and keeps `/api/*` + `/ws` to the
  backend. Build with `infrastructure/build-images.sh frontend`; deploy with `deploy.sh`.
  App UI is then at **http://localhost**.

## Folder structure (feature-based)

```
src/
├── components/   # shared UI (Button, Input, Modal, Toast, Header, Footer)
├── features/     # auth (Zustand store), catalog, seat-picker, checkout, tickets, admin
├── pages/        # route-level composition
├── hooks/        # useDebounce, usePolling
├── services/     # apiClient (typed Axios), websocketClient (STOMP), errors, authToken
├── types/        # domain types (Event, Seat, Order, Ticket, User) + ProblemDetail/ApiResult
├── utils/        # currency, date, seatLayout
└── styles/       # tokens.css, globals.css, components.css
```

## Conventions (keep these)

- **TypeScript strict**; no `any`. `interface` for object/API shapes, `type` for unions.
- **No raw fetch/axios in components.** All calls go through `services/apiClient.ts`'s `http`
  helper, which returns a discriminated `ApiResult<T>` (`{ ok: true, data } | { ok: false, error }`)
  — never throws. Errors are normalized to RFC 7807 `ProblemDetail` in `services/errors.ts`.
- **Auth**: `features/auth/store.ts` (Zustand). The JWT lives in `services/authToken.ts`
  (in-memory + localStorage) so `apiClient` and the store don't import each other.
- **Live seat map**: `services/websocketClient.ts` connects STOMP to `/ws` and subscribes to
  `/topic/events/{eventId}/seats`. Use it via a `useSeatWebSocket` hook (to be added in seat-picker).
- **Styling**: plain CSS with design tokens in `styles/tokens.css`; component classes in
  `styles/components.css`. No UI framework.
- **Path alias**: `@/` -> `src/`.

## Backend contract assumptions (verify as services are built)

The scaffold assumes these (consistent with the gateway/nginx routes and business rules):
`POST /api/users/login -> { token, user }`, `GET /api/events`, `GET /api/search`,
`/api/bookings/*` (+ STOMP), `POST /api/orders` & `POST /api/payments` (idempotency-key header),
`GET /api/tickets`. Some backend services are still skeletons — confirm shapes before wiring
a feature; adjust the types in `src/types/` to match.

## Roadmap (build features vertically, one at a time)

1. **catalog** — list/search events (`GET /api/events`, `/api/search`), filters, `useDebounce`.
2. **auth** — login/register screens on the existing store.
3. **seat-picker** — SeatMap from `groupSeats`, `useSeatWebSocket`, reservation countdown hook.
4. **checkout** — order summary + idempotent payment (`useIdempotentCheckout`).
5. **tickets** — ticket cards with QR.
6. **admin** — event management form.
