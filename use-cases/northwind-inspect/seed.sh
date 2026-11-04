#!/usr/bin/env bash
# Northwind at scale: seed all 14 tables, parents first, from the inspected structures.
#
#   ./seed.sh db     # PostgreSQL (schema.sql + scale.sql loaded first, DB_PASSWORD exported)
#   ./seed.sh file   # JSON files under ./output/  (add --format csv via SEEDSTREAM_ARGS)
#
# ⚠️ DESTRUCTIVE (db): every job truncates its table first. Disposable database only.
#
# Row counts MUST stay in sync with the static ref[] ranges in structures/*.yaml
# (e.g. orders.customer_id = ref[customers.customer_id, 1..90000] ⇔ customers seeded with 90000).
# Inspector gap tracked in #442.
set -euo pipefail

TARGET="${1:-db}"
case "$TARGET" in db | file) ;; *) echo "usage: $0 db|file" >&2; exit 2 ;; esac
[[ "$TARGET" == db ]] && : "${DB_PASSWORD:?export DB_PASSWORD first}"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEEDSTREAM="${SEEDSTREAM:-seedstream}"
cd "$DIR" # file jobs write to ./output/

total=0
start=$SECONDS
seed() { # seed <table> <count>
  local t0=$SECONDS
  echo "==> $1 ($2 rows)"
  # shellcheck disable=SC2086 # SEEDSTREAM_ARGS is intentionally word-split
  "$SEEDSTREAM" execute --job "$DIR/jobs/${TARGET}_$1.yaml" --count "$2" ${SEEDSTREAM_ARGS:-}
  echo "    $1 done in $((SECONDS - t0))s"
  total=$((total + $2))
}

# Lookup tables: original Northwind sizes.
seed categories 8
seed region 4
seed shippers 6
seed suppliers 29
seed us_states 51
seed customer_demographics 10
seed employees 9
seed territories 53
seed products 77
seed employee_territories 49
# Scaled tables.
seed customers 90000 # varchar(5) key: max 99 999 (#441)
seed customer_customer_demo 20000
seed orders 1000000
seed order_details 2600000

elapsed=$((SECONDS - start))
echo "==> Done: $total rows in ${elapsed}s (~$((total / (elapsed > 0 ? elapsed : 1))) rows/s overall)"
