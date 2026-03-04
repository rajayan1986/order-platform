# E2E flow: Order Service -> Kafka -> Processing Service -> Notification Service
# Prerequisites: Gateway + Order + Processing + Notification + Kafka + Postgres + Redis running.
# Usage: .\scripts\e2e-flow.ps1 [-BaseUrl "http://localhost:8080"]

param([string]$BaseUrl = "http://localhost:8080")

$ErrorActionPreference = "Stop"

Write-Host "=== E2E: Order -> Processing -> Notification ==="
Write-Host "Base URL: $BaseUrl`n"

# 1. Login
Write-Host "[1/5] Login (Order Service)..."
$loginBody = '{"username":"admin","password":"admin123"}'
$loginResp = Invoke-RestMethod -Uri "$BaseUrl/api/auth/token" -Method Post -Body $loginBody -ContentType "application/json"
$token = $loginResp.token
if (-not $token) { Write-Host "FAIL: No token"; exit 1 }
Write-Host "OK: Got JWT"

# 2. Create order
Write-Host "[2/5] Create order (Order Service, publishes to Kafka)..."
$idem = "e2e-$(Get-Date -UFormat %s)"
$orderBody = @{ customerId = "CUST-E2E"; idempotencyKey = $idem; items = @(@{ productId = "PROD-001"; quantity = 2; price = 29.99 }) } | ConvertTo-Json
$headers = @{ Authorization = "Bearer $token" }
$createResp = Invoke-RestMethod -Uri "$BaseUrl/api/orders" -Method Post -Body $orderBody -ContentType "application/json" -Headers $headers
$orderId = $createResp.orderId
if (-not $orderId) { Write-Host "FAIL: No orderId"; exit 1 }
Write-Host "OK: Order created: $orderId"

# 3. List orders
Write-Host "[3/5] List orders..."
Invoke-RestMethod -Uri "$BaseUrl/api/orders?page=0&size=5" -Headers $headers | ConvertTo-Json -Depth 2 | Out-String | Write-Host

# 4. Poll until SHIPPED
Write-Host "[4/5] Poll order until status = SHIPPED (Processing Service saga)..."
$max = 30; $attempt = 0; $status = ""
while ($attempt -lt $max) {
    $order = Invoke-RestMethod -Uri "$BaseUrl/api/orders/$orderId" -Headers $headers
    $status = $order.status
    Write-Host "  Status: $status"
    if ($status -eq "SHIPPED") { Write-Host "OK: Order SHIPPED"; break }
    $attempt++; Start-Sleep -Seconds 2
}
if ($status -ne "SHIPPED") { Write-Host "WARN: Did not reach SHIPPED after $max attempts" }

# 5. Stats
Write-Host "[5/5] Stats from Order, Processing, Notification..."
Write-Host "  Order:        $((Invoke-RestMethod -Uri "$BaseUrl/api/orders/stats" -Headers $headers | ConvertTo-Json -Compress))"
Write-Host "  Processing:   $((Invoke-RestMethod -Uri "$BaseUrl/processing/stats" | ConvertTo-Json -Compress))"
Write-Host "  Notification: $((Invoke-RestMethod -Uri "$BaseUrl/notify-api/stats" | ConvertTo-Json -Compress))"

Write-Host "`n=== E2E flow complete. Order ID: $orderId ==="
Write-Host "Dashboard: http://localhost:4200"
