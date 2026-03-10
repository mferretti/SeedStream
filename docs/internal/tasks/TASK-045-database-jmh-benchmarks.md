# TASK-045: Database JMH Benchmarks — Insert Throughput & Batch Size Sensitivity

**Status:** Complete ✅
**Priority:** P2
**Phase:** Phase 8 (Database Destinations)
**Estimated Effort:** 3-4h
**Complexity:** Low
**Dependencies:** TASK-043 (nested decomposition ✅), TASK-026 (JMH infrastructure ✅)

---

## Goal

Add JMH benchmarks for `DatabaseDestination` to quantify INSERT throughput under varying batch
sizes and transaction strategies, and to measure the overhead of nested auto-decomposition vs
flat inserts.

These benchmarks provide the missing data point referenced in `docs/PERFORMANCE.md` — the database
section contains E2E throughput numbers but no component-level JMH data for batch size sensitivity.

---

## Design Summary

Follows the same pattern as `KafkaBenchmark.java`:
- System properties override connection defaults (`db.url`, `db.user`, `db.password`)
- `@Setup(Level.Trial)` creates the benchmark schema; `@TearDown(Level.Trial)` drops it
- `@TearDown(Level.Iteration)` flushes pending batches and TRUNCATEs tables between iterations
- `@Param` drives the benchmark matrix

### Benchmark Matrix

| Parameter             | Values                        |
|-----------------------|-------------------------------|
| `batchSize`           | 100, 500, 1000, 5000          |
| `transactionStrategy` | `per_batch`, `auto_commit`    |
| Methods               | `benchmarkFlatInsert`, `benchmarkNestedInsert` |

**Total configurations**: 4 × 2 × 2 = **16**

`per_job` strategy is excluded — it keeps a single transaction open across JMH iterations,
which deadlocks against the TRUNCATE issued at iteration teardown.

### Benchmark Methods

**`benchmarkFlatInsert`** — 1 INSERT per `write()` call into `benchmark_flat`:
```sql
CREATE TABLE benchmark_flat (
    id BIGINT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100),
    age INT, amount NUMERIC(10,2), created_at TIMESTAMP WITH TIME ZONE
);
```

**`benchmarkNestedInsert`** — 3 INSERTs per `write()` call via `NestedRecordDecomposer`:
```sql
CREATE TABLE benchmark_order (
    id BIGINT PRIMARY KEY, customer_name VARCHAR(100), total_amount NUMERIC(10,2)
);
CREATE TABLE order_items (
    benchmark_order_id BIGINT, product VARCHAR(100), quantity INT, price NUMERIC(10,2)
);
```

Nested record has 1 parent + 2 child items. The FK column `benchmark_order_id` is injected by
`NestedRecordDecomposer` using the convention `{parentTableName}_id`.

### Key Implementation Notes

- `flatRecord` and `nestedRecord` are pre-built in `@Setup(Level.Trial)` and reused across calls
- The `id` field is overwritten per call using an `AtomicLong` counter to avoid PK conflicts
- Child item maps are pre-built; `NestedRecordDecomposer` injects (or overwrites) the FK into
  them on each `write()` — this is correct behaviour and requires no per-call allocation
- PostgreSQL driver is `compileOnly` in `:destinations`, so `benchmarks/build.gradle.kts` declares
  `runtimeOnly(libs.postgresql)` to make it available to the JMH classpath

---

## Files Created / Modified

| File | Change |
|------|--------|
| `benchmarks/src/jmh/java/com/datagenerator/benchmarks/DatabaseBenchmark.java` | New — JMH benchmark class |
| `benchmarks/DATABASE-BENCHMARK-GUIDE.md` | New — user guide (Docker setup, expected results, troubleshooting) |
| `benchmarks/build.gradle.kts` | Added `runtimeOnly(libs.postgresql)` |

---

## Running the Benchmarks

```bash
# Prerequisites: PostgreSQL running on localhost:5432
docker run -d --name pg-benchmark \
  -e POSTGRES_DB=benchmark -e POSTGRES_USER=benchmark -e POSTGRES_PASSWORD=benchmark \
  -p 5432:5432 postgres:17-alpine

# Run full matrix (16 configurations)
./gradlew :benchmarks:jmh -Pjmh.includes=".*DatabaseBenchmark.*"

# Skip if no PostgreSQL available
./gradlew :benchmarks:jmh -Pjmh.excludes=".*DatabaseBenchmark.*"
```

See `benchmarks/DATABASE-BENCHMARK-GUIDE.md` for full documentation.

---

## Acceptance Criteria

- [x] `DatabaseBenchmark.java` compiles and runs against a local PostgreSQL instance
- [x] Both benchmark methods (`benchmarkFlatInsert`, `benchmarkNestedInsert`) produce valid throughput measurements
- [x] TRUNCATE between iterations prevents unbounded table growth
- [x] Schema is created in `@Setup(Level.Trial)` and dropped in `@TearDown(Level.Trial)`
- [x] Connection parameters are overridable via system properties
- [x] Skipping with `-Pjmh.excludes` works when PostgreSQL is unavailable
- [x] `DATABASE-BENCHMARK-GUIDE.md` covers setup, configuration, expected results, and troubleshooting

---

## Expected Results (Developer Laptop, Local PostgreSQL)

| Benchmark            | batchSize | Strategy    | Expected Throughput |
|----------------------|-----------|-------------|---------------------|
| `benchmarkFlatInsert`   | 1000   | `per_batch` | ~3,000–5,000 ops/s  |
| `benchmarkFlatInsert`   | 1000   | `auto_commit` | ~500–1,500 ops/s  |
| `benchmarkNestedInsert` | 1000   | `per_batch` | ~1,200–2,000 ops/s  |
| `benchmarkNestedInsert` | 1000   | `auto_commit` | ~200–600 ops/s    |

Nested throughput is expected to be 50–70% lower than flat due to the 3× INSERT fanout.
`auto_commit` throughput is independent of `batchSize` (per-statement commit dominates latency).
