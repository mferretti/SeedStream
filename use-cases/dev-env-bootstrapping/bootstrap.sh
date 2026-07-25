#!/usr/bin/env bash
# Developer-environment bootstrap (issue #79): create the schema, then seed every table in FK order.
# A new developer runs this once and gets a fully populated local task-tracker database — no prod
# dump, no PII. The fixed seed in each job makes the dataset identical for everyone on the team.
#
# Prerequisites:
#   * A reachable Postgres (jobs point at localhost:5432/devdb — edit jobs/*.yaml for your target).
#   * DB_PASSWORD exported (used by both psql and SeedStream).
#   * SeedStream on PATH, OR set SEEDSTREAM to the launcher. The Postgres JDBC driver must be dropped
#     into the distribution's extras/ dir (drivers are not bundled — vendor-neutral by design; see
#     docs/CONTAINER.md and PERFORMANCE.md).
#
# Row counts below are the parent-pool sizes. They MUST stay in sync with the static ref ranges in
# structures/*.yaml (e.g. projects.owner_id = ref[users.id, 1..200] ⇔ users seeded with 200).
set -euo pipefail

: "${DB_PASSWORD:?export DB_PASSWORD first}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEEDSTREAM="${SEEDSTREAM:-seedstream}"
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-devuser}"
PGDATABASE="${PGDATABASE:-devdb}"

echo "==> Creating schema"
PGPASSWORD="$DB_PASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" \
  -v ON_ERROR_STOP=1 -f "$DIR/schema.sql"

seed() {  # seed <table> <count>
  echo "==> Seeding $1 ($2 rows)"
  "$SEEDSTREAM" execute --job "$DIR/jobs/db_$1.yaml" --count "$2"
}

# Parent-first: users/labels have no FKs; the rest reference earlier tables.
seed users        200
seed labels        20
seed projects      50
seed tasks        500
seed comments    2000
seed task_labels  800

echo "==> Done. Populated task-tracker DB is ready."
