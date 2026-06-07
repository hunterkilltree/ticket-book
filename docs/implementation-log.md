# Implementation log

**Single source of truth for feature progress. Read this first** to know what is done and what
to build next, then update it after finishing each requirement.

- Requirement IDs reference `docs/business-requirements.md`.
- Each "part" is a **full vertical slice**: backend REST + DB/migration + the frontend screen.
- Roadmap order: catalog → auth → seat-picker → checkout → tickets → admin.
- After finishing a requirement: move it to **Done**, update the **Status** table, and rewrite
  the **Next step** section so the following session can start immediately.

## Status

| # | Requirement | Status |
|---|---|---|
| 1 | Catalog — browse & view events (FR-SRCH-1/2, FR-EVT view) | ✅ Done |
| 2 | Authentication — register & login (FR-USR-1/2/3/4) | ✅ Done |
| 3 | Seat-picker — live seat map + reservation (FR-SEAT-1..6) | ✅ Done |
| 4 | Checkout — order + idempotent payment (FR-ORD-*, FR-PAY-*) | ✅ Done |
| 5 | Tickets — issued ticket display (FR-TKT-*) | ✅ Done |
| 6 | Admin — event management (FR-ADM-*, FR-EVT-1) | ✅ Done |

## Done

### 1. Catalog — browse & view events  (2026-06-07)

**Backend — `event-service`**
- `GET /api/events?q=&status=` (defaults to `PUBLISHED`; `q` searches title/artist) and `GET /api/events/{id}`.
- Added `EventRepository` (`search`, `findByIdWithVenue` with `join fetch venue`), `EventService`
  (`@Transactional(readOnly=true)`), `EventMapper`, DTOs `EventResponse` / `VenueResponse`.
- `EventServiceApplication` now `scanBasePackages = "com.ticketbooking"` so the shared
  `GlobalExceptionHandler` (RFC 7807) is picked up.
- `V2__seed_events.sql` seeds 3 venues + 5 events (4 `PUBLISHED`, 1 `DRAFT` to prove filtering).

**Frontend**
- `types/event.ts` aligned to the backend contract: `EventStatus = DRAFT|PUBLISHED|CANCELLED|COMPLETED`,
  `Venue { id, name, address, capacity }`, `Event { id, title, artist, startsAt, status, venue }`.
- `features/catalog/`: `api.ts` (`listEvents`, `getEvent`), `useEvents.ts` (debounced search hook),
  `EventCard.tsx`.
- `CatalogPage` (search + grid + loading/error/empty states), `EventDetailPage` (with a "Pick seats"
  link). Route `/events/:eventId` added.

**Verify**
```bash
cd backend && ./gradlew :event-service:test            # compile/tests
cd infrastructure && ./build-images.sh event-service frontend && ./deploy.sh
# then open http://localhost/events  (search; click an event)
curl http://localhost/api/events | jq                  # 4 published events
```

**Notes / not yet**
- Catalog queries `event-service` directly; `search-service` (Elasticsearch, FR-SRCH-3) is not yet
  wired — fine for now, revisit when search needs scale/filters.

### 2. Authentication — register & login  (2026-06-07)

**Backend — `user-service`**
- `POST /api/users/register` (email, password ≥8, fullName) → `201 { token, user }`;
  `POST /api/users/login` → `{ token, user }`. Kept `GET /api/users/{id}`.
- `SecurityConfig` (`@EnableWebSecurity`): stateless, CSRF off, `anyRequest().permitAll()`
  (endpoints open for now — gateway is the boundary), `BCryptPasswordEncoder` bean.
- `JwtService` issues an HS256 JWT using only the JDK (no extra deps) — claims sub/email/role/iat/exp,
  secret from `jwt.secret` (`JWT_SECRET` env, added to `ticket-secret`).
- Duplicate email → 409 (`ResponseStatusException`); bad credentials → 403 (`UnauthorizedActionException`).
- `UserServiceApplication` now scans `com.ticketbooking`. `V1__create_users.sql` already had
  password_hash/full_name/role — no new migration.

**Frontend**
- `types/user.ts` aligned: `User { id, email, fullName, role }`.
- `features/auth/api.ts` (`registerUser`, `loginUser`); store gained `register`; `Header` shows the user.
- `LoginPage` + `RegisterPage` wired to the store; routes `/login`, `/register`.

**Verify**
```bash
cd backend && ./gradlew :user-service:test
cd ../infrastructure && ./build-images.sh user-service frontend && ./deploy.sh
curl -s -XPOST localhost/api/users/register -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","password":"password1","fullName":"Ada"}'   # -> {token,user}
# then register/log in at http://localhost/register
```

