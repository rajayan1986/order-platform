-- Clean all order-related data (for fresh bulk/e2e test runs).
-- Order: idempotency_keys and outbox_events first, then orders (CASCADE removes order_items, order_status_history).

BEGIN;

DELETE FROM idempotency_keys;
DELETE FROM outbox_events;
DELETE FROM orders;

COMMIT;
