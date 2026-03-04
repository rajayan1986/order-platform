#!/usr/bin/env bash
# Bulk order ingestion for load/processing tests (5000+ orders).
# Flow: Order Service -> Kafka -> Processing Service (saga).
# Usage: ./scripts/bulk-ingest-orders.sh [BASE_URL] [COUNT] [DELAY_MS]
# Example: ./scripts/bulk-ingest-orders.sh http://localhost:8080 5000 0
# Example: ./scripts/bulk-ingest-orders.sh http://localhost:8080 10000 20

set -e
BASE_URL="${1:-http://localhost:8080}"
COUNT="${2:-5000}"
DELAY_MS="${3:-0}"
BATCH_REPORT=500

echo "=== Bulk order ingestion ==="
echo "Base URL: $BASE_URL | Count: $COUNT | Delay: ${DELAY_MS}ms"
echo ""

# Login
echo "[1] Login..."
TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
TOKEN=$(echo "$TOKEN" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
  echo "FAIL: No token. Is the stack up?"
  exit 1
fi
echo "OK: JWT obtained"
echo ""

# Products and prices for variety (productId, quantity, price)
PRODUCTS=("PROD-001:1:29.99" "PROD-002:2:19.99" "PROD-003:1:99.50" "PROD-004:3:14.99" "PROD-005:1:49.00" \
          "PROD-006:2:25.00" "PROD-007:1:79.99" "PROD-008:4:9.99"  "PROD-009:1:129.00" "PROD-010:2:34.50" \
          "PROD-011:1:59.99" "PROD-012:2:22.00" "PROD-013:1:89.00" "PROD-014:3:11.99" "PROD-015:1:199.99" \
          "PROD-016:2:44.00" "PROD-017:1:15.99" "PROD-018:1:65.00" "PROD-019:2:39.99" "PROD-020:1:149.00")
NUM_PRODS=${#PRODUCTS[@]}

SUCCESS=0
FAIL=0
START=$(date +%s)

echo "[2] Ingesting $COUNT orders..."
for ((i=1; i<=COUNT; i++)); do
  IDEM="bulk-$(date +%s)-$i"
  CUST="CUST-bulk-$(printf "%05d" $i)"
  idx=$(( (i - 1) % NUM_PRODS ))
  IFS=':' read -r pid qty prc <<< "${PRODUCTS[$idx]}"
  BODY="{\"customerId\":\"$CUST\",\"idempotencyKey\":\"$IDEM\",\"items\":[{\"productId\":\"$pid\",\"quantity\":$qty,\"price\":$prc}]}"
  HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/orders" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "$BODY")
  if [ "$HTTP" = "202" ] || [ "$HTTP" = "200" ]; then
    SUCCESS=$((SUCCESS + 1))
  else
    FAIL=$((FAIL + 1))
  fi
  if [ $((i % BATCH_REPORT)) -eq 0 ]; then
    echo "  ... $i / $COUNT (ok: $SUCCESS, fail: $FAIL)"
  fi
  if [ "$DELAY_MS" -gt 0 ]; then
    sleep "$(awk "BEGIN {printf \"%.2f\", $DELAY_MS/1000}")" 2>/dev/null || sleep 0.02
  fi
done

END=$(date +%s)
ELAPSED=$((END - START))
echo ""
echo "=== Done ==="
echo "Success: $SUCCESS | Failed: $FAIL | Total: $COUNT | Elapsed: ${ELAPSED}s"
echo "Verify: curl -s $BASE_URL/api/orders/stats -H \"Authorization: Bearer \$TOKEN\""
echo "Processing consumes from Kafka; check /processing/stats and dashboard."
