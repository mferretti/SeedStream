# Database Benchmark Guide

JMH benchmarks for `DatabaseDestination` — measures INSERT throughput under varying batch sizes,
transaction strategies, and record complexity (flat vs nested auto-decomposition).

---

## Quick Start

```bash
# 1. Start PostgreSQL
docker run -d --name pg-benchmark \
  -e POSTGRES_DB=benchmark \
  -e POSTGRES_USER=benchmark \
  -e POSTGRES_PASSWORD=benchmark \
  -p 5432:5432 \
  postgres:17-alpine

# 2. Run all database benchmarks
./gradlew :benchmarks:jmh -Pjmh.includes=".*DatabaseBenchmark.*"

# 3. View results
cat benchmarks/build/reports/jmh/results.json | python3 benchmarks/format_results.py

# 4. Cleanup
docker stop pg-benchmark && docker rm pg-benchmark
```

---

## Prerequisites

### Option 1: Docker (Recommended)

```bash
docker run -d --name pg-benchmark \
  -e POSTGRES_DB=benchmark \
  -e POSTGRES_USER=benchmark \
  -e POSTGRES_PASSWORD=benchmark \
  -p 5432:5432 \
  postgres:17-alpine
```

The benchmark creates and drops its own tables automatically. No manual schema setup is needed.

### Option 2: Existing PostgreSQL

```sql
CREATE DATABASE benchmark;
CREATE USER benchmark WITH PASSWORD 'benchmark';
GRANT ALL PRIVILEGES ON DATABASE benchmark TO benchmark;
```

### Option 3: Custom Connection

Override connection settings via system properties:

```bash
./gradlew :benchmarks:jmh \
  -Pjmh.includes=".*DatabaseBenchmark.*" \
  -Djvm.args="-Ddb.url=jdbc:postgresql://myhost:5432/mydb \
              -Ddb.user=myuser \
              -Ddb.password=secret"
```

---

## Benchmark Configurations

The benchmark runs a **4 × 2 × 2 matrix** = 16 configurations:

| Parameter          | Values                        |
|--------------------|-------------------------------|
| `batchSize`        | 100, 500, 1000, 5000          |
| `transactionStrategy` | `per_batch`, `auto_commit` |
| Benchmark methods  | flat insert, nested insert    |

### Transaction Strategies

| Strategy     | Commit Boundary          | Use Case                           |
|--------------|--------------------------|------------------------------------|
| `per_batch`  | After each `executeBatch()` | Default — best throughput/safety balance |
| `auto_commit`| After each statement     | Auditing, debugging, simple inserts |

> `per_job` is excluded from the benchmark matrix because it holds a single open transaction
> across all JMH iterations; the `TRUNCATE` issued at iteration teardown would deadlock against it.

### Selecting Specific Configurations

```bash
# Flat inserts only
./gradlew :benchmarks:jmh -Pjmh.includes=".*benchmarkFlatInsert.*"

# Nested inserts only
./gradlew :benchmarks:jmh -Pjmh.includes=".*benchmarkNestedInsert.*"

# Single strategy
./gradlew :benchmarks:jmh \
  -Pjmh.includes=".*DatabaseBenchmark.*" \
  -Pjmh.params="transactionStrategy=per_batch"

# Single batch size
./gradlew :benchmarks:jmh \
  -Pjmh.includes=".*DatabaseBenchmark.*" \
  -Pjmh.params="batchSize=1000"
```

### Skipping Database Benchmarks

When PostgreSQL is unavailable, exclude the suite entirely:

```bash
./gradlew :benchmarks:jmh -Pjmh.excludes=".*DatabaseBenchmark.*"
```

---

## Test Record Specifications

### Flat Record (`benchmark_flat`)

```sql
CREATE TABLE benchmark_flat (
    id        BIGINT PRIMARY KEY,
    name      VARCHAR(100),
    email     VARCHAR(100),
    age       INT,
    amount    NUMERIC(10,2),
    created_at TIMESTAMP WITH TIME ZONE
);
```

Record size: ~150 bytes JSON equivalent.

```
id=<sequential>, name="Alexandra Martinez", email="alexandra.martinez@example.com",
age=38, amount=249.99, created_at=2026-03-10T09:00:00Z
```

Each `benchmarkFlatInsert()` call issues **1 INSERT** accumulated into the JDBC batch.

---

### Nested Record (`benchmark_order` + `order_items`)

```sql
CREATE TABLE benchmark_order (
    id            BIGINT PRIMARY KEY,
    customer_name VARCHAR(100),
    total_amount  NUMERIC(10,2)
);

CREATE TABLE order_items (
    benchmark_order_id BIGINT,
    product            VARCHAR(100),
    quantity           INT,
    price              NUMERIC(10,2)
);
```

Record structure (2 child items per parent):