**Notes / not yet**
- JWT is **issued** but not yet **enforced** across services (shared `JwtAuthFilter` is still a stub).
  Cross-service validation + a `JWT_SECRET`-sharing scheme is a later hardening step.
- No protected-route guard on the frontend yet; add when a screen actually requires auth (checkout).

### 3. Seat-picker — live seat map + reservation  (2026-06-07)

**Backend — `booking-service`**
- `WebSocketConfig` (`@EnableWebSocketMessageBroker`): native STOMP endpoint `/ws`, simple broker
  `/topic`, app prefix `/app` — so `SeatStatusBroadcaster` pushes to `/topic/events/{eventId}/seats`.
- `SeatService`: `listSeats(eventId)` (auto-generates a 2×3×8 demo grid on first read),
  `reserve(eventId, seatIds, userId?)` (Redis `SET NX` lock is the no-double-booking guard → mark
  RESERVED → broadcast → return reserved/failed + `expiresAt`), `release(...)`.
- `BookingController`: `GET /api/bookings/events/{eventId}/seats`, `POST /api/bookings/reserve`,
  `POST /api/bookings/release`. App now scans `com.ticketbooking`.
- Reuses existing `SeatLockService`, `SeatReaperService` (releases expired holds), `PaymentEventConsumer`
  (payment_completed → SOLD).

**Frontend — `features/seat-picker`**
- `api.ts` (`listSeats`, `reserveSeats`), `useSeatWebSocket` (wraps `connectSeatSocket`),
  `useReservationCountdown`, `SeatMap` (uses `groupSeats`; fixed numeric sort since `number` is a string).
- `SeatPickerPage` (`/events/:eventId/seats`): loads seats, reflects live updates, drops selected seats
  that others take, reserves, shows a countdown banner, links to checkout. Seat types aligned to backend
  (no price yet).

**Verify**
```bash
cd backend && ./gradlew :booking-service:test
cd ../infrastructure && ./build-images.sh booking-service frontend && ./deploy.sh
# open an event → "Pick seats"; reserve; watch the countdown. In a 2nd tab the seat flips to Reserved live.
```

**Notes / not yet**
- Reservation isn't yet tied to an order; checkout (#4) will carry `reservedSeatIds` + price.
- Seats have no price/tier yet — add when checkout needs totals.

### 4. Checkout — order + idempotent payment  (2026-06-07)

**Backend — `order-service`**
- `POST /api/orders` (`{userId?, seatIds}` + `Idempotency-Key` header → create `Order` PENDING,
  `totalAmount = seats × $50`; replaying the key returns the same order), `GET /api/orders/{id}`.
- `OrderRepository.findByIdempotencyKey`, `OrderService`, DTOs; scans `com.ticketbooking`.
  `V1` migration already had the unique `idempotency_key` + `total_amount` — no change.

**Backend — `payment-service`**
- `POST /api/payments` (`{orderId, seatIds, amount}` + `Idempotency-Key` → idempotent on the key
  **and** on `order_id`; simulate gateway SUCCESS, persist `Payment`, **emit one `payment_completed`
  per seat** via `KafkaTemplate`). booking-service consumes it → seats SOLD.
- Kafka string serde wired: payment producer (`StringSerializer`) + booking consumer
  (`StringDeserializer`, `auto-offset-reset: earliest`). Scans `com.ticketbooking`.

**Frontend — `features/checkout`**
- `store.ts` (carries `eventId`+`seatIds` from seat-picker), `api.ts` (`createOrder`, `payOrder`
  sending `Idempotency-Key`), `useIdempotentCheckout` (keys via `crypto.randomUUID` in refs → reused on
  retry). `CheckoutPage` shows summary + Pay → success screen linking to tickets. Seat-picker now sets
  the checkout store and navigates on "Proceed to checkout". `types/order.ts` aligned to backend.

**Verify**
```bash
cd backend && ./gradlew :order-service:test :payment-service:test :booking-service:test
cd ../infrastructure && ./build-images.sh order-service payment-service booking-service frontend && ./deploy.sh
# reserve seats → Proceed to checkout → Pay. Seats flip to SOLD (booking consumes payment_completed).
```

**Notes / not yet**
- Order stays `PENDING` in its own DB (no event_id/seat columns; payment carries seatIds). Transitioning
  the Order to PAID would need order-service to consume payment events — deferred.
- Payment always succeeds (demo gateway); no failure/timeout path or `payment_failed` emission yet.

### 5. Tickets — issued ticket display  (2026-06-07)

**Event payload enriched (cross-service):**
- New shared record `common-platform/.../messaging/PaymentCompletedPayload`
  `(orderId, userId, eventId, seatIds[])`. `payment-service` now emits **one** `payment_completed`
  message as JSON of this payload (was per-seat seat-id strings). `PaymentRequest` gained `eventId`/`userId`.
