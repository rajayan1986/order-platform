# ADR-002: Use PostgreSQL as Primary Database

## Status

Accepted.

## Context

The system needs a primary store for:

- Orders, order items, and order status history.
- Transactional outbox events (Order Service).
- Idempotency keys (Order Service).
- Saga progress (Processing Service updates status and writes status history in the same DB).

Requirements: ACID transactions, strong consistency for order and outbox writes, and the ability to share schema across Order Service (reactive) and Processing Service (blocking JPA) for a single source of truth.

## Decision

We use **PostgreSQL** as the single relational database for the platform.

- **Schema:** One database (`orderdb`); shared tables: `orders`, `order_items`, `order_status_history`, `outbox_events`, `idempotency_keys`.
- **Order Service** uses **Spring Data R2DBC** (reactive driver) for non-blocking access and reactive transactions when writing orders, items, status history, outbox row, and idempotency key in one transaction.
- **Processing Service** uses **Spring Data JPA** (blocking) to update order status and append status history as part of saga steps, with `@Transactional` boundaries.

Same data, two access styles: reactive for the API layer and blocking for the saga worker, both talking to the same PostgreSQL instance.

## Consequences

**Positive:**

- Single source of truth; no cross-database sync for order and status data.
- Full transactional semantics for order creation + outbox + idempotency and for saga step updates.
- Mature ecosystem, replication, and backup options.
- JSONB used for outbox payloads where useful.

**Negative:**

- Schema must remain compatible for both R2DBC and JPA (e.g. naming, types).
- Scaling is vertical per instance; for very high scale, read replicas or CQRS-style separation could be considered later.

We accept the shared-schema constraint and use a single `init.sql` and migrations to keep both services aligned.
