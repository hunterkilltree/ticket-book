# Business Requirements — Concert Ticket Booking System

> Status: derived from the current system design and implementation (backend services,
> domain entities, event flow, and the README). Intended as a working baseline for the
> team; refine with product/business stakeholders.

## 1. Purpose & scope

The Concert Ticket Booking System is an event-driven platform that lets customers browse
concerts, reserve and purchase seats in real time, and receive digital tickets, while
giving event organizers tools to manage events and administrators oversight of users,
sales, and system health.

This document captures the **business and functional requirements** of that platform. It
covers what the system must do and the rules it must enforce; it does not prescribe
implementation. Technical design lives in the root `CLAUDE.md`, `backend/CLAUDE.md`, and
`infrastructure/CLAUDE.md`.

## 2. Business objectives

The platform exists to sell concert tickets reliably under contention. Its success rests on
four objectives: never sell the same seat twice, keep seat selection fast and live so
customers trust availability, complete payment and ticket issuance dependably end to end,
and stay available and fair during high-demand "flash sale" on-sales.

## 3. Stakeholders & actors

| Actor | Description | Primary goals |
|---|---|---|
| Customer | End user buying tickets | Browse concerts, pick seats, pay, receive tickets, view history |
| Event organizer | Creates and runs events | Manage events, venues, seating, and pricing tiers |
| Administrator | Operates the platform | Manage users, view sales reports, monitor system health |
| Payment provider | External gateway | Authorize and capture payments (integrated, not owned) |

## 4. Functional requirements

Requirements are grouped by capability and tagged `FR-<area>-<n>`. Each maps to one or more
backend services (in parentheses).

### 4.1 User management & authentication (user-service)

- **FR-USR-1** The system shall allow a customer to register an account.
- **FR-USR-2** The system shall authenticate users and issue a JWT used to authorize
  requests across services (validated at the gateway and per service).
- **FR-USR-3** The system shall support distinct roles — at minimum customer, organizer,
  and administrator — and authorize actions by role.
- **FR-USR-4** The system shall let a customer view their booking/purchase history.

### 4.2 Event & venue management (event-service)

- **FR-EVT-1** The system shall allow an organizer to create and update events, including
  the associated venue and pricing tiers.
- **FR-EVT-2** The system shall represent an event lifecycle via an event status (e.g.
  draft → on sale → closed/cancelled) and expose only appropriate events to customers.
- **FR-EVT-3** The system shall model venues and their seating so that seats can be offered
  for an event.

### 4.3 Search & discovery (search-service)

- **FR-SRCH-1** The system shall provide a searchable event catalog.
- **FR-SRCH-2** The system shall let customers filter/search events by attributes such as
  location, date, and artist.
- **FR-SRCH-3** Search shall be backed by a dedicated index (Elasticsearch) kept in sync
  with event data via events, independent of the transactional databases.

### 4.4 Seat selection & reservation (booking-service)

- **FR-SEAT-1** The system shall present a real-time seat map showing live availability,
  pushed to clients over WebSocket (STOMP) as seat states change.
- **FR-SEAT-2** A seat shall have a defined lifecycle: `AVAILABLE → RESERVED → SOLD`, and
  may return to `AVAILABLE` on reservation expiry or payment failure.
- **FR-SEAT-3** The system shall let a customer reserve one or more seats, placing a
  temporary hold for a bounded window (configured 10 minutes; product range 5–10 min).
- **FR-SEAT-4** The system shall guarantee that, under concurrency, at most one customer can
  reserve a given seat (no double booking).
- **FR-SEAT-5** The system shall automatically release reserved seats whose hold has expired
  without completed payment, returning them to `AVAILABLE`.
- **FR-SEAT-6** The system shall transition a seat to `SOLD` only upon a confirmed payment
  event, and release its hold at that point.

### 4.5 Ordering (order-service)

- **FR-ORD-1** The system shall create an order capturing the selected seats and price for a
  customer's checkout.
- **FR-ORD-2** Order creation shall be idempotent: a client-supplied idempotency key shall
  prevent duplicate orders from retries.
- **FR-ORD-3** The order shall track its status through the checkout lifecycle.

### 4.6 Payment (payment-service)

- **FR-PAY-1** The system shall process payment for an order via an external payment gateway.
- **FR-PAY-2** Payment shall be idempotent via an idempotency key to prevent double charges.
- **FR-PAY-3** Payment must complete within the seat reservation window; otherwise it is
  treated as failed/timed-out.
- **FR-PAY-4** On success the system shall emit a payment-completed event; on
  failure/timeout it shall trigger release of the reserved seats.

### 4.7 Ticketing (ticket-service)

- **FR-TKT-1** Upon successful payment the system shall issue a digital ticket for each
  purchased seat.
