#!/usr/bin/env bash
# E2E flow: Order Service → Kafka → Processing Service → DB + Kafka → Notification Service
# Prerequisites: Gateway + Order + Processing + Notification + Kafka + Postgres + Redis running.
# Usage: ./scripts/e2e-flow.sh [BASE_URL]
# Example: ./scripts/e2e-flow.sh http://localhost:8080

set -e
BASE_URL="${1:-http://localhost:8080}"

echo "=== E2E: Order → Processing → Notification ==="
echo "Base URL: $BASE_URL"
echo ""

# 1. Login (Order Service via Gateway)
echo "[1/5] Login (Order Service)..."
TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
TOKEN=$(echo "$TOKEN" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
  echo "FAIL: No token received. Is Order Service / Gateway up?"
  exit 1
fi
echo "OK: Got JWT"

# 2. Create order (Order Service → Kafka)
echo "[2/5] Create order (Order Service, publishes to Kafka)..."
IDEMPOTENCY="e2e-$(date +%s)"
CREATE_RESP=$(curl -s -X POST "$BASE_URL/api/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"customerId\":\"CUST-E2E\",\"idempotencyKey\":\"$IDEMPOTENCY\",\"items\":[{\"productId\":\"PROD-001\",\"quantity\":2,\"price\":29.99}]}")
ORDER_ID=$(echo "$CREATE_RESP" | grep -o '"orderId":"[^"]*"' | cut -d'"' -f4)
if [ -z "$ORDER_ID" ]; then
  echo "FAIL: No orderId in response. Response: $CREATE_RESP"
  exit 1
fi
echo "OK: Order created: $ORDER_ID"

# 3. List orders (Order Service)
echo "[3/5] List orders (Order Service)..."
curl -s "$BASE_URL/api/orders?page=0&size=5" -H "Authorization: Bearer $TOKEN" | head -c 200
echo "..."

# 4. Poll until SHIPPED (Processing Service consumes Kafka, updates DB)
echo "[4/5] Poll order until status = SHIPPED (Processing Service saga)..."
MAX_ATTEMPTS=30
ATTEMPT=0
while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  STATUS=$(curl -s "$BASE_URL/api/orders/$ORDER_ID" -H "Authorization: Bearer $TOKEN" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
  echo "  Status: $STATUS"
  if [ "$STATUS" = "SHIPPED" ]; then
    echo "OK: Order SHIPPED (Processing Service completed saga)"
    break
  fi
  ATTEMPT=$((ATTEMPT + 1))
  sleep 2
done
if [ "$STATUS" != "SHIPPED" ]; then
  echo "WARN: Did not reach SHIPPED after ${MAX_ATTEMPTS} attempts. Check Processing Service and Kafka."
fi

# 5. Stats from all three services (Notification delivers via WebSocket/SSE)
echo "[5/5] Stats from Order, Processing, Notification..."
echo "  Order:       $(curl -s "$BASE_URL/api/orders/stats" -H "Authorization: Bearer $TOKEN" | head -c 80)..."
echo "  Processing:  $(curl -s "$BASE_URL/processing/stats" | head -c 80)..."
echo "  Notification: $(curl -s "$BASE_URL/notify-api/stats" | head -c 80)..."
echo ""
echo "=== E2E flow complete. Order ID: $ORDER_ID ==="
echo "Dashboard: http://localhost:4200 (open order detail for live WebSocket updates)."
