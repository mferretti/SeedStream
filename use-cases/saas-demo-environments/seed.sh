#!/usr/bin/env bash
# SaaS demo environment seeding (issue #82): reseed a disposable CRM database with a fixed,
# convincing demo dataset — sales reps, accounts, contacts, deals, and activities.
#
# Every job sets `truncate_before_insert: true` and `restart_identity: true`, so this script is
# idempotent: it empties each table and restarts its IDENTITY sequence before inserting, and the
# fixed seed regenerates identical column values. Run it nightly (see README "Reseeding on demand")
# to hand every prospect environment a fresh, always-identical, no-PII dataset.
#
# ⚠️ DESTRUCTIVE. Point it only at a disposable demo database.
#
# Prerequisites:
#   * The schema exists: psql ... -f schema.sql (once — see README).
#   * DB_PASSWORD exported (used by SeedStream).
#   * SeedStream on PATH, OR set SEEDSTREAM to the launcher. The PostgreSQL JDBC driver must be
#     dropped into the distribution's extras/ dir (drivers are not bundled — vendor-neutral by
#     design; see docs/CONTAINER.md).
#
# Row counts below are the parent-pool sizes. They MUST stay in sync with the static ref ranges in
# structures/*.yaml (e.g. contacts.account_id = ref[accounts.id, 1..100] ⇔ accounts seeded with 100).
set -euo pipefail

: "${DB_PASSWORD:?export DB_PASSWORD first}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEEDSTREAM="${SEEDSTREAM:-seedstream}"

seed() {  # seed <table> <count>
  echo "==> Seeding $1 ($2 rows)"
  "$SEEDSTREAM" execute --job "$DIR/jobs/db_$1.yaml" --count "$2"
}

# Parent-first: users/accounts have no FKs; contacts references accounts; deals references
# accounts+users; activities references contacts+deals.
seed users         20
seed accounts      100
seed contacts      400
seed deals         250
seed activities   3000

echo "==> Done. Demo CRM dataset is ready."
