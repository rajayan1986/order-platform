#!/usr/bin/env bash
# Delete all existing order records (orders, order_items, order_status_history, outbox_events, idempotency_keys).
# Use for a clean slate before bulk ingestion or e2e tests.
# Usage: ./scripts/clean-orders.sh [PG_HOST] [PG_PORT]
#        DOCKER=1 ./scripts/clean-orders.sh   # run via postgres container (no local psql needed)
# Default: host=localhost, port=5432 (match docker-compose postgres).

set -e
PG_HOST="${1:-localhost}"
PG_PORT="${2:-5432}"
PG_USER="${PG_USER:-orderuser}"
PG_PASSWORD="${PG_PASSWORD:-orderpass}"
PG_DB="${PG_DB:-orderdb}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/clean-orders.sql"

echo "=== Clean order data ==="
echo "Host: $PG_HOST:$PG_PORT | DB: $PG_DB"
echo ""

if [ -n "$DOCKER" ] && [ "$DOCKER" != "0" ]; then
  docker exec -i postgres psql -U "$PG_USER" -d "$PG_DB" < "$SQL_FILE"
else
  export PGPASSWORD="$PG_PASSWORD"
  psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -f "$SQL_FILE"
  unset PGPASSWORD
fi

echo ""
echo "Done. All orders, order_items, order_status_history, outbox_events, and idempotency_keys deleted."
