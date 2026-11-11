#!/usr/bin/env bash
# Northwind at scale: seed all 13 tables, parents first, from the inspected structures.
#
#   ./seed.sh db     # PostgreSQL (schema.sql + scale.sql loaded first, DB_PASSWORD exported)
#   ./seed.sh file   # JSON files under ./output/  (add --format csv via SEEDSTREAM_ARGS)
#
# ⚠️ DESTRUCTIVE (db): every job truncates its table first. Disposable database only.
#
# Row counts come from structures/counts.yaml, the same file the jobs read for their bare
# ref[table.column] fields (#442): change a size there and the references follow.
set -euo pipefail

TARGET="${1:-db}"
case "$TARGET" in db | file) ;; *) echo "usage: $0 db|file" >&2; exit 2 ;; esac
[[ "$TARGET" == db ]] && : "${DB_PASSWORD:?export DB_PASSWORD first}"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEEDSTREAM="${SEEDSTREAM:-seedstream}"
cd "$DIR" # file jobs write to ./output/

total=0
start=$SECONDS
seed() { # seed <table>   (count from structures/counts.yaml)
  local t0=$SECONDS n
  n=$(awk -F': *' -v t="$1" '$1 == t { print $2 }' "$DIR/structures/counts.yaml")
  [[ -n "$n" ]] || { echo "no count for $1 in structures/counts.yaml" >&2; exit 1; }
  echo "==> $1 ($n rows)"
  # shellcheck disable=SC2086 # SEEDSTREAM_ARGS is intentionally word-split
  "$SEEDSTREAM" execute --job "$DIR/jobs/${TARGET}_$1.yaml" --count "$n" ${SEEDSTREAM_ARGS:-}
  echo "    $1 done in $((SECONDS - t0))s"
  total=$((total + n))
}

# Lookup tables: original Northwind sizes.
seed categories
seed region
seed shippers
seed suppliers
seed customer_demographics
seed employees
seed territories
seed products
seed employee_territories
# Scaled tables.
seed customers # varchar(5) key: max 99 999 (#441)
seed customer_customer_demo
seed orders
seed order_details

elapsed=$((SECONDS - start))
echo "==> Done: $total rows in ${elapsed}s (~$((total / (elapsed > 0 ? elapsed : 1))) rows/s overall)"
