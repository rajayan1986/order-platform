# ADR-003: Communication Patterns Between Services

## Status

Accepted.

## Context

We need clear patterns for:

- **Order creation to processing:** Reliable, asynchronous handoff after order is persisted.
- **Processing to notifications:** Reliable delivery of status updates to the notification layer.
- **Frontend to backend:** REST for orders and auth; real-time for status (WebSocket / SSE).

## Decision

1. **Order Service → Processing Service (async, events)**  
   Order Service does **not** call the Processing Service over HTTP. It writes an outbox row in the same transaction as the order; a relay job publishes to Kafka topic `order.created`. The Processing Service consumes this topic. This avoids synchronous coupling and keeps the write path fast and resilient (outbox pattern).

2. **Processing Service → Notification Service (async, events)**  
   The Processing Service publishes to Kafka topics (`order.status.updated`, `order.shipped`, `order.cancelled`, etc.). The Notification Service consumes these topics and pushes to connected clients. No direct HTTP between Processing and Notification.

3. **Frontend → Order Service (sync, REST)**  
   The Angular app calls Order Service over HTTP (proxied as `/api`): `POST /orders`, `GET /orders`, `GET /orders/{id}`, `POST /auth/token`. JWT is stored in memory; an interceptor attaches the token and redirects to login on 401.

4. **Notification Service → Frontend (real-time)**  
   - **WebSocket:** STOMP over SockJS at `/ws`; clients subscribe to `/topic/orders/{orderId}` for live status.
   - **SSE:** `GET /sse/orders/{orderId}` as fallback; reconnects use `Last-Event-ID` and fetch current state from Order Service.

5. **Notification Service → Order Service (sync, on demand)**  
   For SSE reconnect with `Last-Event-ID`, the Notification Service calls the Order Service REST API to get the current order status and sends it as the first event.

## Consequences

**Positive:**

- Loose coupling; services interact via events and well-defined APIs.
- Frontend gets real-time updates without polling the Order Service for status.
- Single REST surface for orders and auth simplifies security and CORS.

**Negative:**

- Eventual consistency between order state and notifications; we accept short delays (typically sub-second) for status propagation.
