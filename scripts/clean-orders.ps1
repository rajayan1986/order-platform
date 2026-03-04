# Delete all existing order records (orders, order_items, order_status_history, outbox_events, idempotency_keys).
# Use for a clean slate before bulk ingestion or e2e tests.
# Usage: .\scripts\clean-orders.ps1 [-PgHost localhost] [-PgPort 5432]
# Env (optional): PG_USER, PG_PASSWORD, PG_DB (defaults: orderuser, orderpass, orderdb)

param(
    [string]$PgHost = "localhost",
    [int]$PgPort = 5432
)

$PgUser = if ($env:PG_USER) { $env:PG_USER } else { "orderuser" }
$PgPassword = if ($env:PG_PASSWORD) { $env:PG_PASSWORD } else { "orderpass" }
$PgDb = if ($env:PG_DB) { $env:PG_DB } else { "orderdb" }

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$SqlFile = Join-Path $ScriptDir "clean-orders.sql"

if (-not (Test-Path $SqlFile)) {
    Write-Error "SQL file not found: $SqlFile"
    exit 1
}

Write-Host "=== Clean order data ==="
Write-Host "Host: ${PgHost}:${PgPort} | DB: $PgDb`n"

$env:PGPASSWORD = $PgPassword
try {
    psql -h $PgHost -p $PgPort -U $PgUser -d $PgDb -f $SqlFile
} finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}

Write-Host "`nDone. All orders, order_items, order_status_history, outbox_events, and idempotency_keys deleted."
