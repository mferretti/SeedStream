#!/usr/bin/env bash
# Performance / load testing (issue #81): drive high-volume, realistic events into YOUR Kafka or
# Postgres so you can benchmark throughput / index / query latency against a real destination.
# This script load-tests the SYSTEM YOU POINT IT AT, not SeedStream itself — see README.md
# "Distinct from the internal benchmarks/" and the repo's own benchmarks/README.md + docs/PERFORMANCE.md
# for benchmarking the generator.
#
#   COUNT=50000000 ./loadtest.sh          # drives real load; the defaults below are a smoke test
#
# For DEST=db, schema.sql must be applied once first:
#   PGPASSWORD=... psql -h localhost -U loaduser -d loadtestdb -f schema.sql
set -euo pipefail

COUNT="${COUNT:-1000000}"
THREADS="${THREADS:-$(nproc)}"
DEST="${DEST:-kafka}"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEEDSTREAM="${SEEDSTREAM:-seedstream}"

case "$DEST" in
  kafka) JOB="$DIR/jobs/kafka_events.yaml" ;;
  db)    JOB="$DIR/jobs/db_events.yaml" ;;
  *)     echo "DEST must be 'kafka' or 'db' (got: $DEST)" >&2; exit 1 ;;
esac

if [[ "$DEST" == "db" ]]; then
  : "${DB_PASSWORD:?export DB_PASSWORD first (required for DEST=db)}"
fi

echo "==> Load-testing $DEST: $COUNT records, $THREADS threads"

start=$(date +%s.%N)
"$SEEDSTREAM" execute --job "$JOB" --count "$COUNT" --threads "$THREADS"
end=$(date +%s.%N)

elapsed=$(awk -v s="$start" -v e="$end" 'BEGIN { printf "%.3f", e - s }')
rec_per_sec=$(awk -v c="$COUNT" -v s="$elapsed" 'BEGIN { printf "%.0f", c / s }')

echo "=================================================="
echo " dest      : $DEST"
echo " count     : $COUNT"
echo " threads   : $THREADS"
echo " elapsed   : ${elapsed}s"
echo " rec/sec   : $rec_per_sec"
echo "=================================================="
