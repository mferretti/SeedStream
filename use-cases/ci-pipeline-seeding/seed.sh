#!/usr/bin/env bash
# CI pipeline seeding (issue #80): reseed a disposable database with a fixed, known dataset.
#
# Run this BEFORE the integration-test task. Every job sets `truncate_before_insert: true` and
# `restart_identity: true`, so this script is idempotent: it empties each table and restarts its
# IDENTITY sequence before inserting, and the fixed seed regenerates identical column values.
# Same script, same rows, every run — no teardown step, no leftover state between suites.
#
# ⚠️ DESTRUCTIVE. Point it only at a disposable/CI database.
#
# Prerequisites:
#   * The schema exists: psql ... -f schema.sql (once — see README).
#   * DB_PASSWORD exported (used by SeedStream).
#   * SeedStream on PATH, OR set SEEDSTREAM to the launcher. The PostgreSQL JDBC driver must be
#     dropped into the distribution's extras/ dir (drivers are not bundled — vendor-neutral by
#     design; see docs/CONTAINER.md).
#
# Row counts below are the parent-pool sizes. They MUST stay in sync with the static ref ranges in
# structures/*.yaml (e.g. orders.customer_id = ref[customers.id, 1..100] ⇔ customers seeded with 100).
set -euo pipefail

: "${DB_PASSWORD:?export DB_PASSWORD first}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEEDSTREAM="${SEEDSTREAM:-seedstream}"

seed() {  # seed <table> <count>
  echo "==> Seeding $1 ($2 rows)"
  "$SEEDSTREAM" execute --job "$DIR/jobs/db_$1.yaml" --count "$2"
}

# Parent-first: customers has no FKs; orders references customers; order_items references orders.
seed customers    100
seed orders       400
seed order_items 1200

echo "==> Done. Deterministic fixture is in place."
