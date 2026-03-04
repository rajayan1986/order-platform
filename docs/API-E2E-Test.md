# API E2E Test — Same Data as Dashboard

Use these requests to test the **full flow** with the **same sample data** the UI uses. Base URL: **Order Service** directly `http://localhost:8087` or via Gateway `http://localhost:8080/api` (strip `/api` when calling the paths below; e.g. Gateway: `POST http://localhost:8080/api/auth/token`).

---

## How to run the full stack (Order → Processing → Notification)

1. **Start all services** (from repo root):
   ```bash
   cd order-processing-platform
   docker compose up --build
   ```
   See [README — Quick Start](README.md) for details. Ensure Gateway (8080), Order Service, Processing Service, Notification Service, Kafka, Postgres, and Redis are up.

2. **Run the E2E script** (exercises Order → Kafka → Processing → Notification):
   - **Bash (Linux / Mac / WSL / Git Bash):**
     ```bash
     chmod +x scripts/e2e-flow.sh
     ./scripts/e2e-flow.sh http://localhost:8080
     ```
   - **PowerShell (Windows):**
     ```powershell
     .\scripts\e2e-flow.ps1 -BaseUrl "http://localhost:8080"
     ```
   The script: logs in → creates an order → lists orders → polls until status is SHIPPED (Processing) → calls stats on Order, Processing, and Notification.

---

## Why does GET /sse/orders/{orderId} keep loading in Swagger?

**GET /sse/orders/{orderId}** is a **Server-Sent Events (SSE)** endpoint: it keeps the HTTP connection open and streams events over time. It does not return a single response and close. In Swagger UI the request therefore appears to “hang” or “keep pending” because the connection stays open (until timeout or client disconnect). This is expected. To test SSE, use a client that supports streaming (e.g. browser EventSource, or `curl -N`), or rely on the dashboard’s WebSocket/SSE for live updates.

---

## 1. Get JWT (login)

**Request:**

```http
POST /auth/token
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

**Example (curl):**

```bash
curl -s -X POST http://localhost:8087/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

**Sample response (200):**

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

Save the `token` value; use it as `Authorization: Bearer <token>` in the next steps.

---

## 2. Create order (same data as dashboard “Create Order”)

**Request:**

```http
POST /orders
Content-Type: application/json
Authorization: Bearer <TOKEN>

{
  "customerId": "CUST-E2E-001",
  "idempotencyKey": "e2e-test-001-a1b2c3d4",
  "items": [
    {
      "productId": "PROD-001",
      "quantity": 2,
      "price": 29.99
    }
  ]
}
```

**Example (curl; replace `TOKEN`):**

```bash
TOKEN="<paste token from step 1>"

curl -s -X POST http://localhost:8087/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "customerId": "CUST-E2E-001",
    "idempotencyKey": "e2e-test-001-a1b2c3d4",
    "items": [
      {
        "productId": "PROD-001",
        "quantity": 2,
        "price": 29.99
      }
    ]
  }'
```

**Sample response (202 Accepted):**

```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING"
}
```

Save `orderId` for step 4. The Processing Service will consume the order from Kafka and update status (e.g. PROCESSING → SHIPPED). The dashboard shows these updates live.

---

## 3. List orders (dashboard table)

**Request:**

```http
GET /orders?page=0&size=10
Authorization: Bearer <TOKEN>
```

**Example (curl):**

```bash
curl -s "http://localhost:8087/orders?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

**Sample response (200):**

```json
{
  "content": [
    {
      "orderId": "550e8400-e29b-41d4-a716-446655440000",
      "customerId": "CUST-E2E-001",
      "status": "PENDING",
      "totalAmount": 59.98,
      "createdAt": "2026-03-02T14:30:00Z"
    }
  ],
  "totalElements": 1
}
```

This matches what the dashboard table shows (Order ID, Customer, Status, Amount, Created At).

---

## 4. Get order by ID (order detail page)

**Request:**

```http
GET /orders/{orderId}
Authorization: Bearer <TOKEN>
```

**Example (curl; replace `ORDER_ID` with the `orderId` from step 2):**

```bash
ORDER_ID="550e8400-e29b-41d4-a716-446655440000"

