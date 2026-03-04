# ADR-004: Caching Strategy with Redis

## Status

Accepted.

## Context

We need to:

- Reduce read load on the database for frequently accessed order details.
- Enforce rate limits per client (e.g. per IP) without storing all state in the application.
- Support idempotency checks (e.g. duplicate `POST /orders` with same idempotency key) with fast lookups before hitting the DB.
- Allow the Notification Service to track WebSocket subscriptions per order (e.g. for routing and TTL refresh).

## Decision

We use **Redis** for:

1. **Order detail cache (Order Service)**  
   After loading an order from PostgreSQL (cache miss), we store the serialized order response in Redis with a short TTL (e.g. 30 seconds). Key pattern: `order:{orderId}`. Subsequent `GET /orders/{id}` requests use the cache when present, reducing DB load for hot orders.

2. **Rate limiting (Order Service)**  
   Token-bucket (or counter) per client IP in Redis. Key pattern: `ratelimit:{clientIp}`. Limits are enforced before order creation; exceeded requests return 429 with `Retry-After`.

3. **Idempotency (Order Service)**  
   First check: Redis key `idempotency:{key}`. If missing, proceed and then check DB idempotency table; on first successful creation we store the idempotency key in both DB and Redis (with TTL) so repeat requests with the same key are answered from cache or DB without re-executing the flow.

4. **WebSocket session registry (Notification Service)**  
   Redis stores which sessions are subscribed to which order IDs (e.g. hash `ws:sessions:{orderId}`). Used to decide where to push status updates and to refresh TTL on activity.

We do **not** cache order list (paginated) or mutation responses; only single-order reads and idempotency/rate-limit metadata.

## Consequences

**Positive:**

- Lower latency and DB load for repeated order reads.
- Centralized, fast rate limiting and idempotency checks.
- Shared session state for notifications when running multiple Notification Service instances (future).

**Negative:**

- Cache invalidation: short TTL and status updates via events mean eventual consistency; we accept that list/detail may be briefly stale.
- Redis is a single point of failure for rate limit and idempotency; we accept this for the current scale and can add replication later.
