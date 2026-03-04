# ADR-005: Microservices and Bounded Contexts

## Status

Accepted.

## Context

We need an architecture that:

- Supports high-volume order ingestion and asynchronous processing.
- Keeps the order-creation API responsive and resilient.
- Allows independent scaling and deployment of order acceptance, processing (saga), and notification delivery.
- Maintains clear boundaries and technology choices per concern (e.g. reactive vs blocking where appropriate).

## Decision

We adopt a **microservices-style** layout with three main backend services and a single frontend:

1. **Order Service (bounded context: Order Management)**  
   - **Responsibility:** Accept and validate order requests, persist orders, enforce idempotency and rate limits, publish order-created events.
   - **Style:** Reactive (Spring WebFlux, R2DBC) for non-blocking I/O and backpressure.
   - **Persistence:** PostgreSQL (orders, items, status history, outbox, idempotency); Redis for cache, rate limit, idempotency fast path.
   - **Outbound:** Kafka (outbox relay → `order.created`).

2. **Processing Service (bounded context: Order Fulfilment)**  
   - **Responsibility:** Consume order-created events, run saga (payment → compliance → approval → shipping), update order status and history, publish status events; handle failures and DLQ.
   - **Style:** Blocking (Spring MVC, JPA) for straightforward transaction boundaries and manual Kafka offset commit after saga completion.
   - **Persistence:** Same PostgreSQL; no Redis.
   - **Inbound:** Kafka (`order.created`); **Outbound:** Kafka (status topics), optional DLQ.

3. **Notification Service (bounded context: Notifications)**  
   - **Responsibility:** Consume status events from Kafka, maintain WebSocket (STOMP) and SSE subscriptions, push updates to clients; optional fetch from Order Service for SSE reconnect.
   - **Style:** Blocking (Spring MVC); WebSocket and SSE for real-time.
   - **Persistence:** Redis for session/subscription registry.
   - **Inbound:** Kafka (status topics); **Outbound:** Order Service HTTP (for SSE reconnect).

4. **Frontend (single SPA)**  
   - **Responsibility:** Login, dashboard (order list with live updates), create order, order detail with status timeline and real-time subscription.
   - **Stack:** Angular 17+, Angular Material, RxJS, STOMP over SockJS for WebSocket.

Shared infrastructure: **Kafka** (events), **PostgreSQL** (orders and saga state), **Redis** (cache, rate limit, idempotency, session registry). Observability: **Prometheus** (metrics), **Grafana** (dashboards), structured logs and tracing where implemented.

## Consequences

**Positive:**

- Clear separation of concerns: accept orders, process them, notify users.
- Each service can be scaled and deployed independently.
- Technology fit: reactive for I/O-bound API; blocking for saga and Kafka commit semantics.
- Single database for order and saga state avoids distributed transactions; events provide eventual consistency across services.

**Negative:**

- Operational and deployment complexity (multiple services, Kafka, Redis, DB).
- Cross-service consistency is eventual; we rely on events, idempotency, and idempotent saga steps.

We accept these trade-offs to meet scalability, resilience, and team/technology boundaries.
