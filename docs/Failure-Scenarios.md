# Failure Scenario Explanation

This document describes how the Order Processing Platform handles specific failure situations. Each section states whether the behaviour is **handled in code** and where (service, class, or file).

---

## 1. Messaging system (Kafka) is unavailable

**Handled in code: Yes**

### Behaviour

- **Order Service** does not publish to Kafka directly during the request. It writes an **outbox row** in the same database transaction as the order. A scheduled job (**Outbox Relay**) periodically reads unpublished events and publishes them to Kafka. If Kafka is down, the publish call fails; the relay **does not** set `published_at`, so the event remains in the outbox and is retried on the next run.
- **Processing Service** and **Notification Service** consumers will fail to poll or connect when Kafka is unavailable. Kafka clients typically retry connection/rebalance; when Kafka comes back, consumers resume and process from their committed offsets. No application-level retry is required for “broker down” beyond client reconnection.

### Code references

| Location | What it does |
|----------|----------------|
| `order-service/.../service/OutboxRelay.java` | `@Scheduled(fixedRate = 1000)` loads events with `published_at IS NULL`, calls `orderEventProducer.publish(...)`. On exception, `onErrorResume` logs and returns without updating the event, so the same event is retried on the next run. |
| `order-service/.../domain/OutboxEvent.java` | `published_at` is null until successfully published. |
| `order-service/.../repository/OutboxRepository.java` | `findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()` used by the relay. |

### Summary

Orders are still created and stored; the event is durable in the outbox. When Kafka is available again, the relay will eventually publish all pending events. No order is lost; processing is delayed until Kafka recovers.

---

## 2. Database failure during order creation

**Handled in code: Yes**

### Behaviour

- Order creation runs inside a **single reactive transaction** (R2DBC). The same transaction writes: order, order items, initial status history, outbox event, and idempotency key. If the database fails at any point (connection loss, constraint violation, disk full, etc.), the **entire transaction is rolled back**. The client receives an error (e.g. 500) and no partial order or outbox row is persisted.
- If the failure is transient and the client retries with the **same idempotency key**, the request is treated as a duplicate (see “Duplicate order requests” below) and the client gets the same response as the first attempt, avoiding double creation.

### Code references

| Location | What it does |
|----------|----------------|
| `order-service/.../service/OrderService.java` | `persistOrderAndOutbox()` builds a chain of `orderRepository.save()`, item saves, `statusHistoryRepository.save()`, `outboxRepository.save()`, `idempotencyKeyRepository.save()` and runs it under `transactionalOperator.transactional(saveChain)`. Any failure rolls back the whole chain. |
| `order-service/.../exception/GlobalExceptionHandler.java` | Unhandled exceptions (including DB errors) are mapped to 500 with a generic message. |
| `order-service/.../controller/OrderController.java` | Create order is wrapped with a Resilience4j circuit breaker; after repeated failures the circuit can open and return 503 instead of repeatedly hitting the DB. |

### Summary

No partial writes; client sees a clear error and can retry with the same idempotency key. Circuit breaker prevents hammering the DB when it is consistently failing.

---

## 3. Processing service crash during execution

**Handled in code: Yes**

### Behaviour

- The Processing Service consumes from Kafka with **manual offset commit** and commits **only after** the saga (and optional retries) completes successfully or the message is sent to the DLQ. If the service **crashes** (process kill, OOM, etc.) before committing, the consumer never sends an ack. When the process restarts (or another instance in the group takes over), Kafka **redelivers** the same `order.created` message.
- Saga steps are **idempotent**: each step checks `order_status_history` (e.g. “has PAYMENT_VALIDATED already been recorded for this order?”). If the step was already applied before the crash, the re-run skips the work and returns success, so the saga can continue or complete without duplicating side effects.

### Code references

| Location | What it does |
|----------|----------------|
| `processing-service/.../config/KafkaConsumerConfig.java` | `ENABLE_AUTO_COMMIT_CONFIG = false`, `AckMode.MANUAL_IMMEDIATE` so offsets are committed only when the listener calls `ack.acknowledge()`. |
| `processing-service/.../consumer/OrderEventConsumer.java` | After `sagaOrchestrator.processOrder(orderId)` succeeds (or after sending to DLQ), it calls `ack.acknowledge()`. On crash, ack is never sent, so the record is redelivered. |
| `processing-service/.../steps/PaymentStep.java` (and other steps) | `orderStatusRepository.existsByOrderIdAndToStatus(orderId, STEP_STATUS)` before applying the step; if already present, step is skipped (idempotent). Same pattern in other saga steps. |

### Summary

Crash during processing leads to at-least-once redelivery. Idempotent saga steps prevent duplicate updates; the saga either completes or is retried until success or DLQ.

---

## 4. Notification service downtime

**Handled in code: Partially**

### Behaviour