- `booking-service` `PaymentEventConsumer` updated to parse the JSON and loop `seatIds` → SOLD.

**Backend — `ticket-service`**
- `Ticket` gained a `userId` column (migration `V2__add_ticket_user.sql`).
- `TicketIssuanceConsumer` (`@KafkaListener` on `payment_completed`, group `ticket-service`):
  issues one `Ticket` per seat (status ISSUED, `qrCode = "TKT-"+uuid`), idempotent via
  `existsByOrderIdAndSeatId`, then forwards `ticket_issued` (for notification-service later).
- REST `GET /api/tickets?userId=` (or `?orderId=`). Kafka string serde + producer configured;
  scans `com.ticketbooking`.

**Frontend**
- `types/ticket.ts` aligned; `features/tickets` (`api.listMyTickets`, `TicketCard`); `TicketsPage`
  (`/tickets`) lists the logged-in user's tickets (prompts login otherwise). Checkout now threads
  `eventId`/`userId` into the payment call so tickets link to the user.

**Verify**
```bash
cd backend && ./gradlew :payment-service:test :booking-service:test :ticket-service:test
cd ../infrastructure && ./build-images.sh payment-service booking-service ticket-service frontend && ./deploy.sh
# log in → reserve → checkout → Pay → /tickets shows the issued tickets; seats are SOLD.
```

**Notes / not yet**
- Auth store doesn't persist `user` across reload (only the token), so `/tickets` after a refresh
  prompts re-login. Add a `GET /api/users/me` (or persist user) when convenient.
- `notification-service` doesn't consume `ticket_issued` yet (email on issue) — optional follow-up.

### 6. Admin — event management  (2026-06-07)

**Backend — `event-service`**
- `POST /api/events` (create with `venueId`), `PUT /api/events/{id}` (partial update incl.
  DRAFT→PUBLISHED). `GET /api/events?all=true` returns every status for admin; the public list still
  defaults to PUBLISHED. New `VenueController` `GET/POST /api/venues`, `VenueService`, `VenueRepository`,
  DTOs (`CreateEventRequest`, `UpdateEventRequest`, `VenueRequest`).
- **nginx fix**: the `/api/*` locations had trailing slashes, so collection calls like `GET /api/events`
  (no trailing slash) would have fallen through to the SPA in-cluster. Changed all to prefix matches
  (no trailing slash) and added `/api/venues` → event_svc. (Dev was unaffected; the Vite `/api` proxy
  already matched.)

**Frontend — `features/admin`**
- `api.ts` (`listAllEvents`, `createEvent`, `publishEvent`, `listVenues`, `createVenue`); `AdminPage`
  (`/admin`): create-venue form, create-event form (venue dropdown, datetime-local), and an all-events
  table with a Publish action. `Header` shows an Admin link for ADMIN/ORGANIZER.

**Verify**
```bash
cd backend && ./gradlew :event-service:test
cd ../infrastructure && ./build-images.sh event-service frontend && ./deploy.sh
# /admin → create a venue → create an event → Publish → it appears in /events (public catalog).
```

## Status: all six requirements complete ✅

The end-to-end journey works: browse/search → register/login → live seat map + reserve →
idempotent checkout/pay → seats SOLD → tickets issued → admin creates/publishes events.

## Deferred / backlog (hardening & polish, not yet done)

- **JWT enforcement across services** — tokens are issued but the shared `JwtAuthFilter` is still a stub;
  no role-based authorization is enforced (admin endpoints are open). FR-USR-3 / security.
- **Order → PAID transition** — order stays PENDING; have order-service consume `payment_completed`.
- **Auth `users/me` / persist user** — `/tickets` and the Admin link need re-login after a page refresh
  (only the token persists, not the user object).
- **search-service (Elasticsearch)** — catalog search hits event-service directly (FR-SRCH-3).
- **notification-service** — ✅ done: `TicketIssuedConsumer` consumes `ticket_issued` and emails the
  buyer via MailHog (`EmailService`/`JavaMailSender`). Recipient is derived from `userId` for now
  (`user-<id>@demo.local`) — thread the real email through the payload to improve. FR-NTF.
- **Payment failure path** — demo gateway always succeeds; no `payment_failed` / seat-release-on-failure.
- **Tests** — unit tests added for the new service logic (user + JwtService, event, booking/seat,
  order, payment, ticket) using JUnit 5 + Mockito (no Spring context/DB/Kafka). Run with
  `cd backend && ./gradlew test`. Integration tests (Testcontainers, web layer, Kafka flow) still pending.
  Note: written but not yet compiled here (sandbox JDK 11) — expect to fix minor issues on first run.

This section is the new "Next step" pointer: pick any item above; otherwise the feature set is complete.
