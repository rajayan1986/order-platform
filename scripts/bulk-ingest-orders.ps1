# Bulk order ingestion for load/processing tests (5000+ orders).
# Flow: Order Service -> Kafka -> Processing Service (saga).
# Usage: .\scripts\bulk-ingest-orders.ps1 [-BaseUrl "http://localhost:8080"] [-Count 5000] [-DelayMs 0]

param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Count = 5000,
    [int]$DelayMs = 0
)

$ErrorActionPreference = "Stop"
$BatchReport = 500

# Product variants: productId, quantity, price
$Products = @(
    @("PROD-001", 1, 29.99), @("PROD-002", 2, 19.99), @("PROD-003", 1, 99.50), @("PROD-004", 3, 14.99), @("PROD-005", 1, 49.00),
    @("PROD-006", 2, 25.00), @("PROD-007", 1, 79.99), @("PROD-008", 4, 9.99),  @("PROD-009", 1, 129.00), @("PROD-010", 2, 34.50),
    @("PROD-011", 1, 59.99), @("PROD-012", 2, 22.00), @("PROD-013", 1, 89.00), @("PROD-014", 3, 11.99), @("PROD-015", 1, 199.99),
    @("PROD-016", 2, 44.00), @("PROD-017", 1, 15.99), @("PROD-018", 1, 65.00), @("PROD-019", 2, 39.99), @("PROD-020", 1, 149.00)
)
$NumProds = $Products.Length

Write-Host "=== Bulk order ingestion ==="
Write-Host "Base URL: $BaseUrl | Count: $Count | Delay: ${DelayMs}ms`n"

# Login
Write-Host "[1] Login..."
$loginBody = '{"username":"admin","password":"admin123"}'
$loginResp = Invoke-RestMethod -Uri "$BaseUrl/api/auth/token" -Method Post -Body $loginBody -ContentType "application/json"
$token = $loginResp.token
if (-not $token) { Write-Host "FAIL: No token"; exit 1 }
Write-Host "OK: JWT obtained`n"

$Success = 0
$Fail = 0
$Start = Get-Date

Write-Host "[2] Ingesting $Count orders..."
for ($i = 1; $i -le $Count; $i++) {
    $idem = "bulk-$(Get-Date -UFormat %s)-$i"
    $cust = "CUST-bulk-{0:D5}" -f $i
    $p = $Products[($i - 1) % $NumProds]
    $pid = $p[0]; $qty = $p[1]; $prc = $p[2]
    $orderBody = @{ customerId = $cust; idempotencyKey = $idem; items = @(@{ productId = $pid; quantity = $qty; price = $prc }) } | ConvertTo-Json
    $headers = @{ Authorization = "Bearer $token" }
    try {
        $resp = Invoke-WebRequest -Uri "$BaseUrl/api/orders" -Method Post -Body $orderBody -ContentType "application/json" -Headers $headers -UseBasicParsing
        if ($resp.StatusCode -eq 202 -or $resp.StatusCode -eq 200) { $Success++ } else { $Fail++ }
    } catch {
        $Fail++
    }
    if ($i % $BatchReport -eq 0) {
        Write-Host "  ... $i / $Count (ok: $Success, fail: $Fail)"
    }
    if ($DelayMs -gt 0) {
        Start-Sleep -Milliseconds $DelayMs
    }
}

$Elapsed = ((Get-Date) - $Start).TotalSeconds
Write-Host "`n=== Done ==="
Write-Host "Success: $Success | Failed: $Fail | Total: $Count | Elapsed: $([math]::Round($Elapsed, 1))s"
Write-Host "Verify: Invoke-RestMethod -Uri $BaseUrl/api/orders/stats -Headers @{ Authorization = 'Bearer ' + `$token }"
Write-Host "Processing consumes from Kafka; check /processing/stats and dashboard."