curl -s "http://localhost:8087/orders/$ORDER_ID" \
  -H "Authorization: Bearer $TOKEN"
```

**Sample response (200):**

```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "customerId": "CUST-E2E-001",
  "status": "PROCESSING",
  "totalAmount": 59.98,
  "items": [
    {
      "id": "item-uuid",
      "productId": "PROD-001",
      "quantity": 2,
      "unitPrice": 29.99
    }
  ],
  "statusHistory": [
    { "fromStatus": null, "toStatus": "PENDING", "changedAt": "2026-03-02T14:30:00Z" },
    { "fromStatus": "PENDING", "toStatus": "PROCESSING", "changedAt": "2026-03-02T14:30:05Z" }
  ],
  "createdAt": "2026-03-02T14:30:00Z",
  "updatedAt": "2026-03-02T14:30:05Z"
}
```

This matches the **order detail** view (full order + status history). Status may change to SHIPPED as the saga completes.

---

## 5. Optional — Create another order (same customer, different idempotency key)

Use a **new** `idempotencyKey` so it is treated as a new order; same customer and product as above:

```bash
curl -s -X POST http://localhost:8087/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "customerId": "CUST-E2E-001",
    "idempotencyKey": "e2e-test-002-b2c3d4e5",
    "items": [
      {
        "productId": "PROD-001",
        "quantity": 1,
        "price": 29.99
      }
    ]
  }'
```

Then call **GET /orders?page=0&size=10** again to see both orders in the list (same data family as the dashboard).

---

## Inter-service communication and dashboard stats

**Flow:** Order Service creates the order and publishes **order.created** to Kafka. **Processing Service** consumes it, runs the saga (payment → compliance → approval → shipping), updates the shared DB, and publishes **order.status.updated** / **order.shipped** to Kafka. **Notification Service** consumes those events and pushes to **WebSocket** and **SSE** so the dashboard gets live updates. There is no direct HTTP between the three; all coordination is via Kafka.

**Dashboard stats (live from all three services):**

| Service | Via Gateway | Path | Response (example) |
|---------|-------------|------|--------------------|
| Order | `/api/orders/stats` | Order Service | `totalOrders`, `ordersToday`, `pendingCount`, `processingCount`, `shippedCount`, `byStatus` |
| Processing | `/processing/stats` | Processing Service | `ordersProcessedToday`, `pendingCount`, `processingCount`, `shippedCount` |
| Notification | `/notify-api/stats` | Notification Service | `connectedWebSockets`, `connectedSse`, `eventsDeliveredTotal` |

**Example (curl via Gateway, port 8080):**

```bash
# Order stats (requires JWT for /api)
curl -s "http://localhost:8080/api/orders/stats" -H "Authorization: Bearer $TOKEN"

# Processing stats (no auth)
curl -s "http://localhost:8080/processing/stats"

# Notification stats (no auth)
curl -s "http://localhost:8080/notify-api/stats"
```

The dashboard UI calls all three and shows **Processing status** and **Notification stats** in the right column (Processed today, Pending, In progress, Shipped; WebSocket/SSE connections, Events delivered).

---

## Use case: test all three services (Order → Processing → Notification)

Follow these steps to verify **Order**, **Processing**, and **Notification** work together.

### 1. Start the stack

Ensure Kafka, Order Service, Processing Service, Notification Service, and (optional) Gateway and frontend are running (e.g. `docker compose up` or run each locally).

### 2. Get JWT and create an order (Order Service)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token)

ORDER_ID=$(curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"customerId":"CUST-E2E-001","idempotencyKey":"e2e-'$(date +%s)'","items":[{"productId":"PROD-001","quantity":2,"price":29.99}]}' \
  | jq -r .orderId)

echo "Order ID: $ORDER_ID"
```

