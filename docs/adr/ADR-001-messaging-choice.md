# ADR-001: Use Apache Kafka for Event Messaging

## Status

Accepted.

## Context

The platform needs a messaging backbone for:

- Publishing order-created events from the Order Service after persistence.
- Delivering those events to the Processing Service for saga execution.
- Publishing order status updates (payment, compliance, approval, shipping, failure) from the Processing Service.
- Delivering status events to the Notification Service for real-time push (WebSocket/SSE).
- Handling failed messages via a Dead Letter Queue (DLQ).

Requirements include: at-least-once delivery, ordering per key where needed, replay capability, and scalability for high volume.

## Decision

We use **Apache Kafka** as the event bus between Order Service, Processing Service, and Notification Service.

- **Order Service** writes to an outbox table in the same transaction as the order; a relay job publishes outbox rows to the `order.created` topic.
- **Processing Service** consumes `order.created` with a dedicated consumer group, processes orders through a saga, and publishes to status topics (`order.status.updated`, `order.shipped`, `order.cancelled`, etc.). Failed messages after retries go to `order.dlq`.
- **Notification Service** consumes status topics and pushes updates to WebSocket and SSE clients.

## Consequences

**Positive:**

- Durable, replayable log; consumers can reprocess from offsets.
- High throughput and horizontal scaling via partitions and consumer groups.
- Clear separation of write path (order creation) and read/process path (saga, notifications).
- DLQ supports operational visibility and replay of failures.

**Negative:**

- Operational complexity (Kafka + Zookeeper in this setup).
- Need to handle exactly-once / idempotency in consumers (e.g. saga steps, notification delivery).

We mitigate consumer-side duplication with idempotency (e.g. saga step history, optional deduplication in notification handling).