- **Kafka:** The Notification Service consumes from status topics with a consumer group. While the service is down, it does not commit offsets; messages **accumulate** in Kafka. When the service comes back, it resumes from the last committed offset and processes the backlog. So **no status events are lost** from the messaging perspective; delivery is delayed.
- **Clients (frontend):** WebSocket/SSE connections to the Notification Service will drop when the service is down. The frontend **reconnects** automatically (STOMP client with a 5s reconnect delay). After reconnect, clients can subscribe again to `/topic/orders/{id}`. They do not automatically receive events that were emitted while the connection was down; they can refetch current state from the Order Service (e.g. GET order by id or use SSE reconnect with `Last-Event-ID` and backend fetch).

### Code references

| Location | What it does |
|----------|----------------|
| `notification-service/.../consumer/StatusEventConsumer.java` | Listener with manual ack; processes status events and pushes to WebSocket and SSE. When the service is down, no ack is sent, so Kafka will redeliver after restart. |
| `frontend/.../services/websocket.service.ts` | `Client` from `@stomp/stompjs` with `reconnectDelay: 5000`; reconnects when the connection is lost (e.g. Notification Service down). |
| `notification-service/.../sse/SseController.java` | SSE endpoint can use `Last-Event-ID` and call Order Service to send current state on reconnect. |

**Note:** If the Notification Service is up but pushing to WebSocket/SSE fails (e.g. client disconnected), the consumer still acknowledges the Kafka message (`StatusEventConsumer.consume` acks in both success and catch paths). That event is not redelivered; clients can refresh order state via the Order Service API or SSE reconnect with `Last-Event-ID`.

### Summary

- **Event flow:** Handled in code — Kafka retains messages; Notification Service catches up after restart.
- **Real-time UX during outage:** Partially handled — reconnect is automatic; missed events are not replayed, but order state can be refreshed via REST or SSE reconnect behaviour.

---

## 5. Duplicate order requests

**Handled in code: Yes**

### Behaviour

- Every create-order request must include an **idempotency key** (client-generated, e.g. UUID). The Order Service checks this key **before** doing any write:
  1. **Redis:** If a cached response exists for this key, return it immediately (same orderId/response as the original request).
  2. **Database:** If Redis misses, the service checks the `idempotency_keys` table. If a row exists for this key, it returns the stored `order_id` and response code (e.g. 202) without creating a new order.
  3. Only if both checks miss does the service run the transactional “create order + outbox + idempotency key” flow. After success, it stores the idempotency key (and response) in the DB and in Redis (with TTL) so future duplicates are detected.

So duplicate requests (same idempotency key) — whether from retries, double-clicks, or replays — receive the **same** response and do not create a second order.

### Code references

| Location | What it does |
|----------|----------------|
| `order-service/.../service/OrderService.java` | `createOrder()`: `checkRedisIdempotency(idempotencyKey)` then `checkDbIdempotency(idempotencyKey)`; only in their absence does it call `persistOrderAndOutbox(request)`. After persist, saves idempotency key to DB and caches response in Redis. |
| `order-service/.../repository/IdempotencyKeyRepository.java` | `findByKey(key)` used for DB idempotency check. |
| `order-service/.../domain/IdempotencyKey.java` | Entity storing key, orderId, responseCode. |
| `frontend/.../orders/create-order-dialog/create-order-dialog.component.ts` | Generates a new UUID as `idempotencyKey` per “Create Order” submission. |

### Summary

Duplicate order requests with the same idempotency key are detected and return the original response; no duplicate orders are created. Handled in code end-to-end (Order Service + frontend sending the key).

---

## 6. Service instance crashes during processing

**Handled in code: Yes (with at-least-once semantics)**

### Behaviour

- **Order Service instance crash:** If the crash happens after the transaction is committed, the order and outbox row are already in the database. Another instance (or the same after restart) will run the Outbox Relay and publish the event. If the crash happens before commit, the transaction is rolled back; the client can retry with the same idempotency key. So order creation is safe.
- **Processing Service instance crash:** Described in “Processing service crash during execution”: no offset commit until saga completion (or DLQ), so Kafka redelivers; saga steps are idempotent, so re-runs are safe.
- **Notification Service instance crash:** Consumer does not commit while down; on restart (or failover to another instance in the group), consumption continues from last committed offset. No special application logic beyond Kafka consumer semantics.
- **Frontend:** No server-side “processing” state; users can refresh or reconnect WebSocket/SSE.

### Code references

| Location | What it does |
|----------|----------------|
| `order-service/.../service/OutboxRelay.java` | Any instance can run the relay; they all read `published_at IS NULL` and publish. First to publish and save wins; no double publish because `published_at` is set only on success. |
| `processing-service/.../consumer/OrderEventConsumer.java` | Offset committed only after `processOrder()` succeeds or message is sent to DLQ; crash before that causes redelivery. |
| `processing-service/.../steps/*Step.java` | Idempotency via `existsByOrderIdAndToStatus` (or equivalent) so a re-delivered message does not duplicate step effects. |

### Summary

Instance crashes are handled in code: orders are not lost (transaction + outbox), processing is redelivered and idempotent, and notification consumption resumes from Kafka. The system exhibits at-least-once delivery with idempotent handling of duplicates.
