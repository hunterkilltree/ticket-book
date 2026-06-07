---
name: add-flyway-migration
description: Add a Flyway migration (and register the database) for a service in this ticket-booking backend. Use when asked to add a migration, create a table, add a database, or when a DB-backed service needs its schema. Follows the V{n}__description.sql convention and keeps the schema in sync with the JPA entities (ddl-auto is validate, so a missing/mismatched migration crashes startup). Also fixes admin-service, which currently lacks a migration.
---

# Add a Flyway migration

DB-backed services run with `spring.jpa.hibernate.ddl-auto: validate`, so Flyway must create
a schema that exactly matches the JPA entities — otherwise the app crash-loops on startup.

## Steps

1. **Locate the entities** in `backend/<service>/src/main/java/com/ticketbooking/<short>/entity/`.
   Note table names, columns, types, PKs (UUID), FKs, unique constraints
   (e.g. `idempotencyKey` in order/payment), and the inherited `created_at` / `updated_at`
   from `AuditableEntity`.

2. **Create the migration** at
   `backend/<service>/src/main/resources/db/migration/V{n}__<description>.sql`.
   - `{n}` is the next integer after existing migrations in that folder (start at `1`).
   - Use a sibling as a style reference: `event-service/.../V1__create_events.sql`,
     `order-service/.../V1__create_orders.sql`.
   - Include the audit columns: `created_at TIMESTAMP, updated_at TIMESTAMP`.
   - Match column names to the entity field mapping (snake_case unless overridden).

3. **Register the database** (only if this is the service's first migration / new DB):
   add `CREATE DATABASE <short>db;` to the `postgres-init` ConfigMap `init.sql` in
   `infrastructure/k8s/10-postgres.yaml`, and ensure the service's `DB_URL` points at
   `jdbc:postgresql://postgres:5432/<short>db`.
   Note: Postgres init only runs on a fresh volume — if the cluster already ran, recreate
   the Postgres pod (`kubectl -n ticketing delete pod -l app=postgres`) or create the DB manually.

4. **Verify**: `cd backend && ./gradlew :<service>:bootRun` (or redeploy) and confirm Flyway
   applies the migration and the app starts (`/actuator/health` UP).

## admin-service note
`admin-service` has Flyway + JPA validate enabled but no migration. If it has entities, use
this skill to add `V1__*.sql` matching them; if it has none, the missing migration is harmless.