```
parent: id=<sequential>, customer_name="Alexandra Martinez", total_amount=329.97
item 1: product="Widget Pro",   quantity=2, price=89.99
item 2: product="Gadget Plus",  quantity=1, price=149.99
```

Each `benchmarkNestedInsert()` call issues **3 INSERTs** (1 parent + 2 children) via
`NestedRecordDecomposer`. The reported ops/sec is in terms of logical records, not raw statements.

---

## Expected Results

Reference hardware: developer laptop (8-core, NVMe SSD, local PostgreSQL).

### Flat Insert Throughput

| batchSize | `per_batch`      | `auto_commit`   |
|-----------|------------------|-----------------|
| 100       | ~1,500 rec/s     | ~500 rec/s      |
| 500       | ~2,500 rec/s     | ~500 rec/s      |
| 1000      | ~4,000 rec/s     | ~500 rec/s      |
| 5000      | ~5,000 rec/s     | ~500 rec/s      |

`auto_commit` throughput is independent of `batchSize` — each statement commits immediately,
making batch accumulation irrelevant.

### Nested Insert Throughput (3 INSERTs per logical record)

| batchSize | `per_batch`     | `auto_commit`   |
|-----------|-----------------|-----------------|
| 100       | ~600 rec/s      | ~200 rec/s      |
| 500       | ~1,000 rec/s    | ~200 rec/s      |
| 1000      | ~1,500 rec/s    | ~200 rec/s      |
| 5000      | ~1,800 rec/s    | ~200 rec/s      |

Nested throughput is roughly 50–70% lower than flat — each logical record requires 3 separate
prepared statement executions across 2 tables.

> These figures are estimates based on E2E measurements from `docs/PERFORMANCE.md`. JMH microbenchmark
> results on your hardware may differ. Update `docs/PERFORMANCE.md` after running on reference hardware.

---

## Interpreting Results

### Key Metrics

```json
{
  "benchmark": "com.datagenerator.benchmarks.DatabaseBenchmark.benchmarkFlatInsert",
  "params": {"batchSize": "1000", "transactionStrategy": "per_batch"},
  "mode": "thrpt",
  "score": 4123.45,
  "scoreError": 87.3,
  "scoreUnit": "ops/s"
}
```

- **score**: records/second (higher is better)
- **scoreError**: 99.9% confidence interval — values within ±scoreError are not statistically different
- **mode**: `thrpt` = throughput

### Batch Size Sensitivity Analysis

Plot `score` vs `batchSize` for `per_batch` strategy to find the diminishing returns threshold.
Typically:

- 100 → 500: large gain (amortising commit overhead)
- 500 → 1000: moderate gain
- 1000 → 5000: small gain, higher memory pressure

The optimal `batchSize` for your schema is where the `score` curve flattens.

### Flat vs Nested Overhead

```
overhead_factor = flat_score / nested_score
```

A factor of 3.0 means pure INSERT fanout with no extra overhead. Factors above 3.0 indicate
decomposition overhead (object allocation, FK injection, multi-table prepared statement cache misses).

---

## Profiling with JFR

Add Java Flight Recorder to isolate bottlenecks:

```bash
./gradlew :benchmarks:jmh \
  -Pjmh.includes=".*DatabaseBenchmark.*" \
  "-Djvm.args=-XX:+FlightRecorder \
    -XX:StartFlightRecording=filename=db_bench.jfr,duration=60s,settings=profile"
```

Open `db_bench.jfr` in JDK Mission Control to inspect:
- JDBC call hotspots (`executeBatch`, `commit`)
- HikariCP connection acquisition time
- Object allocation in `NestedRecordDecomposer`

---

## Troubleshooting

**Connection refused (`Connection to localhost:5432 refused`)**
- Verify PostgreSQL is running: `docker ps | grep pg-benchmark`
- Check port mapping: `docker inspect pg-benchmark | grep HostPort`
- Override URL if using a different host/port: `-Ddb.url=jdbc:postgresql://...`

**Authentication failed**
- Default credentials are `benchmark` / `benchmark`. Override with `-Ddb.user=` and `-Ddb.password=`.

**`ERROR: relation "benchmark_flat" already exists`**
- Previous run left tables behind (crash or interrupted teardown). Drop manually:
  ```sql
  DROP TABLE IF EXISTS order_items, benchmark_order, benchmark_flat;
  ```

**Abnormally low throughput**
- Check PostgreSQL is on local disk, not a remote server. Network round-trip latency dominates.
- Verify `fsync=off` is NOT set — benchmarks should reflect realistic durability settings.
- Try reducing `batchSize` — very large batches (5000+) can exceed PostgreSQL shared_buffers.

**`No suitable driver found`**
- Ensure you are running via `./gradlew :benchmarks:jmh`, not a plain `java -jar`. The Gradle JMH
  plugin puts the PostgreSQL driver on the classpath automatically via `runtimeOnly(libs.postgresql)`
  in `benchmarks/build.gradle.kts`.
