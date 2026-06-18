#!/bin/bash
# Determinism demo — same seed → byte-for-byte identical output, at any thread count.
#
# Generates the same dataset (seed 12345) three times — single-threaded, 4 threads,
# 8 threads — and shows the SHA-256 is identical every time. This is SeedStream's
# headline guarantee: the data does not depend on how the work is partitioned.
#
# Usage:   ./scripts/determinism-demo.sh
#
# No arguments. Builds the CLI once, runs the demo, cleans up after itself.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

JOB="config/jobs/quickstart.yaml"
OUT="out/invoices.json"   # path defined in the job YAML (out/invoices -> .json)
SEED=12345
COUNT=5000

cleanup() { rm -f "$OUT"; rmdir out 2>/dev/null || true; }
trap cleanup EXIT

echo "==> Building CLI (one time)…"
./gradlew -q :cli:installDist
CLI="$ROOT/cli/build/install/cli/bin/cli"

run_hash() {
  local threads="$1"
  rm -f "$OUT"
  "$CLI" execute --job "$JOB" --count "$COUNT" --seed "$SEED" --threads "$threads" \
    >/dev/null 2>&1
  sha256sum "$OUT" | cut -d' ' -f1
}

echo "==> Generating $COUNT records with --seed $SEED, three thread counts…"
H1=$(run_hash 1);  printf '    threads=1  sha256=%s\n' "$H1"
H4=$(run_hash 4);  printf '    threads=4  sha256=%s\n' "$H4"
H8=$(run_hash 8);  printf '    threads=8  sha256=%s\n' "$H8"

echo
if [ "$H1" = "$H4" ] && [ "$H4" = "$H8" ]; then
  echo "✅ DETERMINISTIC — identical SHA-256 across 1, 4, and 8 threads."
  echo "   Same seed, same bytes, regardless of parallelism."
  exit 0
else
  echo "❌ NON-DETERMINISTIC — hashes differ across thread counts. This is a bug."
  exit 1
fi
