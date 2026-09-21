# ADR 001: Idempotent order creation via a unique key column

- Status: accepted
- Date: 2026-09-21
- Phase: 1 (walking skeleton)

## Context

`POST /orders` must be safe to retry. Clients, gateways and later message consumers
will resend requests after timeouts, so the same `Idempotency-Key` must always yield the
same order and must never create a second one, including when duplicates arrive at the
same moment.

## Decision

Store the key on the order itself and let PostgreSQL arbitrate:

- `orders.idempotency_key` has a `UNIQUE` constraint; `orders.request_hash` holds a SHA-256
  of the normalized payload (customer, currency, items in order, prices without trailing zeros).
- Creation runs in one transaction that starts with
  `INSERT ... ON CONFLICT (idempotency_key) DO NOTHING`.
  - 1 row inserted: this request owns the key. Insert the items, return **201 Created**.
  - 0 rows inserted: an order with this key already exists. Compare `request_hash`.
    Equal: return the stored order with **200 OK**. Different: **422** (problem+json), nothing changes.
- Every response, first or replay, is built from the persisted row, so replays are identical
  (no drift in decimal scale or timestamp precision; `created_at` is truncated to microseconds).
- A missing or blank `Idempotency-Key` is a **400**.

Under READ COMMITTED a concurrent duplicate blocks on the unique index until the first
transaction commits, then takes the "0 rows" path and reads the committed order. There is no
check-then-insert window and no application-level lock.

## Alternatives considered

- **Separate `idempotency_keys` table** (key, hash, order id, stored response). More general
  and reusable by other endpoints, but needs a second insert, an ordering rule against the
  order foreign key, and more moving parts. Rejected for now.
- **Check, then insert** in application code. Racy: two requests both see "absent". Rejected;
  the constraint would turn the race into 500s.
- **Distributed lock / Redis key.** A new component for a problem the database already solves.

## Consequences

- Correctness rests on the database constraint, and tests run against real PostgreSQL 16
  (Testcontainers), including a 20-thread same-key test.
- Idempotency is coupled to the orders table. Other write endpoints will need their own
  mechanism or a migration to the separate-table design.
- Keys are **globally unique**. Once authentication exists they must be scoped per caller
  (e.g. unique on `(tenant, key)`), otherwise one caller can collide with another.
- Keys never expire. A retention/cleanup policy is needed before this holds real traffic.
- The 422 rule treats item order as part of the payload.
- When the outbox arrives (later phase), the order row and its event must be written in this
  same transaction so a replay never emits a second event.