- **FR-TKT-2** Each ticket shall carry a scannable QR/barcode and track its status.
- **FR-TKT-3** Tickets shall be downloadable and deliverable by email.

### 4.8 Notifications (notification-service)

- **FR-NTF-1** The system shall send transactional notifications (e.g. booking confirmation,
  ticket delivery) by consuming domain events asynchronously.
- **FR-NTF-2** Notifications shall not block the purchase path (decoupled via messaging).

### 4.9 Administration (admin-service)

- **FR-ADM-1** The system shall let administrators manage user accounts.
- **FR-ADM-2** The system shall provide sales reporting to administrators.
- **FR-ADM-3** The system shall expose operational/system-health information to
  administrators.

### 4.10 High-demand / flash sale handling

- **FR-FLASH-1** The system shall absorb on-sale demand spikes by queuing requests through
  the messaging layer (Kafka).
- **FR-FLASH-2** The system shall apply rate limiting per user/IP during flash sales.
- **FR-FLASH-3** The system shall provide anti-bot protection (e.g. CAPTCHA) during flash
  sales.

## 5. Business rules

| ID | Rule | Detail |
|---|---|---|
| BR-1 | No double booking | A distributed per-seat lock ensures only one reservation wins under concurrency. |
| BR-2 | Reservation expiry | Seats are auto-released after the hold window (5–10 min) if payment is not completed. |
| BR-3 | Payment window | Payment must complete within the active reservation window. |
| BR-4 | Seat confirmation | A seat becomes `SOLD` only after a successful payment event. |
| BR-5 | Payment failure handling | Failed or timed-out payments automatically release the held seats. |
| BR-6 | Flash sale fairness | Requests are queued via messaging; rate limiting per user/IP; CAPTCHA for anti-bot. |
| BR-7 | Idempotency | Order and payment operations accept idempotency keys to prevent duplicates/double charges. |

## 6. Non-functional requirements

| ID | Requirement | Target |
|---|---|---|
| NFR-1 | Seat selection latency | < 200 ms |
| NFR-2 | System uptime | ≥ 99.9% |
| NFR-3 | Scalability | Horizontally scalable; messaging consumers scale with load |
| NFR-4 | Consistency | Strong consistency for seat allocation (no double booking) |
| NFR-5 | Reliability | Retry mechanisms for payment and messaging failures |
| NFR-6 | Availability | Graceful degradation under load |
| NFR-7 | Observability | Metrics, distributed tracing, correlation IDs, and centralized structured logging |
| NFR-8 | Security | JWT-based authentication and role-based authorization across services |

## 7. Key workflows

### 7.1 Ticket purchase (happy path and failure)

```
Customer selects a concert
  → selects seats
    → system places a temporary hold on the seats (5–10 min)
      → customer completes payment
        ├── success  → booking confirmed → ticket issued → customer notified
        └── failure / timeout → seats released → seats become available again
```

### 7.2 Seat state machine

```
AVAILABLE --reserve--> RESERVED --payment success--> SOLD
   ^                       |
   └------ expiry / payment failure ------┘
```

### 7.3 Domain event flow

The purchase is coordinated asynchronously across services through ordered domain events:

```
seat_reserved  →  payment_completed  →  ticket_issued
```

Downstream services (ticketing, notifications, search) react to these events rather than
calling each other synchronously, which keeps the purchase path resilient and scalable.

## 8. Key domain entities

| Entity | Owning service | Notes |
|---|---|---|
| User (with role) | user-service | Customer / organizer / administrator |
| Event, Venue | event-service | Event has a status; venue defines seating |
| Seat (with state) | booking-service | `AVAILABLE` / `RESERVED` / `SOLD`; held via distributed lock |
| Order | order-service | Carries an idempotency key |
| Payment | payment-service | Carries an idempotency key; integrates external gateway |
| Ticket | ticket-service | QR/barcode; downloadable and emailable |

## 9. Assumptions & out of scope

These reflect the current design; confirm with stakeholders.

- Each service owns its own data store; cross-service consistency is achieved through
  events, not distributed transactions.
- The payment provider is an external, integrated system; the platform does not implement
  card processing itself.
- Seat-map and availability updates are best-effort real-time via WebSocket; the
  authoritative allocation is enforced by the per-seat lock, not the pushed view.
- Pricing/discount engines, refunds/cancellations, secondary-market resale, and multi-event
  cart checkout are not described by the current implementation and are treated as out of
  scope until specified.

## 10. Glossary

- **Seat hold / reservation**: a temporary, time-bounded lock on a seat during checkout.
- **Reservation window**: the time (5–10 min) within which payment must complete.
- **Idempotency key**: a client-supplied token making a retried request safe to repeat.
- **Domain event**: an asynchronous message (e.g. `payment_completed`) other services react to.
- **Flash sale**: a high-demand on-sale where many users compete for limited seats at once.