Order Service creates the order and publishes **order.created** to Kafka.

### 3. Poll order until status is SHIPPED (Processing Service)

Processing Service consumes **order.created**, runs the saga, and updates the order status. Poll **Order Service** (which reads the same DB) until status becomes **SHIPPED**:

```bash
until [ "$(curl -s "http://localhost:8080/api/orders/$ORDER_ID" -H "Authorization: Bearer $TOKEN" | jq -r .status)" = "SHIPPED" ]; do
  echo "Status: $(curl -s "http://localhost:8080/api/orders/$ORDER_ID" -H "Authorization: Bearer $TOKEN" | jq -r .status)"
  sleep 2
done
echo "Order SHIPPED."
```

This confirms **Processing Service** consumed the event and completed the saga.

### 4. Verify Notification Service (live updates)

- **Option A (UI):** Open the dashboard (http://localhost:4200), log in, and open the order detail for `$ORDER_ID`. You should see status history and (if the page was open during step 3) live status updates via WebSocket.
- **Option B (stats):** Call Notification stats; after the dashboard has been open, `connectedWebSockets` or `eventsDeliveredTotal` should reflect activity:

```bash
curl -s "http://localhost:8080/notify-api/stats" | jq
```

### 5. Verify all three stats endpoints

```bash
echo "Order stats:"     && curl -s "http://localhost:8080/api/orders/stats"     -H "Authorization: Bearer $TOKEN" | jq
echo "Processing stats:" && curl -s "http://localhost:8080/processing/stats"   | jq
echo "Notification stats:" && curl -s "http://localhost:8080/notify-api/stats" | jq
```

Use this use case to validate the full chain: **Order Service** (create + list + get) → **Kafka** → **Processing Service** (saga) → **DB + Kafka** → **Notification Service** (WebSocket/SSE + stats).

---

## Bulk ingestion (5000+ orders) for load/processing tests

To test **ingestion and processing** at scale (e.g. 5000+ orders), use the bulk scripts. Each order is created via **POST /api/orders** (same as the UI), so the full flow runs: **Order Service** → Kafka → **Processing Service** (saga) → DB + Kafka → **Notification Service**.

**Prerequisites:** Stack running (`docker compose up`), Gateway at 8080.

### Bash (Linux / Mac / WSL / Git Bash)

```bash
# Ingest 5000 orders (default), no delay between requests
./scripts/bulk-ingest-orders.sh http://localhost:8080 5000 0

# Ingest 10000 orders with 20ms delay to reduce load
./scripts/bulk-ingest-orders.sh http://localhost:8080 10000 20
```

**Usage:** `./scripts/bulk-ingest-orders.sh [BASE_URL] [COUNT] [DELAY_MS]`  
- **BASE_URL:** Gateway URL (default `http://localhost:8080`)  
- **COUNT:** Number of orders to create (default **5000**)  
- **DELAY_MS:** Optional delay in ms between requests (0 = no delay)

### PowerShell (Windows)

```powershell
# 5000 orders (default)
.\scripts\bulk-ingest-orders.ps1

# 6000 orders, 10ms delay
.\scripts\bulk-ingest-orders.ps1 -BaseUrl "http://localhost:8080" -Count 6000 -DelayMs 10
```

**Parameters:** `-BaseUrl`, `-Count` (default **5000**), `-DelayMs` (default **0**).

### After ingestion

- **Order stats:** `curl -s http://localhost:8080/api/orders/stats -H "Authorization: Bearer $TOKEN"` (use token from script output or login again).
- **Processing stats:** `curl -s http://localhost:8080/processing/stats` — processed count will rise as the Processing Service consumes from Kafka.
- **Dashboard:** Open http://localhost:4200; KPIs and Live Order Stream will reflect the ingested orders (paginated). Processing will catch up over time.

Data is varied: customers `CUST-bulk-00001` … `CUST-bulk-05000`, products `PROD-001` … `PROD-020`, mixed quantities and prices.

### Troubleshooting bulk ingestion (many failures after ~100 success)

**Cause:** The Order Service applies a **rate limit per client IP** (default **100 requests per minute**). After ~100 successful order creations, further requests in the same minute get **HTTP 429 (Rate limit exceeded)** and are counted as failed.

**What you see:** Success count stays around 100 (or 200 after the next minute), with most of the 5000 requests failing.

**Fix (Docker Compose):** The stack is configured for bulk runs: `APP_RATE_LIMIT_REQUESTS_PER_MINUTE=10000` in `docker-compose.yml` for `order-service`. Restart the stack so the new limit is applied, then re-run the bulk script:

```bash
docker compose down
docker compose up -d
# wait for services to be healthy, then:
./scripts/bulk-ingest-orders.sh http://localhost:8080 5000 0
```

**Fix (running Order Service locally):** In `order-service/src/main/resources/application.yml` set a higher limit for testing, e.g.:

```yaml
app:
  rate-limit:
    requests-per-minute: 10000
```

Or pass the env var when starting: `APP_RATE_LIMIT_REQUESTS_PER_MINUTE=10000`.

**How to confirm it’s rate limiting:**

1. **Response body:** A rate-limited request returns **429** with a body like `{"message":"Rate limit exceeded",...}`. To inspect the first non-2xx response during a run, you can log the response (e.g. in the script) or call the API once after 100 quick successes and check status and body.
2. **Order Service logs:** Look for log lines indicating rate limit (e.g. “Rate limit exceeded” or similar).
3. **Prometheus:** Check the metric `rate_limit_rejected_total` (or `rate.limit.rejected.total`) on the Order Service; it increments for each rejected request.

**Alternative (no config change):** Keep the default 100/min and throttle the script so you stay under the limit, e.g. ~600 ms between requests: `./scripts/bulk-ingest-orders.sh http://localhost:8080 5000 600` (about 5000 orders in ~50 minutes).

---

## Clean existing order data (reset for testing)

To delete all orders and related data (order_items, order_status_history, outbox_events, idempotency_keys) for a fresh run:

**Bash (with local psql):**
```bash
./scripts/clean-orders.sh localhost 5432
```

**Bash (via Docker postgres container, no local psql):**
```bash
DOCKER=1 ./scripts/clean-orders.sh
```

**PowerShell (with local psql):**
```powershell
.\scripts\clean-orders.ps1 -PgHost localhost -PgPort 5432
```

Defaults: host `localhost`, port `5432`, user `orderuser`, password `orderpass`, db `orderdb` (match docker-compose). Override with env: `PG_USER`, `PG_PASSWORD`, `PG_DB`.

---

## Summary

| Step | Method | Path | Service | Purpose |
|------|--------|------|---------|---------|
| 1 | POST | `/api/auth/token` | Order | Login, get JWT |
| 2 | POST | `/api/orders` | Order | Create order (publishes to Kafka) |
| 3 | GET | `/api/orders?page=0&size=10` | Order | List orders (dashboard table) |
| 4 | GET | `/api/orders/{id}` | Order | Order detail + status history (updated by Processing) |
| — | GET | `/api/orders/stats` | Order | Dashboard KPIs |
| — | GET | `/processing/stats` | Processing | Processing status (processed today, by status) |
| — | GET | `/notify-api/stats` | Notification | Notification stats (WS/SSE connections, events) |

Use the **same** `customerId` / `productId` / `price` in step 2 as in the table above to align with the dashboard “Create Order” flow. Live status updates appear in the UI via **Notification Service** (WebSocket); the API reflects the same state when you poll GET `/api/orders` or GET `/api/orders/{id}` (updated by **Processing Service**).
